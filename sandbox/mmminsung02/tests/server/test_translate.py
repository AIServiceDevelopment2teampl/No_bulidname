"""translation/page-translation.md 완료 조건 [서버].

번역기(LLM)는 가짜, 단위 변환은 기능 5의 실제 함수.
"""

import pytest
from fastapi.testclient import TestClient


class FakeTranslator:
    """문장 앞에 [ko]를 붙여 '번역'한다. 호출 횟수와 받은 문장을 기록한다"""

    def __init__(self):
        self.calls: list[list[str]] = []
        self.fail = False
        self.wrong_length = False

    def translate(self, texts: list[str]) -> list[str]:
        self.calls.append(list(texts))
        if self.fail:
            raise RuntimeError("LLM down")
        out = [f"[ko] {t}" for t in texts]
        return out[:-1] if self.wrong_length else out


@pytest.fixture
def translator(app):
    fake = FakeTranslator()
    # 앱이 이미 켜졌든 아니든 가짜가 쓰이게 둘 다 넣는다
    app.state.test_translator = fake
    app.state.translator = fake
    return fake


def translate(client, texts, **extra):
    response = client.post("/v1/translate", json={"texts": texts, **extra})
    assert response.status_code == 200, response.text
    return response.json()["translations"]


# 완료 조건


def test_returns_same_length_and_order(client, translator):
    texts = ["Wool coat", "Free delivery", "Notch lapels"]

    out = translate(client, texts)

    assert out == ["[ko] Wool coat", "[ko] Free delivery", "[ko] Notch lapels"]


def test_second_time_uses_cache_and_calls_llm_zero_times(client, translator):
    translate(client, ["Wool coat", "Free delivery"])
    calls_after_first = len(translator.calls)

    out = translate(client, ["Free delivery", "Wool coat"])

    assert out == ["[ko] Free delivery", "[ko] Wool coat"]
    assert len(translator.calls) == calls_after_first


def test_only_new_sentences_go_to_llm(client, translator):
    translate(client, ["Wool coat"])

    translate(client, ["Wool coat", "Machine wash"])

    assert translator.calls[-1] == ["Machine wash"]


def test_units_are_converted_next_to_translation(client, translator):
    out = translate(client, ["Length: 28 in, Chest: 21 in"])

    assert "71.1 cm (28 in)" in out[0]
    assert "53.3 cm (21 in)" in out[0]


# 번역하지 않는 글자


@pytest.mark.parametrize("text", ["$129.00", "¥8,800", "12345", "SKU-1178-A", "  ", "★★★", "50%"])
def test_numbers_prices_and_codes_are_not_sent(client, translator, text):
    out = translate(client, [text])

    assert out == [text]
    assert translator.calls == []


def test_korean_sentences_are_not_sent(client, translator):
    out = translate(client, ["무료 배송", "Wool coat"])

    assert out == ["무료 배송", "[ko] Wool coat"]
    assert translator.calls == [["Wool coat"]]


def test_duplicates_in_one_request_are_sent_once(client, translator):
    out = translate(client, ["Add to bag", "Add to bag", "Add to bag"])

    assert out == ["[ko] Add to bag"] * 3
    assert translator.calls == [["Add to bag"]]


def test_japanese_is_translated(client, translator):
    out = translate(client, ["手洗い可"])

    assert out == ["[ko] 手洗い可"]


# 오류


def test_llm_failure_returns_502_and_caches_nothing(client, translator):
    translator.fail = True
    response = client.post("/v1/translate", json={"texts": ["Wool coat"]})
    assert response.status_code == 502
    assert response.json()["detail"] == "번역하지 못했어요"

    translator.fail = False
    assert translate(client, ["Wool coat"]) == ["[ko] Wool coat"]


def test_wrong_length_from_llm_is_an_error_not_a_misaligned_page(client, translator):
    translator.wrong_length = True

    response = client.post("/v1/translate", json={"texts": ["Wool coat", "Free delivery"]})

    assert response.status_code == 502


def test_too_many_or_too_long_texts_are_rejected(client, translator):
    assert client.post("/v1/translate", json={"texts": ["a"] * 201}).status_code == 422
    assert client.post("/v1/translate", json={"texts": ["a" * 2001]}).status_code == 422


def test_empty_list_returns_empty(client, translator):
    assert translate(client, []) == []


def test_translate_needs_login(app, translator):
    with TestClient(app) as anon:
        assert anon.post("/v1/translate", json={"texts": ["Wool coat"]}).status_code == 401


def test_without_translator_returns_503(app, client):
    app.state.translator = None

    response = client.post("/v1/translate", json={"texts": ["Wool coat"]})

    assert response.status_code == 503


def test_cache_is_shared_between_users(client, translator, as_user):
    """번역 결과는 개인 정보가 아니라 사용자끼리 함께 쓴다 (비용·속도)"""
    as_user("a")
    translate(client, ["Wool coat"])
    as_user("b")

    translate(client, ["Wool coat"])

    assert len(translator.calls) == 1
