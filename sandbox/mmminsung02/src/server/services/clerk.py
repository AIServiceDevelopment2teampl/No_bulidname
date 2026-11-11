"""AI 점원 (compare/ai-clerk.md).

- LLM에 보내는 것은 상품 정보(칸 값)·비교 리포트 요약·옷장 속성뿐이다.
  사용자 id, 이미지 경로, 상품 주소, 페이지 원문은 보내지 않는다.
- 대화 기록은 상품별로 저장한다. 같은 주소의 상품을 다시 열면 이어서 보인다.
- 어울리는 옷 추천은 상품과 같은 종류를 빼고 고른다 (2026-11-11 결정), 최대 3벌.
"""

import json
import logging
import re
import sqlite3

from server.ai.clerk_llm import ClerkLLM
from server.services.clothes import cloth_row_to_dict
from server.services.report import FORBIDDEN, NUMBER

log = logging.getLogger(__name__)

NO_INFO = "페이지에 그 정보가 없어요"
NEEDS_PRODUCT = "상품 비교는 상품 페이지에서 눌러 주세요"
CANNOT_ANSWER = "그 질문에는 답하기 어려워요"
EMPTY_CLOSET = "옷장에 옷을 등록하면 어울리는 옷을 골라 드려요"
NO_MATCH = "어울리는 옷을 찾지 못했어요"

HISTORY_FOR_LLM = 10
MAX_CLOSET_FOR_LLM = 100
MAX_PICKS = 3
CLOTH_ATTRS = ("id", "category", "color", "name", "brand", "material", "size")


class ProductNotFound(Exception):
    pass


def _product(conn: sqlite3.Connection, user_id: str, product_id: int) -> sqlite3.Row:
    row = conn.execute(
        "SELECT * FROM products WHERE id = ? AND user_id = ?", (product_id, user_id)
    ).fetchone()
    if row is None:
        raise ProductNotFound
    return row


def _cloth_attrs(row: sqlite3.Row) -> dict:
    return {k: row[k] for k in CLOTH_ATTRS if row[k] is not None}


def _product_context(row: sqlite3.Row) -> dict:
    fields = json.loads(row["fields"])
    return {
        "category": row["category"],
        "fields": {k: {"ko": v["ko"], "original": v["original"]} for k, v in fields.items() if v},
    }


def _report_context(conn: sqlite3.Connection, product_id: int) -> dict | None:
    row = conn.execute(
        "SELECT body FROM reports WHERE product_id = ? ORDER BY id DESC LIMIT 1", (product_id,)
    ).fetchone()
    if row is None:
        return None
    body = json.loads(row["body"])
    # 옷 정보(사진 주소 등)는 빼고 비교한 값만
    return {
        "summary": body.get("summary"),
        **{
            part: [
                {"label": i["label"], "product": i["product"], "my_garment": i["cloth"]}
                for i in body.get(part, [])
            ]
            for part in ("similar", "different", "unknown")
        },
    }


def _closet(conn: sqlite3.Connection, user_id: str) -> list[sqlite3.Row]:
    return conn.execute(
        "SELECT * FROM clothes WHERE user_id = ? ORDER BY id DESC LIMIT ?",
        (user_id, MAX_CLOSET_FOR_LLM),
    ).fetchall()


def _thread_condition(conn: sqlite3.Connection, user_id: str, product_id: int | None):
    """대화 묶음: 상품이 없으면 일반 대화, 주소가 있으면 같은 주소의 상품 전부"""
    if product_id is None:
        return "m.user_id = ? AND m.product_id IS NULL", [user_id]
    product = _product(conn, user_id, product_id)
    if product["url"]:
        return (
            "m.user_id = ? AND m.product_id IN"
            " (SELECT id FROM products WHERE user_id = ? AND url = ?)",
            [user_id, user_id, product["url"]],
        )
    return "m.user_id = ? AND m.product_id = ?", [user_id, product_id]


