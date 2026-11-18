"""자동 등록: 주문내역 캡처(기능 13) · 앱에서 산 옷(기능 14) · 영수증(기능 15).

흐름은 셋 다 같다: 후보 만들기(imports, status=pending) → 사용자가 고르고 고침 → confirm.
**확인하기 전에는 옷장(clothes)에 아무것도 저장하지 않는다.**

- 후보 썸네일은 `imports/<user>/<import id>/<index>.png`에 두고, 확인하면 옷 사진으로 옮긴다
- 같은 옷이 두 번 생기지 않게 옷마다 source_ref(주문번호·상품명 등)를 두고 DB 고유 색인으로 막는다
- 이름·주소·연락처·결제 정보 형식의 값은 후보와 DB에 넣지 않는다 (scrub_personal)
- 사진에서 읽은 글자 전체(transcript)는 저장하지 않는다
"""

import hashlib
import io
import json
import logging
import re
import shutil
import sqlite3
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date
from urllib.parse import urlsplit

from PIL import Image, ImageOps

from server.ai.analyzer import ImageAnalyzer
from server.ai.import_reader import ImportItem, ImportRead, ImportReader
from server.config import Settings
from server.services.clothes import (
    ALLOWED_FORMATS,
    InvalidImageError,
    _open_image,
    cloth_row_to_dict,
    validate_category_color,
)
from server.services.screenshot import _crop, _for_reader

log = logging.getLogger(__name__)

TYPES = ("order_capture", "receipt", "in_app")
MIN_LETTERS = 8

UNREADABLE = {
    "order_capture": "주문내역이 잘 보이게 다시 캡처해 주세요",
    "receipt": "영수증 글자가 잘 보이게 다시 찍어 주세요",
}
NOTHING_FOUND = {
    "order_capture": "주문내역에서 상품을 찾지 못했어요. 상품 목록이 보이게 다시 캡처해 주세요",
    "receipt": "영수증에서 옷을 찾지 못했어요",
    "in_app": "주문한 상품을 찾지 못했어요",
}

# ---------------------------------------------------------------- 개인 정보 걸러 내기

_PERSONAL_PATTERNS = [
    # 휴대전화·일반 전화 (한국·일본), 국제 번호
    re.compile(r"(?<!\d)(?:\+?82[-.\s]?)?0?1[016789][-.\s]?\d{3,4}[-.\s]?\d{4}(?!\d)"),
    re.compile(r"\b0\d{1,4}[-.\s]\d{1,4}[-.\s]\d{4}\b"),
    re.compile(r"\+\d{1,3}[-.\s]?\d{1,4}[-.\s]?\d{3,4}[-.\s]?\d{3,4}"),
    # 이메일
    re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"),
    # 주소: 시·도 + 시·군·구, 도로명(…로/길 + 번호), 우편번호 표시
    re.compile(
        r"(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|충청|전북|전남|전라|경북|경남|경상|제주)"
        r"\S*\s+\S+(시|군|구)(\s|$)"
    ),
    re.compile(r"\S+(로|길)\s?\d+(-\d+)?(번길)?"),
    re.compile(r"〒\s?\d{3}-?\d{4}"),
    re.compile(r"(都|道|府|県)\S*(市|区|町|村)"),
    # 이름·주소 칸 이름이 붙은 값
    re.compile(
        r"(주문자|받는\s?분|받는\s?사람|수령인|수취인|구매자|배송지|주소|연락처|"
        r"お届け先|ご注文者|お名前|住所|電話|Ship\s?to|Recipient|Address|Phone)",
        re.IGNORECASE,
    ),
    # 결제 정보: 카드 번호(가린 것 포함), 승인번호
    # (붙어 있는 13자리 바코드 번호는 품번이라 남긴다)
    re.compile(r"\d{4}[-\s][\d*]{4}[-\s][\d*]{4}[-\s][\d*]{2,4}|\d{4,6}\*{4,}"),
    re.compile(r"승인\s?(번호|No)|承認番号|approval", re.IGNORECASE),
    re.compile(r"(카드|card)\s?(번호|no)", re.IGNORECASE),
]


def looks_personal(text: str | None) -> bool:
    if not text:
        return False
    return any(p.search(text) for p in _PERSONAL_PATTERNS)


