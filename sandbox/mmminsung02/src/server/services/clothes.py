import hashlib
import io
import sqlite3
from dataclasses import dataclass

from PIL import Image, UnidentifiedImageError

from server.ai.analyzer import ImageAnalyzer
from server.config import Settings

ALLOWED_FORMATS = {"JPEG": "jpg", "PNG": "png", "WEBP": "webp"}


class InvalidImageError(Exception):
    pass


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
    return {
        "id": row["id"],
        "category": row["category"],
        "color": row["color"],
        "source": row["source"],
        "confirmed": bool(row["confirmed"]),
        "needs_category": row["category"] == "other" and not row["confirmed"],
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
