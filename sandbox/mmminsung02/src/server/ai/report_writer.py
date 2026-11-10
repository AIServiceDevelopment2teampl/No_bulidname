"""구매 전 비교 리포트 문장 다듬기 (compare/purchase-report.md).

비슷한 점 / 다른 점 / 확인 안 된 점은 서버 규칙이 이미 정했다.
LLM은 정해진 사실을 문장으로 옮기기만 한다. 결과는 services/report.py가 다시 검사한다
(금지 표현, 데이터에 없는 숫자).
"""

import json
from typing import Protocol

from pydantic import BaseModel

from server.ai.product_llm import MODEL


class WrittenItem(BaseModel):
    id: int
    text: str


class ReportText(BaseModel):
    summary: str
    items: list[WrittenItem]


class ReportWriter(Protocol):
    def write(
        self, facts: list[dict], cloth_name: str | None, product_name: str | None
    ) -> ReportText:
        """facts: [{id, part, field, label, product, cloth, basis}]. 실패하면 예외"""
        ...


SYSTEM = "\n".join(
    [
        "You write a short comparison in Korean (polite 해요체) between a clothing product "
        "a shopper is looking at and one garment they already own.",
        "- The input facts were already decided by rules. "
        "part is similar, different, unknown or product_info.",
        "- For every fact id write one short sentence. Use only the values in that fact.",
        "- similar: say both are alike in that point. different: say how they differ, "
        "with both values. unknown: say which side has no information.",
        "- basis tells where the product color came from: page = written on the shop page, "
        "photo = read from the product photo. Say 사진 기준 only when basis is photo.",
        "- Call the product '새 상품' and the owned garment '내 옷' (or its name if given).",
        "- product_info (my_garment is null): the closet has no similar garment. Only state "
        "the product's value and do not mention my garment at all.",
        "- Then write summary: one or two sentences about the most useful points.",
        "- Do not give styling, coordination or care advice.",
        "- Never add facts, numbers, sizes or materials that are not in the input.",
        "- Never tell the shopper to buy or not to buy, never recommend, "
        "and never mention probability or duplicate purchase. The shopper decides.",
    ]
)


class ClaudeReportWriter:
    def __init__(self, client=None):
        if client is None:
            import anthropic

            # 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다
            client = anthropic.Anthropic()
        self._client = client

    def write(
        self, facts: list[dict], cloth_name: str | None, product_name: str | None
    ) -> ReportText:
        payload = json.dumps(
            {"product_name": product_name, "my_garment": cloth_name, "facts": facts},
            ensure_ascii=False,
        )
        response = self._client.messages.parse(
            model=MODEL,
            max_tokens=2000,
            system=SYSTEM,
            output_config={"effort": "low"},
            messages=[{"role": "user", "content": payload}],
            output_format=ReportText,
        )
        parsed = response.parsed_output
        if response.stop_reason != "end_turn" or parsed is None:
            raise RuntimeError(
                f"리포트 문장을 만들지 못했어요 (stop_reason={response.stop_reason})"
            )
        return parsed