def scrub_personal(value: str | None) -> str | None:
    """개인 정보처럼 보이면 값을 통째로 버린다 (일부만 지우면 남은 조각이 남는다)"""
    if value is None or looks_personal(value):
        return None
    return value


# ---------------------------------------------------------------- 의류가 아닌 줄

_NOT_PRODUCT = re.compile(
    r"쇼핑\s?백|봉투|비닐|포장|선물\s?박스|배송비|배달료|할인|쿠폰|적립|"
    r"shopping\s?bag|gift\s?(box|wrap)|packaging|shipping|delivery\s?fee|discount|"
    r"ショッピングバッグ|袋|ラッピング|送料|割引",
    re.IGNORECASE,
)
_NOT_CLOTHING = re.compile(
    r"가방|백팩|토트|파우치|지갑|화장품|크림|로션|향수|립|핸드|생활|컵|우산|"
    r"\bbag\b|tote|backpack|pouch|wallet|cosmetic|cream|lotion|perfume|umbrella|mug|"
    r"バッグ|トート|財布|化粧|クリーム",
    re.IGNORECASE,
)


def is_packaging_line(name: str) -> bool:
    return bool(_NOT_PRODUCT.search(name))


def guess_clothing(name: str) -> bool:
    return not (is_packaging_line(name) or _NOT_CLOTHING.search(name))


# ---------------------------------------------------------------- 공통 도우미


def _squash(text: str | None) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def _grounded(value: str | None, evidence: str) -> str | None:
    """LLM이 준 값이 사진 글자에 실제로 있을 때만 쓴다 (근거 검사)"""
    if value is None:
        return None
    value = value.strip()
    if not value or _squash(value) not in evidence:
        return None
    return value


def _valid_date(text: str | None) -> str | None:
    if not text:
        return None
    try:
        return date.fromisoformat(text.strip()).isoformat()
    except ValueError:
        return None


def _import_dir(settings: Settings, user_id: str, import_id: int):
    return settings.data_dir / "imports" / user_id / str(import_id)


def _ref_exists(conn: sqlite3.Connection, user_id: str, source: str, ref: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM clothes WHERE user_id = ? AND source = ? AND source_ref = ?",
        (user_id, source, ref),
    ).fetchone()
    return row is not None


def import_row_to_dict(row: sqlite3.Row) -> dict:
    candidates = json.loads(row["candidates"])
    for c in candidates:
        c["image_url"] = (
            f"/v1/imports/{row['id']}/candidates/{c['index']}/image" if c["has_image"] else None
        )
    return {
        "id": row["id"],
        "type": row["type"],
        "status": row["status"],
        "candidates": candidates,
    }


@dataclass
class _Draft:
    """저장 전 후보 하나 (썸네일 바이트는 따로 파일로 쓴다)"""

    name: str
    option: str | None
    color: str | None
    size: str | None
    price: str | None
    date: str | None
    product_code: str | None
    store: str | None
    category: str | None
    material: str | None
    is_clothing: bool
    ref: str
    thumbnail: bytes | None


def _save_import(
    conn: sqlite3.Connection,
    settings: Settings,
    user_id: str,
    kind: str,
    drafts: list[_Draft],
    import_ref: str | None = None,
) -> dict:
    source = kind
    candidates = []
    seen: set[str] = set()
    for draft in drafts:
        if draft.ref in seen:  # 여러 장 캡처에 같은 줄이 겹쳐 찍힌 경우
            continue
        seen.add(draft.ref)
        duplicate = _ref_exists(conn, user_id, source, draft.ref)
        candidates.append(
            {
                "index": len(candidates),
                "name": draft.name,
                "option": draft.option,
                "color": draft.color,
                "size": draft.size,
                "price": draft.price,
                "date": draft.date,
                "product_code": draft.product_code,
                "store": draft.store,
                "category": draft.category,
                "material": draft.material,
                "is_clothing": draft.is_clothing,
                "duplicate": duplicate,
                # 의류가 아니거나 이미 있는 옷은 기본 체크를 해제한다
                "selected": draft.is_clothing and not duplicate,
                "has_image": draft.thumbnail is not None,
                "source_ref": draft.ref,
                "_thumb": draft.thumbnail,
            }
        )
    with conn:
        cursor = conn.execute(
            "INSERT INTO imports (user_id, type, status, candidates, ref)"
            " VALUES (?, ?, 'pending', '[]', ?)",
            (user_id, kind, import_ref),
        )
        import_id = cursor.lastrowid
        folder = _import_dir(settings, user_id, import_id)
        for c in candidates:
            thumb = c.pop("_thumb")
            if thumb is not None:
                folder.mkdir(parents=True, exist_ok=True)
                (folder / f"{c['index']}.png").write_bytes(thumb)
        conn.execute(
            "UPDATE imports SET candidates = ? WHERE id = ?",
            (json.dumps(candidates, ensure_ascii=False), import_id),
        )
    row = conn.execute("SELECT * FROM imports WHERE id = ?", (import_id,)).fetchone()
    return import_row_to_dict(row)


