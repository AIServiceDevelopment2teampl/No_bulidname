import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, File, HTTPException, Query, Request, Response, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

from server.api.deps import get_conn, get_current_user_id
from server.services.clothes import (
    InvalidImageError,
    InvalidValueError,
    cloth_row_to_dict,
    delete_cloth,
    delete_user_data,
    get_cloth_row,
    list_clothes,
    register_photo,
    update_cloth,
)

router = APIRouter(tags=["clothes"])

Conn = Annotated[sqlite3.Connection, Depends(get_conn)]
UserId = Annotated[str, Depends(get_current_user_id)]

NOT_FOUND = "옷을 찾을 수 없어요"


class ClothUpdate(BaseModel):
    category: str | None = None
    color: str | None = None
    name: str | None = None
    brand: str | None = None
    material: str | None = None
    size: str | None = None


@router.post("/v1/clothes", status_code=201)
async def upload_cloth_photo(
    request: Request,
    image: Annotated[UploadFile, File()],
    conn: Conn,
    user_id: UserId,
):
    settings = request.app.state.settings
    data = await image.read(settings.max_upload_bytes + 1)
    if len(data) > settings.max_upload_bytes:
        raise HTTPException(status_code=413, detail="사진 용량이 너무 커요")

    try:
        result = register_photo(conn, settings, request.app.state.analyzer, user_id, data)
    except InvalidImageError:
        raise HTTPException(status_code=400, detail="이미지를 읽을 수 없어요") from None

    if result.duplicate:
        return JSONResponse(
            status_code=200,
            content={**result.cloth, "duplicate": True, "message": "이미 등록된 사진이에요"},
        )
    return {**result.cloth, "duplicate": False}


@router.get("/v1/clothes")
def get_clothes(
    conn: Conn,
    user_id: UserId,
    category: str | None = None,
    color: str | None = None,
    limit: Annotated[int, Query(ge=1, le=200)] = 100,
    offset: Annotated[int, Query(ge=0)] = 0,
):
    try:
        return list_clothes(conn, user_id, category, color, limit, offset)
    except InvalidValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from None


@router.get("/v1/clothes/{cloth_id}")
def get_cloth(cloth_id: int, conn: Conn, user_id: UserId):
    row = get_cloth_row(conn, user_id, cloth_id)
    if row is None:
        raise HTTPException(status_code=404, detail=NOT_FOUND)
    return cloth_row_to_dict(row)


@router.get("/v1/clothes/{cloth_id}/image")
def get_cloth_image(cloth_id: int, request: Request, conn: Conn, user_id: UserId):
    row = get_cloth_row(conn, user_id, cloth_id)
    if row is None or row["image_path"] is None:
        raise HTTPException(status_code=404, detail=NOT_FOUND)
    return FileResponse(request.app.state.settings.data_dir / row["image_path"])


@router.patch("/v1/clothes/{cloth_id}")
def patch_cloth(cloth_id: int, update: ClothUpdate, conn: Conn, user_id: UserId):
    try:
        cloth = update_cloth(conn, user_id, cloth_id, update.model_dump(exclude_unset=True))
    except InvalidValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from None
    if cloth is None:
        raise HTTPException(status_code=404, detail=NOT_FOUND)
    return cloth


@router.delete("/v1/clothes/{cloth_id}", status_code=204)
def remove_cloth(cloth_id: int, request: Request, conn: Conn, user_id: UserId):
    if not delete_cloth(conn, request.app.state.settings, user_id, cloth_id):
        raise HTTPException(status_code=404, detail=NOT_FOUND)
    return Response(status_code=204)


@router.delete("/v1/me/data", status_code=204)
def remove_my_data(request: Request, conn: Conn, user_id: UserId):
    delete_user_data(conn, request.app.state.settings, user_id)
    return Response(status_code=204)
