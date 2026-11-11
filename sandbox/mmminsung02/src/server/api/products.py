import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse

from server.api.deps import get_conn, get_current_user_id
from server.api.limits import allow
from server.services.clothes import InvalidImageError
from server.services.products import ProductPage, extract_product, product_row_to_dict
from server.services.report import build_report
from server.services.screenshot import NoTextFound, ReadFailed, analyze_screenshot
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


@router.get("/v1/products/{product_id}/image")
def get_product_image(product_id: int, request: Request, conn: Conn, user_id: UserId):
    row = conn.execute(
        "SELECT image_path FROM products WHERE id = ? AND user_id = ?", (product_id, user_id)
    ).fetchone()
    if row is None or row["image_path"] is None:
        raise HTTPException(status_code=404, detail="상품 사진을 찾을 수 없어요")
    return FileResponse(request.app.state.settings.data_dir / row["image_path"])


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


@router.post("/v1/products/from-screenshot", status_code=201)
async def from_screenshot(
    request: Request,
    image: Annotated[UploadFile, File()],
    conn: Conn,
    user_id: UserId,
):
    """공유받은 스크린샷 한 장 → 상품 정보 + 비슷한 옷 + 비교 리포트 (shopping/share-intake.md)"""
    state = request.app.state
    settings = state.settings
    data = await image.read(settings.max_upload_bytes + 1)
    if len(data) > settings.max_upload_bytes:
        raise HTTPException(status_code=413, detail="사진 용량이 너무 커요")
    if state.screenshot_reader is None:
        raise HTTPException(status_code=503, detail="서버에 스크린샷 읽기 설정이 없어요")
    if not allow(
        f"{id(request.app)}:screenshot:{user_id}",
        settings.screenshot_window_seconds,
        settings.screenshot_requests_per_window,
    ):
        raise HTTPException(
            status_code=429, detail="요청이 너무 많아요. 잠시 후 다시 시도해 주세요"
        )
    try:
        return analyze_screenshot(
            conn,
            settings,
            state.analyzer,
            state.screenshot_reader,
            state.report_writer,
            state.convert_units,
            user_id,
            data,
        )
    except InvalidImageError:
        raise HTTPException(status_code=400, detail="이미지를 읽을 수 없어요") from None
    except NoTextFound:
        raise HTTPException(
            status_code=422, detail="글자를 읽지 못했어요. 상품 설명이 보이게 다시 캡처해 주세요"
        ) from None
    except ReadFailed:
        raise HTTPException(
            status_code=502, detail="스크린샷을 읽지 못했어요. 잠시 후 다시 시도해 주세요"
        ) from None