# ---------------------------------------------------------------- 기능 13·15: 사진에서


class Unreadable(Exception):
    """글자를 거의 못 읽음"""


class NothingFound(Exception):
    pass


class ReadFailed(Exception):
    pass


def _draft_from_item(
    item: ImportItem,
    read: ImportRead,
    evidence: str,
    kind: str,
    thumbnail: bytes | None,
) -> _Draft | None:
    name = scrub_personal(_grounded(item.name, evidence))
    if name is None:
        return None
    if kind == "receipt" and (not item.is_clothing or is_packaging_line(name)):
        return None  # 영수증은 의류가 아닌 줄을 후보에서 뺀다
    option = scrub_personal(_grounded(item.option, evidence))
    size = scrub_personal(_grounded(item.size, evidence))
    order_number = scrub_personal(_grounded(read.order_number, evidence))
    store = scrub_personal(_grounded(read.store, evidence))
    purchased = _valid_date(item.date)
    code = scrub_personal(_grounded(item.product_code, evidence))
    if kind == "receipt":
        key = [store or "", purchased or "", _squash(name), code or ""]
    else:
        key = [order_number or purchased or "", _squash(name), _squash(option)]
    return _Draft(
        name=name,
        option=option,
        color=item.color,
        size=size,
        price=scrub_personal(_grounded(item.price, evidence)),
        date=purchased,
        product_code=code,
        store=store,
        category=item.category,
        material=None,
        is_clothing=item.is_clothing and guess_clothing(name),
        ref=hashlib.sha256("\x1f".join(key).encode()).hexdigest()[:32],
        thumbnail=None if kind == "receipt" else thumbnail,
    )


def import_from_images(
    conn: sqlite3.Connection,
    settings: Settings,
    reader: ImportReader,
    user_id: str,
    kind: str,
    images: list[bytes],
) -> dict:
    """kind: order_capture(여러 장) / receipt(한 장).

    사진이 아니면 InvalidImageError, 글자를 거의 못 읽으면 Unreadable,
    상품 줄이 없으면 NothingFound, Claude 실패는 ReadFailed
    """
    opened = [ImageOps.exif_transpose(_open_image(data)).convert("RGB") for data in images]
    drafts: list[_Draft] = []
    letters = 0
    for image in opened:
        sent, scale = _for_reader(image)
        try:
            read = reader.read(sent, "image/jpeg", kind)
        except Exception as e:
            log.exception("자동 등록 사진 읽기 실패")
            raise ReadFailed from e
        letters += sum(ch.isalpha() for ch in read.transcript)
        evidence = _squash(read.transcript)
        for item in read.items:
            thumb = _crop(image, item.thumbnail, scale) if kind == "order_capture" else None
            draft = _draft_from_item(item, read, evidence, kind, thumb)
            if draft is not None:
                drafts.append(draft)
    if letters < MIN_LETTERS:
        raise Unreadable
    if not drafts:
        raise NothingFound
    return _save_import(conn, settings, user_id, kind, drafts)


# ---------------------------------------------------------------- 기능 14: 앱에서 산 옷


def _clean_url(url: str | None) -> str:
    if not url:
        return ""
    parts = urlsplit(url)
    return f"{parts.netloc.lower()}{parts.path.rstrip('/')}"


def _match_product(conn: sqlite3.Connection, user_id: str, item: dict) -> sqlite3.Row | None:
    """이번 쇼핑에서 본 상품(products) 중 주소나 상품명이 같은 것"""
    rows = conn.execute(
        "SELECT * FROM products WHERE user_id = ? AND source = 'page' ORDER BY id DESC LIMIT 200",
        (user_id,),
    ).fetchall()
    url = _clean_url(item.get("product_url"))
    name = _squash(item.get("name"))
    for row in rows:
        if url and _clean_url(row["url"]) == url:
            return row
    for row in rows:
        fields = json.loads(row["fields"])
        seen = fields.get("name") or {}
        if name and name in (_squash(seen.get("original")), _squash(seen.get("ko"))):
            return row
    return None


