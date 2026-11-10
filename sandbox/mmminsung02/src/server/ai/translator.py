"""쇼핑몰 페이지 문장 번역 (translation/page-translation.md).

번역 방식은 Claude로 전부 (2026-11-11 결정).
문장 배열을 넣으면 같은 길이·순서의 한국어 배열을 받는다.

문장마다 번호(id)를 붙여 JSON으로 보내고 번호로 맞춘다.
번호 목록 글자로 보냈더니 문장 안의 줄바꿈 때문에 40개를 41개로 답한 일이 있었다 (실제 ASOS 페이지).
빠진 번호는 한 번 더 묻고, 그래도 없으면 원문을 둔다.
"""

import json
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import Protocol

from pydantic import BaseModel

from server.ai.product_llm import MODEL

log = logging.getLogger(__name__)

BATCH = 40


class Translator(Protocol):
    def translate(self, texts: list[str]) -> list[str]:
        """같은 길이·순서로 돌려준다. 실패하면 예외"""
        ...


class Translated(BaseModel):
    id: int
    ko: str


class Translations(BaseModel):
    translations: list[Translated]


SYSTEM = "\n".join(
    [
        "You translate text pieces from an online clothing shop page into natural Korean "
        "for Korean shoppers.",
        "- The input is a JSON array of {id, text}. Return one {id, ko} for every input id.",
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
        if client is None:
            import anthropic

            # 키는 ANTHROPIC_API_KEY 환경변수에서 읽는다
            client = anthropic.Anthropic()
        self._client = client

    def translate(self, texts: list[str]) -> list[str]:
        if not texts:
            return []
        batches = [texts[start : start + BATCH] for start in range(0, len(texts), BATCH)]
        if len(batches) == 1:
            return self._batch(batches[0])
        # 나눈 묶음을 동시에 번역한다 (한 묶음 약 2초, 차례로 하면 묶음 수만큼 늘어난다)
        with ThreadPoolExecutor(max_workers=min(4, len(batches))) as pool:
            results = list(pool.map(self._batch, batches))
        return [t for batch in results for t in batch]

    def _batch(self, texts: list[str]) -> list[str]:
        found = self._ask(dict(enumerate(texts)))
        missing = {i: t for i, t in enumerate(texts) if i not in found}
        if missing:
            found.update(self._ask(missing))
        still = [i for i in range(len(texts)) if i not in found]
        if still:
            log.warning("번역에서 빠진 문장 %d개는 원문으로 둡니다", len(still))
        return [found.get(i, t) for i, t in enumerate(texts)]

    def _ask(self, items: dict[int, str]) -> dict[int, str]:
        payload = json.dumps([{"id": i, "text": t} for i, t in items.items()], ensure_ascii=False)
        response = self._client.messages.parse(
            model=MODEL,
            max_tokens=8000,
            system=SYSTEM,
            output_config={"effort": "low"},
            messages=[
                {"role": "user", "content": f"Translate these {len(items)} items:\n{payload}"}
            ],
            output_format=Translations,
        )
        parsed = response.parsed_output
        if response.stop_reason != "end_turn" or parsed is None:
            raise RuntimeError(f"번역하지 못했어요 (stop_reason={response.stop_reason})")
        # 모르는 번호(끼어든 문장)는 버린다
        return {t.id: t.ko for t in parsed.translations if t.id in items}
