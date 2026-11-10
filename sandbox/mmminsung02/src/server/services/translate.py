"""페이지 번역 (translation/page-translation.md).

번역할 문장만 골라, 캐시에 없는 것만 번역기로 보낸다.
"""

import hashlib
import re
import sqlite3
from collections.abc import Callable

from server.ai.translator import Translator

TARGET = "ko"

# 글자(라틴·가나·한자 등)가 하나도 없으면 숫자·가격·기호라 번역하지 않는다
_LETTER = re.compile(r"[^\W\d_]")
_HANGUL = re.compile(r"[가-힣]")
_KANA_OR_LATIN = re.compile(r"[a-zA-Z぀-ヿ]")
# 상품 코드: 대문자·숫자·기호만 (SKU-1178-A)
_CODE = re.compile(r"^[A-Z0-9][A-Z0-9\-_./#]*\d[A-Z0-9\-_./#]*$")


class TranslationFailed(Exception):
    pass


def needs_translation(text: str) -> bool:
    stripped = text.strip()
    if not stripped or not _LETTER.search(stripped):
        return False
    if _CODE.match(stripped):
        return False
    # 한글이 있고 영어·가나가 없으면 이미 한국어
    if _HANGUL.search(stripped) and not _KANA_OR_LATIN.search(stripped):
        return False
    return True


def _key(text: str) -> str:
    return hashlib.sha256(f"{TARGET}\x00{text}".encode()).hexdigest()


def translate_texts(
    conn: sqlite3.Connection,
    translator: Translator,
    convert_units: Callable[[str], str],
    texts: list[str],
) -> list[str]:
    """입력과 같은 길이·순서. 번역이 필요 없는 문장은 그대로 둔다"""
    todo = list(dict.fromkeys(t for t in texts if needs_translation(t)))
    done: dict[str, str] = {}
    if todo:
        placeholders = ",".join("?" * len(todo))
        rows = conn.execute(
            f"SELECT key, text_ko FROM translation_cache WHERE key IN ({placeholders})",
            [_key(t) for t in todo],
        ).fetchall()
        cached = {row["key"]: row["text_ko"] for row in rows}
        missing = [t for t in todo if _key(t) not in cached]
        for t in todo:
            if _key(t) in cached:
                done[t] = cached[_key(t)]
        if missing:
            try:
                translated = translator.translate(missing)
            except Exception as e:
                raise TranslationFailed from e
            if len(translated) != len(missing):
                raise TranslationFailed("번역 개수가 달라요")
            # 단위 변환은 번역문에 붙인다 (예: 71.1 cm (28 in))
            translated = [convert_units(t) for t in translated]
            with conn:
                conn.executemany(
                    "INSERT OR REPLACE INTO translation_cache (key, src_lang, text_ko)"
                    " VALUES (?, NULL, ?)",
                    [(_key(src), ko) for src, ko in zip(missing, translated, strict=True)],
                )
            done.update(zip(missing, translated, strict=True))
    return [done.get(t, t) for t in texts]
