import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse

from server.api.deps import get_conn, get_current_user_id
from server.services.clothes import InvalidImageError, register_photo

router = APIRouter(prefix="/v1/clothes", tags=["clothes"])


@router.post("", status_code=201)
async def upload_cloth_photo(
    request: Request,
    image: Annotated[UploadFile, File()],
    conn: Annotated[sqlite3.Connection, Depends(get_conn)],
    user_id: Annotated[str, Depends(get_current_user_id)],
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