def import_in_app(
    conn: sqlite3.Connection,
    settings: Settings,
    fetch_image: Callable[[str], bytes | None],
    user_id: str,
    order: dict,
) -> tuple[dict, bool]:
    """order.js가 읽은 주문 완료 페이지 → 후보. (결과, 이미 있던 등록인지)

    같은 주문(쇼핑몰 + 주문번호)은 새로 만들지 않고 앞서 만든 등록을 돌려준다
    """
    site = (order.get("site") or "").lower()
    order_number = scrub_personal((order.get("order_number") or "").strip()) or ""
    import_ref = f"{site}#{order_number or _clean_url(order.get('url'))}"
    existing = conn.execute(
        "SELECT * FROM imports WHERE user_id = ? AND type = 'in_app' AND ref = ?",
        (user_id, import_ref),
    ).fetchone()
    if existing is not None:
        return import_row_to_dict(existing), True

    drafts: list[_Draft] = []
    for item in order.get("items", []):
        name = scrub_personal((item.get("name") or "").strip())
        if not name:
            continue
        product = _match_product(conn, user_id, item)
        fields = json.loads(product["fields"]) if product else {}
        thumbnail = None
        if product is not None and product["image_path"]:
            path = settings.data_dir / product["image_path"]
            if path.is_file():
                thumbnail = path.read_bytes()
        if thumbnail is None and (item.get("image_url") or "").startswith("https://"):
            try:
                thumbnail = fetch_image(item["image_url"])
            except Exception:
                log.exception("주문 상품 이미지를 받지 못했어요")
        thumbnail = _as_png(thumbnail)
        category = product["category"] if product else None
        is_clothing = guess_clothing(name) and category in (None, "top", "bottom", "outer")
        option = scrub_personal(item.get("color"))
        drafts.append(
            _Draft(
                name=name,
                option=option,
                color=None,
                size=scrub_personal(item.get("size")),
                price=scrub_personal(item.get("price")),
                date=date.today().isoformat(),
                product_code=None,
                store=site or None,
                category=category,
                material=(fields.get("material") or {}).get("ko"),
                is_clothing=is_clothing,
                ref=hashlib.sha256(
                    "\x1f".join([import_ref, _squash(name), _squash(option)]).encode()
                ).hexdigest()[:32],
                thumbnail=thumbnail,
            )
        )
    if not drafts:
        raise NothingFound
    return _save_import(conn, settings, user_id, "in_app", drafts, import_ref), False


def _as_png(data: bytes | None) -> bytes | None:
    if not data:
        return None
    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except Exception:
        return None
    if image.format not in ALLOWED_FORMATS:
        return None
    buf = io.BytesIO()
    image.convert("RGB").save(buf, format="PNG")
    return buf.getvalue()


# ---------------------------------------------------------------- 확인 · 버리기


class NotPending(Exception):
    pass


class MissingColor(Exception):
    def __init__(self, name: str):
        super().__init__(name)
        self.name = name


def get_import_row(conn: sqlite3.Connection, user_id: str, import_id: int) -> sqlite3.Row | None:
    return conn.execute(
        "SELECT * FROM imports WHERE id = ? AND user_id = ?", (import_id, user_id)
    ).fetchone()


