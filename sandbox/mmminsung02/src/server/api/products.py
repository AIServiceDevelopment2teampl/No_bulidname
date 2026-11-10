import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request

from server.api.deps import get_conn, get_current_user_id
from server.services.similar import MissingProductImage, find_similar

router = APIRouter(tags=["products"])


@router.post("/v1/products/{product_id}/similar")
def similar_clothes(
    product_id: int,
    request: Request,
    conn: Annotated[sqlite3.Connection, Depends(get_conn)],
    user_id: Annotated[str, Depends(get_current_user_id)],
):
    state = request.app.state
    try:
        result = find_similar(conn, state.settings, state.analyzer, user_id, product_id)
    except MissingProductImage:
        raise HTTPException(status_code=400, detail="상품 사진을 찾지 못했어요") from None
    if result is None:
        raise HTTPException(status_code=404, detail="상품을 찾을 수 없어요")
    return result
