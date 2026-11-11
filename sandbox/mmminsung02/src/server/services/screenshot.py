"""공유받은 스크린샷 한 장 → 상품 정보 → 비슷한 옷 TOP 3 → 비교 리포트 (shopping/share-intake.md).

한 요청에서 끝까지 이어서, 앱은 결과 화면 하나만 기다리면 된다.
- 글자 읽기·상품 칸·옷 사진 위치: Claude 한 번 (ai/screenshot_reader.py)
- 근거 검사·단위 변환·저장: 기능 7과 같은 extract_product
- 옷 사진은 Claude가 알려 준 위치로 잘라 유사 검색에 쓴다 (글자까지 넣으면 비교가 엉망)
"""

import io
import logging
import sqlite3
from collections.abc import Callable

from PIL import Image, ImageOps

from server.ai.analyzer import ImageAnalyzer
from server.ai.product_llm import ExtractedProduct
from server.ai.report_writer import ReportWriter
from server.ai.screenshot_reader import Box, ScreenshotReader
from server.config import Settings
from server.services.clothes import _open_image
from server.services.products import ProductPage, extract_product
from server.services.report import build_report
from server.services.similar import find_similar

log = logging.getLogger(__name__)

# Claude에 보내는 사진의 긴 변. 더 크면 비용만 늘고 읽는 글자는 거의 같다
MAX_SIDE = 1568
MIN_LETTERS = 8
# 원본 픽셀. 이보다 작은 상자는 옷 사진이 아니라고 본다 (아이콘·썸네일)
MIN_BOX_SIDE = 64

NO_TEXT = "글자를 읽지 못했어요. 상품 설명이 보이게 다시 캡처해 주세요"
NO_PHOTO = "스크린샷에서 옷 사진을 찾지 못해 비슷한 옷은 찾지 않았어요"


class NoTextFound(Exception):
    pass


class ReadFailed(Exception):
    pass


class _AlreadyExtracted:
    """extract_product에 '이미 읽은 결과'를 LLM처럼 넘긴다 (Claude를 두 번 부르지 않게)"""

    def __init__(self, extracted: ExtractedProduct):
        self._extracted = extracted

    def extract(self, page_text: str) -> ExtractedProduct:
        return self._extracted


def _for_reader(image: Image.Image) -> tuple[bytes, float]:
    """(보낼 JPEG, 원본 대비 배율)"""
    scale = min(1.0, MAX_SIDE / max(image.size))
    sent = image
    if scale < 1.0:
        sent = image.resize((round(image.width * scale), round(image.height * scale)))
    buf = io.BytesIO()
    sent.save(buf, format="JPEG", quality=85)
    return buf.getvalue(), scale


def _crop(image: Image.Image, box: Box | None, scale: float) -> bytes | None:
    if box is None:
        return None
    x0, x1 = sorted((box.x0 / scale, box.x1 / scale))
    y0, y1 = sorted((box.y0 / scale, box.y1 / scale))
    x0, y0 = max(0, round(x0)), max(0, round(y0))
    x1, y1 = min(image.width, round(x1)), min(image.height, round(y1))
    if x1 - x0 < MIN_BOX_SIDE or y1 - y0 < MIN_BOX_SIDE:
        return None
    buf = io.BytesIO()
    image.crop((x0, y0, x1, y1)).save(buf, format="PNG")
    return buf.getvalue()


def analyze_screenshot(
    conn: sqlite3.Connection,
    settings: Settings,
    analyzer: ImageAnalyzer,
    reader: ScreenshotReader,
    writer: ReportWriter | None,
    convert_units: Callable[[str], str],
    user_id: str,
    data: bytes,
) -> dict:
    """사진이 아니면 InvalidImageError, 글자가 거의 없으면 NoTextFound, Claude 실패는 ReadFailed"""
    image = ImageOps.exif_transpose(_open_image(data)).convert("RGB")
    sent, scale = _for_reader(image)
    try:
        read = reader.read(sent, "image/jpeg")
    except Exception as e:
        log.exception("스크린샷 읽기 실패")
        raise ReadFailed from e
    if sum(ch.isalpha() for ch in read.transcript) < MIN_LETTERS:
        raise NoTextFound

    page = ProductPage(url="", lang=read.product.lang, text=read.transcript)
    product = extract_product(
        conn,
        settings,
        _AlreadyExtracted(read.product),
        lambda url: None,
        convert_units,
        user_id,
        page,
        source="screenshot",
        image_bytes=_crop(image, read.product_box, scale),
        uncertain=read.unclear,
    )
    if not product["has_image"]:
        return {"product": product, "similar": None, "report": None, "notice": NO_PHOTO}

    similar = find_similar(conn, settings, analyzer, user_id, product["id"])
    report = build_report(conn, settings, analyzer, writer, user_id, product["id"])
    return {"product": product, "similar": similar, "report": report, "notice": None}
