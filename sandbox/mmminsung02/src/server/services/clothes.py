import hashlib
import io
import shutil
import sqlite3
from dataclasses import dataclass

from PIL import Image, UnidentifiedImageError

from server.ai.analyzer import ImageAnalyzer
from server.config import Settings
from server.domain import CATEGORIES, COLORS

ALLOWED_FORMATS = {"JPEG": "jpg", "PNG": "png", "WEBP": "webp"}


class InvalidImageError(Exception):
    pass


class InvalidValueError(Exception):
    """정해진 목록 밖의 종류·색"""


EDITABLE_TEXT_FIELDS = ("name", "brand", "material", "size")


@dataclass
class RegisterResult:
    cloth: dict
    duplicate: bool


def _open_image(data: bytes) -> Image.Image:
    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except (UnidentifiedImageError, OSError, SyntaxError) as e:
        raise InvalidImageError from e
    if image.format not in ALLOWED_FORMATS:
        raise InvalidImageError
    return image


def cloth_row_to_dict(row: sqlite3.Row) -> dict:
    has_photo = row["image_path"] is not None
    return {
        "id": row["id"],
        "image_url": f"/v1/clothes/{row['id']}/image" if has_photo else None,
        "category": row["category"],
        "color": row["color"],
        "name": row["name"],
        "brand": row["brand"],
        "material": row["material"],
        "size": row["size"],
        "source": row["source"],
        "confirmed": bool(row["confirmed"]),
        "needs_category": row["category"] == "other" and not row["confirmed"],
        "needs_photo": not has_photo,
        "created_at": row["created_at"],
    }


def register_photo(
    conn: sqlite3.Connection,
    settings: Settings,
    analyzer: ImageAnalyzer,
    user_id: str,
    data: bytes,
) -> RegisterResult:
    image_hash = hashlib.sha256(data).hexdigest()
    existing = conn.execute(
        "SELECT * FROM clothes WHERE user_id = ? AND image_hash = ?", (user_id, image_hash)
    ).fetchone()
    if existing:
        return RegisterResult(cloth_row_to_dict(existing), duplicate=True)

    image = _open_image(data)
    analysis = analyzer.analyze(image)
    category = analysis.category
    if analysis.category_confidence < settings.min_category_confidence:
        category = "other"

    ext = ALLOWED_FORMATS[image.format]
    relative_path = f"images/{user_id}/{image_hash}.{ext}"
    file_path = settings.data_dir / relative_path
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_bytes(data)

    try:
        with conn:
            cursor = conn.execute(
                "INSERT INTO clothes (user_id, image_path, image_hash, category, color, source)"
                " VALUES (?, ?, ?, ?, ?, 'photo')",
                (user_id, relative_path, image_hash, category, analysis.color),
            )
            cloth_id = cursor.lastrowid
            conn.execute(
                "INSERT INTO clothes_embeddings (cloth_id, model, vector) VALUES (?, ?, ?)",
                (cloth_id, analyzer.model_name, analysis.embedding.astype("float32").tobytes()),
            )
    except Exception:
        file_path.unlink(missing_ok=True)
        raise

    row = conn.execute("SELECT * FROM clothes WHERE id = ?", (cloth_id,)).fetchone()
    return RegisterResult(cloth_row_to_dict(row), duplicate=False)


def validate_category_color(category: str | None, color: str | None) -> None:
    if category is not None and category not in CATEGORIES:
        raise InvalidValueError(f"알 수 없는 종류예요: {category}")
    if color is not None and color not in COLORS:
        raise InvalidValueError(f"알 수 없는 색이에요: {color}")


def list_clothes(
    conn: sqlite3.Connection,
    user_id: str,
    category: str | None,
    color: str | None,
    limit: int,
    offset: int,
) -> dict:
    validate_category_color(category, color)
    where, args = ["user_id = ?"], [user_id]
    if category:
        where.append("category = ?")
        args.append(category)
    if color:
        where.append("color = ?")
        args.append(color)
    condition = " AND ".join(where)
    total = conn.execute(f"SELECT COUNT(*) FROM clothes WHERE {condition}", args).fetchone()[0]
    rows = conn.execute(
        f"SELECT * FROM clothes WHERE {condition} ORDER BY id DESC LIMIT ? OFFSET ?",
        [*args, limit, offset],
    ).fetchall()
    return {"items": [cloth_row_to_dict(r) for r in rows], "total": total}


def get_cloth_row(conn: sqlite3.Connection, user_id: str, cloth_id: int) -> sqlite3.Row | None:
    """다른 사용자의 옷이면 없는 것과 똑같이 None"""
    return conn.execute(
        "SELECT * FROM clothes WHERE id = ? AND user_id = ?", (cloth_id, user_id)
    ).fetchone()


def update_cloth(
    conn: sqlite3.Connection, user_id: str, cloth_id: int, changes: dict
) -> dict | None:
    if get_cloth_row(conn, user_id, cloth_id) is None:
        return None
    validate_category_color(changes.get("category"), changes.get("color"))

    values: dict = {}
    for key in ("category", "color"):
        if changes.get(key) is not None:
            values[key] = changes[key]
    for key in EDITABLE_TEXT_FIELDS:
        if key in changes:
            text = (changes[key] or "").strip()
            values[key] = text or None  # 빈 글자는 정보 없음
    values["confirmed"] = 1

    assignments = ", ".join(f"{k} = ?" for k in values)
    with conn:
        conn.execute(
            f"UPDATE clothes SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            [*values.values(), cloth_id],
        )
    return cloth_row_to_dict(get_cloth_row(conn, user_id, cloth_id))


def delete_cloth(conn: sqlite3.Connection, settings: Settings, user_id: str, cloth_id: int) -> bool:
    row = get_cloth_row(conn, user_id, cloth_id)
    if row is None:
        return False
    with conn:
        # 임베딩은 ON DELETE CASCADE로 같이 지워진다
        conn.execute("DELETE FROM clothes WHERE id = ?", (cloth_id,))
    if row["image_path"]:
        (settings.data_dir / row["image_path"]).unlink(missing_ok=True)
    return True


def delete_user_data(conn: sqlite3.Connection, settings: Settings, user_id: str) -> None:
    """옷·상품·리포트·대화·등록 기록과 사진 파일을 모두 지운다. 사용자 계정 행은 남긴다"""
    with conn:
        conn.execute("DELETE FROM clerk_messages WHERE user_id = ?", (user_id,))
        conn.execute("DELETE FROM imports WHERE user_id = ?", (user_id,))
        conn.execute("DELETE FROM products WHERE user_id = ?", (user_id,))  # 리포트는 CASCADE
        conn.execute("DELETE FROM clothes WHERE user_id = ?", (user_id,))  # 임베딩은 CASCADE
    shutil.rmtree(settings.image_dir / user_id, ignore_errors=True)
