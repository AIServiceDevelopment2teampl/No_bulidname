"""구매 전 비교 리포트 (compare/purchase-report.md).

비교 대상은 유사 검색 TOP 3 중 가장 비슷한 1벌 (2026-11-11 결정).
비슷한 점 / 다른 점 / 확인 안 된 점은 아래 규칙으로만 정한다. LLM은 문장만 다듬는다.

- 한쪽이라도 값이 없으면 확인 안 된 점
- 값이 같으면 비슷한 점, 다르면 다른 점
- 색: 쇼핑몰 색 이름을 옷장의 15색으로 바꿔 비교하고, 표에 없으면 상품 사진 색 (같은 날 결정)
- 소재: 섬유 이름(울·면·폴리에스터 …)을 뽑아 종류가 같으면 비슷한 점. 비율은 보지 않는다
- 핏: 내 옷에는 핏 칸이 없어서, 상품에 핏이 있을 때 확인 안 된 점으로만 적는다
- 사이즈는 비교하지 않는다 (쇼핑몰 사이즈표와 내 옷 사이즈 한 칸은 같은 기준이 아니다)
"""

import json
import logging
import re
import sqlite3

from server.ai.analyzer import ImageAnalyzer
from server.ai.report_writer import ReportWriter
from server.config import Settings
from server.services.similar import analyze_product, rank_similar

log = logging.getLogger(__name__)

LABELS = {"category": "종류", "color": "색", "material": "소재", "fit": "핏"}
CATEGORY_KO = {"top": "상의", "bottom": "하의", "outer": "아우터", "other": "기타"}
# 앱 Clothes.kt의 색 이름과 같다
COLOR_KO = {
    "black": "블랙",
    "white": "화이트",
    "gray": "그레이",
    "navy": "네이비",
    "blue": "블루",
    "beige": "베이지",
    "brown": "브라운",
    "khaki": "카키",
    "green": "그린",
    "red": "레드",
    "pink": "핑크",
    "purple": "퍼플",
    "yellow": "옐로",
    "orange": "오렌지",
    "multi": "멀티",
}

# 쇼핑몰 색 이름 → 옷장 15색. 영어는 단어로, 한국어·일본어는 글자 포함으로 찾는다
COLOR_WORDS = {
    "black": ["black", "noir", "블랙", "검정", "검은", "ブラック", "黒"],
    "white": ["white", "ivory", "off-white", "화이트", "흰", "하양", "아이보리", "ホワイト", "白"],
    "gray": [
        "gray",
        "grey",
        "charcoal",
        "heather",
        "그레이",
        "회색",
        "차콜",
        "グレー",
        "チャコール",
        "灰",
    ],
    "navy": ["navy", "네이비", "남색", "ネイビー", "紺"],
    "blue": [
        "blue",
        "denim",
        "indigo",
        "블루",
        "파랑",
        "파란",
        "하늘",
        "데님",
        "ブルー",
        "サックス",
        "青",
    ],
    "beige": [
        "beige",
        "camel",
        "tan",
        "cream",
        "sand",
        "ecru",
        "oatmeal",
        "베이지",
        "카멜",
        "크림",
        "샌드",
        "ベージュ",
        "キャメル",
        "エクリュ",
    ],
    "brown": ["brown", "chocolate", "mocha", "브라운", "갈색", "ブラウン", "茶"],
    "khaki": ["khaki", "olive", "카키", "올리브", "カーキ", "オリーブ"],
    "green": ["green", "mint", "그린", "초록", "녹색", "민트", "グリーン", "緑"],
    "red": [
        "red",
        "burgundy",
        "wine",
        "레드",
        "빨강",
        "빨간",
        "버건디",
        "와인",
        "レッド",
        "ボルドー",
        "赤",
    ],
    "pink": ["pink", "핑크", "분홍", "ピンク"],
    "purple": ["purple", "lavender", "violet", "퍼플", "보라", "라벤더", "パープル", "紫"],
    "yellow": ["yellow", "mustard", "옐로", "노랑", "노란", "머스타드", "イエロー", "黄"],
    "orange": ["orange", "오렌지", "주황", "オレンジ"],
    "multi": [
        "multi",
        "multicolor",
        "multicolour",
        "stripe",
        "striped",
        "floral",
        "멀티",
        "스트라이프",
        "マルチ",
    ],
}