def list_messages(conn: sqlite3.Connection, user_id: str, product_id: int | None) -> list[dict]:
    condition, args = _thread_condition(conn, user_id, product_id)
    rows = conn.execute(
        f"SELECT m.id, m.role, m.content, m.created_at FROM clerk_messages m"
        f" WHERE {condition} ORDER BY m.id",
        args,
    ).fetchall()
    return [dict(r) for r in rows]


def _clean(text: str, found: bool, needs_product: bool, context: dict) -> str:
    on_product = context["product"] is not None
    if not on_product and needs_product:
        return NEEDS_PRODUCT
    if not found:
        return NO_INFO if on_product else CANNOT_ANSWER
    kept = []
    for sentence in [s for s in re.split(r"(?<=[.!?。])\s+", text.strip()) if s]:
        if FORBIDDEN.search(sentence):
            log.warning("점원 답에서 금지 표현을 뺐어요: %r", sentence)
            continue
        kept.append(sentence)
    if on_product:
        known = set(NUMBER.findall(json.dumps(context, ensure_ascii=False)))
        invented = [n for n in NUMBER.findall(" ".join(kept)) if n not in known]
        if invented:
            log.warning("점원 답에 데이터에 없는 숫자 %s가 있어 '정보 없음'으로 바꿨어요", invented)
            return NO_INFO
    if not kept:
        return NO_INFO if on_product else CANNOT_ANSWER
    return " ".join(kept)


def answer_question(
    conn: sqlite3.Connection,
    llm: ClerkLLM,
    user_id: str,
    product_id: int | None,
    question: str,
) -> dict:
    """ProductNotFound, LLM 실패는 예외 그대로 (그때는 아무것도 저장하지 않는다)"""
    product = _product(conn, user_id, product_id) if product_id is not None else None
    context = {
        "product": _product_context(product) if product else None,
        "report": _report_context(conn, product_id) if product else None,
        "closet": [_cloth_attrs(r) for r in _closet(conn, user_id)],
    }
    past = list_messages(conn, user_id, product_id)[-HISTORY_FOR_LLM:]
    history = [{"role": m["role"], "content": m["content"]} for m in past]
    result = llm.answer(context, history, question)
    reply = _clean(result.answer, result.found, result.needs_product, context)

    with conn:
        for role, content in (("user", question), ("assistant", reply)):
            cur = conn.execute(
                "INSERT INTO clerk_messages (user_id, product_id, role, content)"
                " VALUES (?, ?, ?, ?)",
                (user_id, product_id, role, content),
            )
    row = conn.execute(
        "SELECT id, role, content, created_at FROM clerk_messages WHERE id = ?", (cur.lastrowid,)
    ).fetchone()
    return dict(row)


def matching_clothes(
    conn: sqlite3.Connection, llm: ClerkLLM, user_id: str, product_id: int
) -> dict:
    """이 상품과 어울리는 내 옷 1~3벌. ProductNotFound, LLM 실패는 예외 그대로"""
    product = _product(conn, user_id, product_id)
    closet = _closet(conn, user_id)
    if not closet:
        return {"status": "empty_closet", "message": EMPTY_CLOSET, "items": []}
    candidates = {r["id"]: r for r in closet if r["category"] != product["category"]}
    if not candidates:
        return {"status": "none", "message": NO_MATCH, "items": []}

    picks = llm.pick(
        {"product": _product_context(product)}, [_cloth_attrs(r) for r in candidates.values()]
    )
    items, seen = [], set()
    for pick in picks.items:
        if pick.cloth_id in candidates and pick.cloth_id not in seen:
            reason = pick.reason.strip()
            if FORBIDDEN.search(reason):
                reason = ""
            seen.add(pick.cloth_id)
            items.append({"cloth": cloth_row_to_dict(candidates[pick.cloth_id]), "reason": reason})
        else:
            log.info("옷장에 없거나 같은 종류인 추천을 걸렀어요: %s", pick.cloth_id)
    if not items:
        return {"status": "none", "message": NO_MATCH, "items": []}
    return {"status": "ok", "message": None, "items": items[:MAX_PICKS]}
