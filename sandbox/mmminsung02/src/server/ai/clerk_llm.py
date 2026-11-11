"""AI 점원 (compare/ai-clerk.md): 상품 질문 답변과 어울리는 내 옷 고르기.

LLM에는 상품 정보·비교 리포트·옷장 속성만 보낸다 (services/clerk.py가 만든다).
답은 services/clerk.py가 다시 검사한다 (정보 없음·데이터에 없는 숫자·구매 권유).
"""

import json
from typing import Protocol

from pydantic import BaseModel, Field

from server.ai.product_llm import MODEL


class ClerkAnswer(BaseModel):
    answer: str = Field(description="한국어 답 (해요체, 1~3문장)")
    found: bool = Field(description="답의 근거가 주어진 정보에 있으면 true")
    needs_product: bool = Field(
        description="상품 페이지가 아닌데 상품 비교·이 상품에 대한 질문이면 true"
    )


class Pick(BaseModel):
    cloth_id: int
    reason: str = Field(description="어울리는 이유 한 문장 (주어진 속성만 근거로)")


class Picks(BaseModel):
    items: list[Pick]


class ClerkLLM(Protocol):
    def answer(self, context: dict, history: list[dict], question: str) -> ClerkAnswer: ...

    def pick(self, context: dict, closet: list[dict]) -> Picks: ...


ANSWER_SYSTEM = "\n".join(
    [
        "You are a shopping assistant in a Korean closet app. Answer in Korean (해요체), "
        "short (1-3 sentences).",
        "- context.product is the product the user is looking at (fields in Korean `ko` "
        "and the shop's `original`). context.report compares it with the user's closest "
        "garment. context.closet lists the user's garments.",
        "- If context.product is set: answer only from context. If the answer is not in "
        "context, set found=false (do not guess thickness, washing, fit or anything else "
        "that is not written).",
        "- If context.product is null: the user is not on a product page. Answer general "
        "shopping questions (sizes, fabrics, care in general). If the question is about "
        "a specific product or comparing with the closet, set needs_product=true.",
        "- Never tell the user to buy or not to buy, never recommend buying, never "
        "mention probability or duplicate purchase. The user decides.",
        "- Do not invent numbers.",
    ]
)

PICK_SYSTEM = "\n".join(
    [
        "You pick 1 to 3 garments from the user's closet that go well with the product. "
        "Use only cloth_id values from the closet list. Base the reason only on the given "
        "attributes (category, color, name, material). Reason in Korean (해요체), one "
        "short sentence. Do not tell the user to buy or not to buy.",
    ]
)


class ClaudeClerk:
    def __init__(self, client=None):
        if client is None:
            import anthropic

            # 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다
            client = anthropic.Anthropic()
        self._client = client

    def _parse(self, system: str, messages: list[dict], output):
        response = self._client.messages.parse(
            model=MODEL,
            max_tokens=2000,
            system=system,
            output_config={"effort": "low"},
            messages=messages,
            output_format=output,
        )
        parsed = response.parsed_output
        if response.stop_reason != "end_turn" or parsed is None:
            raise RuntimeError(f"AI 점원이 답하지 못했어요 (stop_reason={response.stop_reason})")
        return parsed

    def answer(self, context: dict, history: list[dict], question: str) -> ClerkAnswer:
        intro = "<context>\n" + json.dumps(context, ensure_ascii=False) + "\n</context>"
        messages = [
            {"role": "user", "content": intro},
            {"role": "assistant", "content": "네, 이 정보로만 답할게요."},
            *history,
            {"role": "user", "content": question},
        ]
        return self._parse(ANSWER_SYSTEM, messages, ClerkAnswer)

    def pick(self, context: dict, closet: list[dict]) -> Picks:
        payload = json.dumps({"product": context["product"], "closet": closet}, ensure_ascii=False)
        return self._parse(PICK_SYSTEM, [{"role": "user", "content": payload}], Picks)