def confirm_import(
    conn: sqlite3.Connection,
    settings: Settings,
    analyzer: ImageAnalyzer,
    user_id: str,
    import_id: int,
    choices: list[dict],
) -> dict | None:
    """choices: 고른 후보 [{index, category?, color?, name?, size?, brand?}].

    고른 것만 저장한다. 이미 있는 옷(같은 source_ref·같은 사진)은 건너뛴다.
    없으면 None, pending이 아니면 NotPending, 색을 모르면 MissingColor
    """
    row = get_import_row(conn, user_id, import_id)
    if row is None:
        return None
    if row["status"] != "pending":
        raise NotPending
    candidates = {c["index"]: c for c in json.loads(row["candidates"])}
    folder = _import_dir(settings, user_id, import_id)

    plans = []
    for choice in choices:
        candidate = candidates.get(choice.get("index"))
        if candidate is None:
            continue
        category = choice.get("category") or candidate["category"]
        color = choice.get("color") or candidate["color"]
        validate_category_color(category, color)
        thumb_path = folder / f"{candidate['index']}.png"
        thumb = thumb_path.read_bytes() if candidate["has_image"] and thumb_path.is_file() else None
        analysis = analyzer.analyze(_open_image(thumb).convert("RGB")) if thumb else None
        if analysis is not None:
            sure = analysis.category_confidence >= settings.min_category_confidence
            if category is None and sure:
                category = analysis.category
            color = color or analysis.color
        if color is None:
            raise MissingColor(candidate["name"])
        plans.append((candidate, choice, category or "other", color, thumb, analysis))

    saved, skipped, written = [], [], []
    try:
        with conn:
            for candidate, choice, category, color, thumb, analysis in plans:
                if _ref_exists(conn, user_id, row["type"], candidate["source_ref"]):
                    skipped.append({"index": candidate["index"], "reason": "duplicate"})
                    continue
                image_path = image_hash = None
                if thumb is not None:
                    image_hash = hashlib.sha256(thumb).hexdigest()
                    taken = conn.execute(
                        "SELECT 1 FROM clothes WHERE user_id = ? AND image_hash = ?",
                        (user_id, image_hash),
                    ).fetchone()
                    if taken:
                        skipped.append({"index": candidate["index"], "reason": "duplicate"})
                        continue
                    image_path = f"images/{user_id}/{image_hash}.png"
                    target = settings.data_dir / image_path
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(thumb)
                    written.append(target)
                name = _text(choice, "name", candidate["name"])
                cursor = conn.execute(
                    "INSERT INTO clothes (user_id, image_path, image_hash, category, color, name,"
                    " brand, material, size, source, source_ref, purchased_at)"
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (
                        user_id,
                        image_path,
                        image_hash,
                        category,
                        color,
                        scrub_personal(name),
                        scrub_personal(_text(choice, "brand", None)),
                        candidate.get("material"),
                        scrub_personal(_text(choice, "size", candidate["size"])),
                        row["type"],
                        candidate["source_ref"],
                        candidate["date"],
                    ),
                )
                cloth_id = cursor.lastrowid
                if analysis is not None:
                    conn.execute(
                        "INSERT INTO clothes_embeddings (cloth_id, model, vector) VALUES (?, ?, ?)",
                        (
                            cloth_id,
                            analyzer.model_name,
                            analysis.embedding.astype("float32").tobytes(),
                        ),
                    )
                saved.append(cloth_id)
            conn.execute("UPDATE imports SET status = 'confirmed' WHERE id = ?", (import_id,))
    except Exception:
        for path in written:
            path.unlink(missing_ok=True)
        raise
    shutil.rmtree(folder, ignore_errors=True)
    clothes = [
        cloth_row_to_dict(conn.execute("SELECT * FROM clothes WHERE id = ?", (i,)).fetchone())
        for i in saved
    ]
    return {"saved": clothes, "skipped": skipped}


def _text(choice: dict, key: str, default: str | None) -> str | None:
    if key not in choice:
        return default
    text = (choice[key] or "").strip()
    return text or None


def discard_import(
    conn: sqlite3.Connection, settings: Settings, user_id: str, import_id: int
) -> bool:
    row = get_import_row(conn, user_id, import_id)
    if row is None:
        return False
    if row["status"] == "pending":
        with conn:
            conn.execute("UPDATE imports SET status = 'discarded' WHERE id = ?", (import_id,))
    shutil.rmtree(_import_dir(settings, user_id, import_id), ignore_errors=True)
    return True


def candidate_image_path(settings: Settings, row: sqlite3.Row, index: int):
    path = _import_dir(settings, row["user_id"], row["id"]) / f"{index}.png"
    return path if path.is_file() else None


__all__ = [
    "InvalidImageError",
    "MissingColor",
    "NotPending",
    "NothingFound",
    "ReadFailed",
    "TYPES",
    "UNREADABLE",
    "NOTHING_FOUND",
    "Unreadable",
    "candidate_image_path",
    "confirm_import",
    "discard_import",
    "get_import_row",
    "import_from_images",
    "import_in_app",
    "import_row_to_dict",
    "looks_personal",
]
