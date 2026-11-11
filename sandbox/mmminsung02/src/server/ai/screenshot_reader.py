"""공유받은 쇼핑 앱 스크린샷 읽기 (shopping/share-intake.md).

글자 읽기(OCR)는 Claude가 사진을 직접 읽는다 (2026-11-11 결정).
한 번의 호출로 ① 보이는 글자 전부 ② 상품 칸(기능 7과 같은 형식) ③ 옷 사진 위치를 받는다.
상품 칸은 services/products.py가 ①에 실제로 있는지 다시 확인한다 (근거 검사).
"""

import base64
from typing import Protocol

from pydantic import BaseModel, Field

from server.ai.product_llm import MODEL, ExtractedProduct
from server.ai.product_llm import SYSTEM as PRODUCT_RULES


class Box(BaseModel):
    """보낸 사진 기준 픽셀 좌표 (왼쪽 위 x0,y0 → 오른쪽 아래 x1,y1)"""

    x0: int
    y0: int
    x1: int
    y1: int


class ScreenshotRead(BaseModel):
    transcript: str = Field(
        description="사진에 보이는 글자 전부, 위에서 아래 순서, 그대로 옮겨 적음"
    )
    unclear: list[str] = Field(
        description="흐리거나 잘려서 확실히 읽지 못한 글자 조각 (transcript 그대로)"
    )
    product_box: Box | None = Field(description="상품 옷 사진 한 장의 위치. 없으면 null")
    product: ExtractedProduct


class ScreenshotReader(Protocol):
    def read(self, image_bytes: bytes, media_type: str) -> ScreenshotRead:
        """실패하면 예외"""
        ...


SYSTEM = "\n".join(
    [
        "You read one phone screenshot of a clothing product from a shopping app "
        "and fill a form for Korean shoppers.",
        "",
        "Step 1 - transcript: copy every piece of text you can see, top to bottom, exactly as "
        "written (do not translate or fix). Skip the phone status bar (time, battery).",
        "Step 2 - unclear: list the pieces of the transcript you could not read with certainty "
        "(blurred, cut off at the edge, too small). Copy them as they appear in the transcript.",
        "Step 3 - product_box: the pixel box of the main product photo (the garment image), "
        "in the coordinates of this image. Only the photo, not the text around it. "
        "null if there is no garment photo.",
        "Step 4 - product: fill it from the transcript only, with these rules. "
        "The page text means your transcript:",
        PRODUCT_RULES,
    ]
)


class ClaudeScreenshotReader:
    def __init__(self, client=None):
        if client is None:
            import anthropic

            # 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다
            client = anthropic.Anthropic()
        self._client = client

    def read(self, image_bytes: bytes, media_type: str) -> ScreenshotRead:
        import io

        from PIL import Image

        width, height = Image.open(io.BytesIO(image_bytes)).size
        response = self._client.messages.parse(
            model=MODEL,
            max_tokens=8000,
            system=SYSTEM,
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
            output_format=ScreenshotRead,
        )
        parsed = response.parsed_output
        if response.stop_reason != "end_turn" or parsed is None:
            raise RuntimeError(f"스크린샷을 읽지 못했어요 (stop_reason={response.stop_reason})")
        return parsed
