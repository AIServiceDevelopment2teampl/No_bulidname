"""상품 페이지 글자에서 필드를 찾고 한국어로 옮긴다 (translation/product-extraction.md).

모델은 Claude Haiku 5.5 (2026-10-28 결정). 결과는 구조화 출력(Pydantic)으로 받는다.
LLM이 준 값은 그대로 믿지 않는다.
services/products.py가 original이 입력 글자에 실제로 있는지 확인한다 (근거 검사).
"""

from typing import Literal, Protocol

from pydantic import BaseModel, Field

MODEL = "claude-haiku-5-5"


class FieldValue(BaseModel):
    original: str = Field(description="페이지 글자에서 그대로 복사한 부분 (한 글자도 바꾸지 않음)")
    ko: str = Field(description="original을 자연스러운 한국어로 옮긴 것")


class ExtractedProduct(BaseModel):
    lang: Literal["ko", "en", "ja", "other"]
    name: FieldValue | None
    category: Literal["top", "bottom", "outer", "other"] | None
    category_evidence: str | None = Field(
        description="종류를 판단한 근거 단어를 원문 그대로 (예: Cardigan)"
    )
    color: FieldValue | None
    material: FieldValue | None
    fit: FieldValue | None
    size: FieldValue | None
    care: FieldValue | None


class ProductLLM(Protocol):
    def extract(self, page_text: str) -> ExtractedProduct: ...


SYSTEM = "\n".join(
    [
        "You read the text of one clothing product page from an online shop "
        "and fill a form for Korean shoppers.",
        "",
        "Rules:",
        "- For every field, `original` must be copied character for character from the page "
        "text. Do not fix typos, translate, reorder, or join pieces that are apart. "
        "If the page does not state it, use null.",
        "- Never guess from images, the product type, or what such products usually are. "
        "A field the text does not state is null (material, fit, size, care are often missing).",
        "- `ko` is a natural Korean translation of `original`. Keep numbers and units as written.",
        "- name: the product name. color: the selected color, or the list of offered colors "
        "as written. material: fabric composition. fit: fit or silhouette words "
        "(e.g. Regular fit, oversized). size: offered sizes and any measurements. "
        "care: washing or care instructions.",
        "- category: top (shirts, t-shirts, knits, cardigans, hoodies), bottom (pants, jeans, "
        "skirts, shorts), outer (coats, jackets, padded jackets, blazers), other (shoes, bags, "
        "dresses, accessories). category_evidence is the word from the text you used. "
        "null if unclear.",
        "- lang: the main language of the page text.",
    ]
)


class ClaudeProductLLM:
    def __init__(self, client=None):
        import anthropic

        # 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다. 코드·저장소에 두지 않는다
        self._client = client or anthropic.Anthropic()

    def extract(self, page_text: str) -> ExtractedProduct:
        response = self._client.messages.parse(
            model=MODEL,
            max_tokens=4000,
            system=SYSTEM,
            # 정해진 칸 채우기라 생각을 짧게 해 비용을 줄인다
            output_config={"effort": "low"},
            messages=[{"role": "user", "content": f"<page_text>\n{page_text}\n</page_text>"}],
            output_format=ExtractedProduct,
        )
        if response.stop_reason != "end_turn" or response.parsed_output is None:
            raise RuntimeError(f"상품 정보를 읽지 못했어요 (stop_reason={response.stop_reason})")
        return response.parsed_output
