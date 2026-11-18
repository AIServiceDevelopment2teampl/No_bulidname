"""자동 등록 API (auto-register/*.md, architecture.md의 /v1/imports)."""

import sqlite3
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, Response, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field

from server.api.deps import get_conn, get_current_user_id
from server.api.limits import allow
from server.services.clothes import InvalidImageError, InvalidValueError
from server.services.imports import (
    NOTHING_FOUND,
    UNREADABLE,
    MissingColor,
    NothingFound,
    NotPending,
    ReadFailed,
    Unreadable,
    candidate_image_path,
    confirm_import,
    discard_import,
    get_import_row,
    import_from_images,
    import_in_app,
    import_row_to_dict,
)

router = APIRouter(tags=["imports"])

Conn = Annotated[sqlite3.Connection, Depends(get_conn)]
UserId = Annotated[str, Depends(get_current_user_id)]

NOT_FOUND = "등록 기록을 찾을 수 없어요"
MAX_IMAGES = 5
TOO_MANY = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요"


@router.post("/v1/imports", status_code=201)
async def create_import(
    request: Request,
    conn: Conn,
    user_id: UserId,
    type: Annotated[Literal["order_capture", "receipt"], Form()],
    images: Annotated[list[UploadFile], File()],
):
    """주문내역 캡처(여러 장) · 영수증(한 장) → 등록 후보. 옷장에는 아직 저장하지 않는다"""
    state = request.app.state
    settings = state.settings
    limit = 1 if type == "receipt" else MAX_IMAGES
    if len(images) > limit:
        raise HTTPException(status_code=400, detail=f"사진은 {limit}장까지 올릴 수 있어요")
    datas = []
    for image in images:
        data = await image.read(settings.max_upload_bytes + 1)
        if len(data) > settings.max_upload_bytes:
            raise HTTPException(status_code=413, detail="사진 용량이 너무 커요")
        datas.append(data)
    if state.import_reader is None:
        raise HTTPException(status_code=503, detail="서버에 사진 읽기 설정이 없어요")
    for _ in datas:  # 사진 한 장마다 Claude를 한 번 부른다
        if not allow(
            f"{id(request.app)}:import:{user_id}",
            settings.import_window_seconds,
            settings.import_images_per_window,
        ):
            raise HTTPException(status_code=429, detail=TOO_MANY)
    try:
        return import_from_images(conn, settings, state.import_reader, user_id, type, datas)
    except InvalidImageError:
        raise HTTPException(status_code=400, detail="이미지를 읽을 수 없어요") from None
    except Unreadable:
        raise HTTPException(status_code=422, detail=UNREADABLE[type]) from None
    except NothingFound:
        raise HTTPException(status_code=422, detail=NOTHING_FOUND[type]) from None
    except ReadFailed:
        raise HTTPException(
            status_code=502, detail="사진을 읽지 못했어요. 잠시 후 다시 시도해 주세요"
        ) from None


class OrderItem(BaseModel):
    name: str = Field(max_length=300)
    color: str | None = Field(default=None, max_length=100)
    size: str | None = Field(default=None, max_length=100)
    price: str | None = Field(default=None, max_length=100)
    image_url: str | None = Field(default=None, max_length=2000)
    product_url: str | None = Field(default=None, max_length=2000)


class InAppOrder(BaseModel):
    """order.js가 주문 완료 페이지의 상품 영역에서만 읽은 값"""

    site: str = Field(max_length=200)
    url: str = Field(max_length=2000)
    order_number: str | None = Field(default=None, max_length=100)
    items: list[OrderItem] = Field(max_length=30)


@router.post("/v1/imports/in-app", status_code=201)
def create_in_app_import(order: InAppOrder, request: Request, conn: Conn, user_id: UserId):
    state = request.app.state
    try:
        result, existed = import_in_app(
            conn, state.settings, state.image_fetcher, user_id, order.model_dump()
        )
    except NothingFound:
        raise HTTPException(status_code=422, detail=NOTHING_FOUND["in_app"]) from None
    if existed:
        return JSONResponse(status_code=200, content=result)
    return result


@router.get("/v1/imports/{import_id}")
def get_import(import_id: int, conn: Conn, user_id: UserId):
    row = get_import_row(conn, user_id, import_id)
    if row is None:
        raise HTTPException(status_code=404, detail=NOT_FOUND)
    return import_row_to_dict(row)


@router.get("/v1/imports/{import_id}/candidates/{index}/image")
def get_candidate_image(import_id: int, index: int, request: Request, conn: Conn, user_id: UserId):
    row = get_import_row(conn, user_id, import_id)
    path = None if row is None else candidate_image_path(request.app.state.settings, row, index)
    if path is None:
        raise HTTPException(status_code=404, detail="사진을 찾을 수 없어요")
    return FileResponse(path)


class Choice(BaseModel):
    index: int
    category: str | None = None
    color: str | None = None
    name: str | None = Field(default=None, max_length=300)
    brand: str | None = Field(default=None, max_length=100)
    size: str | None = Field(default=None, max_length=100)


class Confirm(BaseModel):
    items: list[Choice]


@router.post("/v1/imports/{import_id}/confirm")
def confirm(import_id: int, body: Confirm, request: Request, conn: Conn, user_id: UserId):
    """고른 후보만 옷장에 저장한다"""
    state = request.app.state
    choices = [c.model_dump(exclude_unset=True) for c in body.items]
    try:
        result = confirm_import(conn, state.settings, state.analyzer, user_id, import_id, choices)
    except NotPending:
        raise HTTPException(status_code=409, detail="이미 처리한 등록이에요") from None
    except InvalidValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from None
    except MissingColor as e:
        raise HTTPException(status_code=400, detail=f"색을 골라 주세요: {e.name}") from None
    if result is None:
        raise HTTPException(status_code=404, detail=NOT_FOUND)
    return result


@router.delete("/v1/imports/{import_id}", status_code=204)
def discard(import_id: int, request: Request, conn: Conn, user_id: UserId):
    """사용자가 확인 화면·카드를 닫음: 저장하지 않고 후보 사진을 지운다"""
    if not discard_import(conn, request.app.state.settings, user_id, import_id):
        raise HTTPException(status_code=404, detail=NOT_FOUND)
    return Response(status_code=204)
