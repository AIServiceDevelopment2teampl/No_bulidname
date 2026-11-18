"""주문내역 캡처·영수증 사진 읽기 (auto-register/order-capture.md, receipt.md).

글자 읽기(OCR)는 Claude가 사진을 직접 읽는다 (2026-11-11 결정). 사진 한 장에 한 번 호출한다.
한 번의 호출로 ① 보이는 글자 전부 ② 상품 줄 ③ 상품 썸네일 위치를 받는다.
services/imports.py가 상품 줄이 ①에 실제로 있는지 다시 확인하고(근거 검사),
이름·주소·연락처·결제 정보 형식의 값을 지운다.
"""

import base64
from typing import Literal, Protocol

from pydantic import BaseModel, Field

from server.ai.product_llm import MODEL
from server.ai.screenshot_reader import Box
from server.domain import COLORS


class ImportItem(BaseModel):
    name: str = Field(description="상품명. 사진의 글자 그대로 복사 (번역·수정하지 않음)")
    option: str | None = Field(description="옵션 글자 그대로 (색상·사이즈 등). 없으면 null")
    color: Literal[tuple(COLORS)] | None = Field(  # type: ignore[valid-type]
        description="옵션이나 상품명에 적힌 색을 이 목록에서 고른 값. 글자에 색이 없으면 null"
    )
    size: str | None = Field(description="사이즈 글자 그대로 (예: M, 30, 250). 없으면 null")
    price: str | None = Field(description="그 상품의 가격 글자 그대로 (통화 기호 포함)")
    date: str | None = Field(description="주문일·구매일을 YYYY-MM-DD로. 사진에 없으면 null")
    product_code: str | None = Field(description="품번·상품 코드 글자 그대로. 없으면 null")
    is_clothing: bool = Field(
        description="입는 옷이면 true. 신발·가방·화장품·생활용품·쇼핑백은 false"
    )
    category: Literal["top", "bottom", "outer", "other"] | None
    thumbnail: Box | None = Field(description="이 상품의 썸네일 사진 위치. 없으면 null")


class ImportRead(BaseModel):
    transcript: str = Field(
        description="사진에 보이는 글자 전부, 위에서 아래 순서, 그대로 옮겨 적음"
    )
    order_number: str | None = Field(description="주문번호 글자 그대로. 없으면 null")
    store: str | None = Field(description="쇼핑몰·매장 이름 글자 그대로. 없으면 null")
    items: list[ImportItem]


class ImportReader(Protocol):
    def read(self, image_bytes: bytes, media_type: str, kind: str) -> ImportRead:
        """kind: order_capture / receipt. 실패하면 예외"""
        ...


_COMMON = [
    "Step 1 - transcript: copy every piece of text you can see, top to bottom, exactly as "
    "written (do not translate or fix). Skip the phone status bar (time, battery).",
    "Step 2 - items: one entry per purchased product line. Copy name, option, size, price, "
    "product_code character for character from the transcript. Never invent values; "
    "use null when the image does not show it.",
    "- color: pick from the list only when the text states the color "
    "(e.g. 블랙/Black/ブラック -> black, 아이보리/크림 -> beige). Otherwise null.",
    "- category: top (shirts, t-shirts, knits, cardigans, hoodies, vests), bottom (pants, "
    "jeans, skirts, shorts), outer (coats, jackets, padding, blazers), other (dresses, "
    "shoes, bags, accessories). null if unclear (abbreviated names).",
    "- Never put the buyer's or recipient's name, address, phone number, email, or any "
    "payment detail (card number, approval number) in any item field.",
]

ORDER_SYSTEM = "\n".join(
    [
        "You read one screenshot of an order history screen from a shopping app or site.",
        *_COMMON,
        "- thumbnail: the pixel box of that product's thumbnail photo in this image, "
        "only the photo. null if there is no photo for the line.",
        "- order_number: the order number if shown.",
    ]
)

RECEIPT_SYSTEM = "\n".join(
    [
        "You read one photo of a paper receipt from an offline store.",
        *_COMMON,
        "- Receipts have no product photos: thumbnail is always null.",
        "- Shopping bags, packaging, gift wrap, delivery fees and discounts are not products: "
        "list them only with is_clothing false.",
        "- store: the store name printed on the receipt.",
    ]
)


class ClaudeImportReader:
    def __init__(self, client=None):
        if client is None:
            import anthropic

            # 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다
            client = anthropic.Anthropic()
        self._client = client

    def read(self, image_bytes: bytes, media_type: str, kind: str) -> ImportRead:
        import io

        from PIL import Image

        width, height = Image.open(io.BytesIO(image_bytes)).size
        response = self._client.messages.parse(
            model=MODEL,
            max_tokens=16000,
            system=RECEIPT_SYSTEM if kind == "receipt" else ORDER_SYSTEM,
            # 정해진 칸 채우기라 생각을 짧게 해 비용을 줄인다
            output_config={"effort": "low"},
            messages=[
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image",
                            "source": {
                                "type": "base64",
                                "media_type": media_type,
                                "data": base64.standard_b64encode(image_bytes).decode("ascii"),
                            },
                        },
                        {"type": "text", "text": f"This image is {width} x {height} pixels."},
                    ],
                }
            ],
            output_format=ImportRead,
        )
        parsed = response.parsed_output
        if response.stop_reason != "end_turn" or parsed is None:
            raise RuntimeError(f"사진을 읽지 못했어요 (stop_reason={response.stop_reason})")
        return parsed
