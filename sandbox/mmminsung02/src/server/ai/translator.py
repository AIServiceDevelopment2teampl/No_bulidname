"""쇼핑몰 페이지 문장 번역 (translation/page-translation.md).

번역 방식은 Claude로 전부 (2026-11-11 결정).
문장 배열을 넣으면 같은 길이·순서의 한국어 배열을 받는다.
"""

from typing import Protocol

from pydantic import BaseModel

from server.ai.product_llm import MODEL

BATCH = 40


class Translator(Protocol):
    def translate(self, texts: list[str]) -> list[str]:
        """같은 길이·순서로 돌려준다. 실패하면 예외"""
        ...


class Translations(BaseModel):
    translations: list[str]


SYSTEM = "\n".join(
    [
        "You translate text pieces from an online clothing shop page into natural Korean "
        "for Korean shoppers.",
        "- Return exactly one translation per input item, in the same order.",
        "- Translate every item fully into Korean, including fabric and color words "
        "(Polyester → 폴리에스터, Wool → 울).",
        "- Keep brand names, model numbers, sizes (S, M, UK 8), numbers, prices and units "
        "as written.",
        "- Use common Korean shopping terms "
        "(Add to bag → 장바구니에 담기, Regular fit → 레귤러 핏).",
        "- Do not add explanations.",
    ]
)


class ClaudeTranslator:
    def __init__(self, client=None):
        import anthropic

        # 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다
        self._client = client or anthropic.Anthropic()

    def translate(self, texts: list[str]) -> list[str]:
        out: list[str] = []
        for start in range(0, len(texts), BATCH):
            out.extend(self._batch(texts[start : start + BATCH]))
        return out

    def _batch(self, texts: list[str]) -> list[str]:
        numbered = "\n".join(f"{i + 1}. {t}" for i, t in enumerate(texts))
        prompt = f"Translate these {len(texts)} items:\n<items>\n{numbered}\n</items>"
        response = self._client.messages.parse(
            model=MODEL,
            max_tokens=8000,
            system=SYSTEM,
            output_config={"effort": "low"},
            messages=[{"role": "user", "content": prompt}],
            output_format=Translations,
        )
        parsed = response.parsed_output
        if response.stop_reason != "end_turn" or parsed is None:
            raise RuntimeError(f"번역하지 못했어요 (stop_reason={response.stop_reason})")
        if len(parsed.translations) != len(texts):
            raise RuntimeError(f"번역 개수가 달라요: {len(parsed.translations)} != {len(texts)}")
        return parsed.translations
