"""실제 Claude API로 상품 정보 추출 확인 (돈이 조금 든다, Haiku 5.5 한 번 호출).

실행: ANTHROPIC_API_KEY 환경변수를 설정한 뒤 pytest -m slow tests/server/test_product_llm_slow.py
키가 없으면 건너뛴다. 정확도는 여기서 재지 않는다 ([평가] 30~50개 페이지로 따로).
"""

import os

import pytest

pytestmark = [
    pytest.mark.slow,
    pytest.mark.skipif(not os.environ.get("ANTHROPIC_API_KEY"), reason="ANTHROPIC_API_KEY 없음"),
]

PAGE = """ASOS DESIGN wool mix oversized coat in camel
$129.00
Colour: CAMEL
Product Details: Coats & Jackets by ASOS DESIGN. Notch lapels. Button placket. Oversized fit.
Size & Fit: Model wears: UK 8/ EU 36/ US 4. Model's height: 5'8" (173cm)
Look After Me: Dry clean only
About Me: Main: 50% Wool, 50% Polyester."""


def test_real_claude_fills_fields_from_page_text():
    from server.ai.product_llm import ClaudeProductLLM

    result = ClaudeProductLLM().extract(PAGE)

    assert result.lang == "en"
    assert result.category == "outer"
    assert result.material is not None and result.material.original in PAGE
    assert result.care is not None and result.care.original in PAGE
    assert result.name is not None and result.name.original in PAGE
    # 한국어로 옮겼는지
    assert any("가" <= ch <= "힣" for ch in result.material.ko)
