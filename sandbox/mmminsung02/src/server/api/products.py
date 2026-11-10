import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request

from server.api.deps import get_conn, get_current_user_id
from server.api.limits import allow
from server.services.products import ProductPage, extract_product, product_row_to_dict
from server.services.report import build_report
from server.services.similar import MissingProductImage, find_similar

router = APIRouter(tags=["products"])

Conn = Annotated[sqlite3.Connection, Depends(get_conn)]
UserId = Annotated[str, Depends(get_current_user_id)]


@router.post("/v1/products/extract", status_code=201)
def extract(page: ProductPage, request: Request, conn: Conn, user_id: UserId):
    state = request.app.state
    return extract_product(
        conn,
        state.settings,
        state.product_llm,
        state.image_fetcher,
        state.convert_units,
        user_id,
        page,
    )


@router.get("/v1/products/{product_id}")
def get_product(product_id: int, conn: Conn, user_id: UserId):
    row = conn.execute(
        "SELECT * FROM products WHERE id = ? AND user_id = ?", (product_id, user_id)
    ).fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="상품을 찾을 수 없어요")
    return product_row_to_dict(row)


@router.post("/v1/products/{product_id}/similar")
def similar_clothes(product_id: int, request: Request, conn: Conn, user_id: UserId):
    state = request.app.state
    try:
        result = find_similar(conn, state.settings, state.analyzer, user_id, product_id)
    except MissingProductImage:
        raise HTTPException(status_code=400, detail="상품 사진을 찾지 못했어요") from None
    if result is None:
        raise HTTPException(status_code=404, detail="상품을 찾을 수 없어요")
    return result


@router.post("/v1/products/{product_id}/report", status_code=201)
def purchase_report(product_id: int, request: Request, conn: Conn, user_id: UserId):
    state = request.app.state
    settings = state.settings
    # 상한을 넘으면 오류 대신 Claude 없이 비교표만 만든다 (비용)
    writer = state.report_writer
    if writer is not None and not allow(
        f"{id(request.app)}:report:{user_id}",
        settings.report_window_seconds,
        settings.report_llm_per_window,
    ):
        writer = None
    try:
        report = build_report(conn, settings, state.analyzer, writer, user_id, product_id)
    except MissingProductImage:
        raise HTTPException(status_code=400, detail="상품 사진을 찾지 못했어요") from None
    if report is None:
        raise HTTPException(status_code=404, detail="상품을 찾을 수 없어요")
    return report