FIBER_WORDS = {
    "cotton": ["cotton", "면", "코튼", "綿", "コットン"],
    "wool": ["wool", "merino", "울", "양모", "ウール"],
    "polyester": ["polyester", "폴리에스터", "폴리에스테르", "ポリエステル"],
    "nylon": ["nylon", "polyamide", "나일론", "폴리아미드", "ナイロン"],
    "acrylic": ["acrylic", "아크릴", "アクリル"],
    "rayon": ["viscose", "rayon", "레이온", "비스코스", "レーヨン"],
    "linen": ["linen", "린넨", "리넨", "麻", "リネン"],
    "cashmere": ["cashmere", "캐시미어", "カシミヤ"],
    "elastane": [
        "elastane",
        "spandex",
        "polyurethane",
        "폴리우레탄",
        "스판덱스",
        "엘라스탄",
        "ポリウレタン",
    ],
    "silk": ["silk", "실크", "シルク", "絹"],
    "leather": ["leather", "가죽", "레더", "レザー", "革"],
    "down": ["down", "덕다운", "구스다운", "ダウン"],
}

# 구매를 권하거나 막는 단정 표현 (purchase-report.md 예외 상황)
FORBIDDEN = re.compile(
    r"사세요|사지\s*마|사지\s*않는\s*(게|것이)|사는\s*(게|것이)\s*(좋|나아)|살\s*필요|꼭\s*사"
    r"|구매하세요|구매하지\s*마|구매를\s*(추천|권|말리|미루)|추천(해요|합니다|드려요|드립니다)"
    r"|권해요|권합니다|중복\s*구매|확률"
)
NUMBER = re.compile(r"\d+(?:\.\d+)?")


def _words_in(text: str, words: list[str]) -> bool:
    lower = text.lower()
    for word in words:
        if word.isascii():
            if re.search(rf"(?<![a-z]){re.escape(word)}(?![a-z])", lower):
                return True
        elif word in text:
            return True
    return False


def color_from_name(text: str | None) -> str | None:
    """색 이름 하나를 15색 중 하나로. 없거나 여러 색이면 None"""
    if not text:
        return None
    found = {color for color, words in COLOR_WORDS.items() if _words_in(text, words)}
    if "navy" in found:
        found.discard("blue")  # navy blue
    return found.pop() if len(found) == 1 else None


def fibers(text: str | None) -> frozenset[str]:
    if not text:
        return frozenset()
    return frozenset(f for f, words in FIBER_WORDS.items() if _words_in(text, words))


