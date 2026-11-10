import sqlite3
import time
from collections import defaultdict, deque
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field, StringConstraints

from server.api.deps import get_conn, get_current_user_id
from server.services.translate import TranslationFailed, translate_texts

router = APIRouter(tags=["translate"])

# 사용자별 최근 요청 시각. 서버 한 대라 메모리에 둔다 (다시 켜면 비워진다)
_recent: dict[str, deque[float]] = defaultdict(deque)


def _allow(user_id: str, window: int, limit: int) -> bool:
    now = time.monotonic()
    times = _recent[user_id]
    while times and now - times[0] > window:
        times.popleft()
    if len(times) >= limit:
        return False
    times.append(now)
    return True


Text = Annotated[str, StringConstraints(max_length=2000)]


class TranslateRequest(BaseModel):
    # 한 번에 보내는 문장 수 상한. 주입 스크립트가 화면에 보이는 것부터 나눠 보낸다
    texts: Annotated[list[Text], Field(max_length=200)]
    source_lang: str | None = None


@router.post("/v1/translate")
def translate(
    body: TranslateRequest,
    request: Request,
    conn: Annotated[sqlite3.Connection, Depends(get_conn)],
    # 돈이 드는 호출이라 로그인한 사용자만. 번역 캐시는 사용자끼리 함께 쓴다
    user_id: Annotated[str, Depends(get_current_user_id)],
):
    settings = request.app.state.settings
    if not _allow(
        f"{id(request.app)}:{user_id}",
        settings.translate_window_seconds,
        settings.translate_requests_per_window,
    ):
        raise HTTPException(
            status_code=429, detail="번역 요청이 너무 많아요. 잠시 후 다시 시도해 주세요"
        )
    translator = request.app.state.translator
    if translator is None:
        raise HTTPException(status_code=503, detail="서버에 번역 설정이 없어요")
    try:
        out = translate_texts(conn, translator, request.app.state.convert_units, body.texts)
    except TranslationFailed:
        raise HTTPException(status_code=502, detail="번역하지 못했어요") from None
    return {"translations": out}
