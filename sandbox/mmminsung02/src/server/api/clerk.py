import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, StringConstraints

from server.api.deps import get_conn, get_current_user_id
from server.api.limits import allow
from server.services.clerk import (
    ProductNotFound,
    answer_question,
    list_messages,
    matching_clothes,
)

router = APIRouter(tags=["clerk"])

Conn = Annotated[sqlite3.Connection, Depends(get_conn)]
UserId = Annotated[str, Depends(get_current_user_id)]
NOT_FOUND = "상품을 찾을 수 없어요"


class Question(BaseModel):
    product_id: int | None = None
    text: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=500)]


def _check_limit(request: Request, user_id: str) -> None:
    settings = request.app.state.settings
    if not allow(
        f"{id(request.app)}:clerk:{user_id}",
        settings.clerk_window_seconds,
        settings.clerk_requests_per_window,
    ):
        raise HTTPException(
            status_code=429, detail="요청이 너무 많아요. 잠시 후 다시 시도해 주세요"
        )


def _llm(request: Request):
    llm = request.app.state.clerk_llm
    if llm is None:
        raise HTTPException(status_code=503, detail="서버에 AI 점원 설정이 없어요")
    return llm


@router.post("/v1/clerk/messages", status_code=201)
def ask(question: Question, request: Request, conn: Conn, user_id: UserId):
    llm = _llm(request)
    _check_limit(request, user_id)
    try:
        message = answer_question(conn, llm, user_id, question.product_id, question.text)
    except ProductNotFound:
        raise HTTPException(status_code=404, detail=NOT_FOUND) from None
    except Exception:
        raise HTTPException(
            status_code=502, detail="답하지 못했어요. 잠시 후 다시 시도해 주세요"
        ) from None
    return {"message": message}


@router.get("/v1/clerk/messages")
def messages(conn: Conn, user_id: UserId, product_id: int | None = None):
    try:
        return {"items": list_messages(conn, user_id, product_id)}
    except ProductNotFound:
        raise HTTPException(status_code=404, detail=NOT_FOUND) from None


@router.post("/v1/products/{product_id}/matching")
def matching(product_id: int, request: Request, conn: Conn, user_id: UserId):
    llm = _llm(request)
    _check_limit(request, user_id)
    try:
        return matching_clothes(conn, llm, user_id, product_id)
    except ProductNotFound:
        raise HTTPException(status_code=404, detail=NOT_FOUND) from None
    except Exception:
        raise HTTPException(
            status_code=502, detail="어울리는 옷을 고르지 못했어요. 잠시 후 다시 시도해 주세요"
        ) from None