def _squash(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def _item(name: str, product: str | None, cloth: str | None, basis: str | None = None) -> dict:
    return {
        "field": name,
        "label": LABELS[name],
        "product": product,
        "cloth": cloth,
        "basis": basis,
        "text": None,
    }


def _ko(fields: dict, name: str) -> str | None:
    value = fields.get(name)
    return value["ko"] if value else None


def _product_color(
    fields: dict, photo_color: str | None
) -> tuple[str | None, str | None, str | None]:
    """(15색, 보여 줄 글자, 근거 page|photo)"""
    value = fields.get("color")
    if value:
        enum = color_from_name(value["ko"]) or color_from_name(value["original"])
        if enum:
            return enum, value["ko"], "page"
    if photo_color in COLOR_KO:
        return photo_color, COLOR_KO[photo_color], "photo"
    return None, value["ko"] if value else None, None


def _verdict(parts: dict, item: dict, same: bool) -> None:
    if item["product"] is None or item["cloth"] is None:
        parts["unknown"].append(item)
    else:
        parts["similar" if same else "different"].append(item)


def compare(product: sqlite3.Row, fields: dict, photo_color: str | None, cloth: dict) -> dict:
    parts: dict[str, list] = {"similar": [], "different": [], "unknown": []}

    p_cat, c_cat = product["category"], cloth["category"]
    _verdict(
        parts,
        _item("category", CATEGORY_KO.get(p_cat) if p_cat else None, CATEGORY_KO.get(c_cat)),
        p_cat == c_cat,
    )

    p_color, p_color_text, basis = _product_color(fields, photo_color)
    color = _item("color", p_color_text if p_color else None, COLOR_KO.get(cloth["color"]), basis)
    _verdict(parts, color, p_color == cloth["color"])

    p_mat, c_mat = _ko(fields, "material"), cloth["material"]
    p_fibers = fibers(p_mat) or fibers((fields.get("material") or {}).get("original"))
    c_fibers = fibers(c_mat)
    if p_fibers and c_fibers:
        same_material = p_fibers == c_fibers
    else:
        same_material = bool(p_mat and c_mat) and _squash(p_mat) == _squash(c_mat)
    _verdict(parts, _item("material", p_mat, c_mat), same_material)

    if _ko(fields, "fit"):
        parts["unknown"].append(_item("fit", _ko(fields, "fit"), None))
    return parts


def product_only(product: sqlite3.Row, fields: dict, photo_color: str | None) -> dict:
    """옷장에 비슷한 옷이 없을 때: 확인 안 된 점에 상품 정보만 정리"""
    unknown = []
    if product["category"]:
        unknown.append(_item("category", CATEGORY_KO.get(product["category"]), None))
    p_color, p_color_text, basis = _product_color(fields, photo_color)
    if p_color_text:
        unknown.append(_item("color", p_color_text, None, basis))
    for name in ("material", "fit"):
        if _ko(fields, name):
            unknown.append(_item(name, _ko(fields, name), None))
    return {"similar": [], "different": [], "unknown": unknown}


def _allowed(sentence: str, numbers: set[str]) -> bool:
    if FORBIDDEN.search(sentence):
        log.warning("리포트 문장에서 금지 표현을 뺐어요: %r", sentence)
        return False
    invented = [n for n in NUMBER.findall(sentence) if n not in numbers]
    if invented:
        log.warning("리포트 문장에 데이터에 없는 숫자 %s가 있어 뺐어요: %r", invented, sentence)
        return False
    return True


def _numbers(*texts: str | None) -> set[str]:
    return {n for t in texts if t for n in NUMBER.findall(t)}


def _write_sentences(
    writer: ReportWriter | None, parts: dict, cloth_name: str | None, product_name: str | None
) -> str | None:
    """LLM 문장을 항목에 넣고 요약을 돌려준다. 실패하면 문장 없이 비교표만 남는다"""
    items = [item for part in ("similar", "different", "unknown") for item in parts[part]]
    if writer is None or not items:
        return None
    facts = [
        {
            "id": i,
            # 비교할 옷이 없으면 상품 정보만 적게 한다 ("내 옷 정보 없음"이라고 쓰지 않게)
            "part": part if cloth_name else "product_info",
            "field": item["field"],
            "label": item["label"],
            "product": item["product"],
            "cloth": item["cloth"],
            "basis": item["basis"],
        }
        for i, (part, item) in enumerate(
            (part, item) for part in ("similar", "different", "unknown") for item in parts[part]
        )
    ]
    try:
        written = writer.write(facts, cloth_name, product_name)
    except Exception:
        log.exception("리포트 문장 만들기 실패. 비교표만 보여 줍니다")
        return None

    all_numbers = _numbers(*(v for item in items for v in (item["product"], item["cloth"])))
    for w in written.items:
        if 0 <= w.id < len(items) and w.text.strip():
            item = items[w.id]
            if _allowed(w.text, _numbers(item["product"], item["cloth"])):
                item["text"] = w.text.strip()
    sentences = [s for s in re.split(r"(?<=[.!?。])\s+", written.summary.strip()) if s]
    kept = [s for s in sentences if _allowed(s, all_numbers)]
    return " ".join(kept) or None


def _cloth_name(cloth: dict) -> str:
    return (
        cloth["name"]
        or f"{COLOR_KO.get(cloth['color'], '')} {CATEGORY_KO.get(cloth['category'], '')}".strip()
    )


def build_report(
    conn: sqlite3.Connection,
    settings: Settings,
    analyzer: ImageAnalyzer,
    writer: ReportWriter | None,
    user_id: str,
    product_id: int,
) -> dict | None:
    """다른 사용자의 상품이거나 없으면 None. 상품 사진이 없으면 MissingProductImage"""
    found = analyze_product(conn, settings, analyzer, user_id, product_id)
    if found is None:
        return None
    product, analysis = found
    result = rank_similar(conn, settings, analyzer, user_id, product, analysis)
    fields = json.loads(product["fields"])
    product_name = _ko(fields, "name")

    if result["status"] == "ok":
        top = result["items"][0]
        cloth, level = top["cloth"], top["level"]
        parts = compare(product, fields, analysis.color, cloth)
        headline = "가장 비슷한 내 옷과 비교했어요"
        summary = _write_sentences(writer, parts, _cloth_name(cloth), product_name)
    else:
        cloth, level = None, None
        parts = product_only(product, fields, analysis.color)
        headline = "옷장에 비슷한 옷이 없어요"
        summary = _write_sentences(writer, parts, None, product_name)

    body = {
        "product_id": product_id,
        "status": "ok" if cloth else "no_similar",
        "headline": headline,
        "cloth": cloth,
        "level": level,
        "summary": summary,
        **parts,
    }
    compared = [
        {
            "cloth_id": i["cloth"]["id"],
            "image_similarity": i["image_similarity"],
            "level": i["level"],
        }
        for i in result["items"]
    ]
    with conn:
        cur = conn.execute(
            "INSERT INTO reports (product_id, similar, body) VALUES (?, ?, ?)",
            (product_id, json.dumps(compared), json.dumps(body, ensure_ascii=False)),
        )
    return {"id": cur.lastrowid, **body}
