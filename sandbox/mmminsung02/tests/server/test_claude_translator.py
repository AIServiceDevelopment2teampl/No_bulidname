"""ClaudeTranslator가 Claude 응답을 다루는 방식 (네트워크 없이 가짜 클라이언트로).

실제 ASOS 페이지로 재 보다 찾은 문제: 40개를 보냈는데 41개로 답해 요청 전체가 실패했다.
이제 문장마다 번호(id)를 붙여 보내고 번호로 맞춘다.
빠진 번호만 한 번 더 묻고, 그래도 없으면 원문을 둔다.
"""

import json
from types import SimpleNamespace

from server.ai.translator import ClaudeTranslator, Translated, Translations


class FakeClient:
    """messages.parse를 흉내 낸다. answer(ids_and_texts) → [(id, ko), ...]"""

    def __init__(self, answer):
        self.answer = answer
        self.calls = []
        self.messages = SimpleNamespace(parse=self.parse)

    def parse(self, **kwargs):
        prompt = kwargs["messages"][0]["content"]
        items = json.loads(prompt[prompt.index("[") : prompt.rindex("]") + 1])
        self.calls.append(items)
        pairs = self.answer(items)
        parsed = Translations(translations=[Translated(id=i, ko=ko) for i, ko in pairs])
        return SimpleNamespace(stop_reason="end_turn", parsed_output=parsed)


def ko(items):
    return [(item["id"], "번역:" + item["text"]) for item in items]


def test_texts_with_newlines_and_numbers_stay_aligned():
    texts = ["Size guide\n1. Measure", "2. Compare", "Wool coat"]
    client = FakeClient(ko)

    out = ClaudeTranslator(client).translate(texts)

    assert out == ["번역:Size guide\n1. Measure", "번역:2. Compare", "번역:Wool coat"]


def test_extra_items_are_ignored():
    client = FakeClient(lambda items: ko(items) + [(999, "끼어든 문장")])

    out = ClaudeTranslator(client).translate(["Wool coat", "Free delivery"])

    assert out == ["번역:Wool coat", "번역:Free delivery"]


def test_answers_in_any_order_are_matched_by_id():
    client = FakeClient(lambda items: list(reversed(ko(items))))

    out = ClaudeTranslator(client).translate(["A coat", "A bag"])

    assert out == ["번역:A coat", "번역:A bag"]


def test_missing_items_are_asked_again_once():
    first = {"done": False}

    def answer(items):
        if not first["done"]:
            first["done"] = True
            return ko(items)[:-1]  # 마지막 하나를 빼먹는다
        return ko(items)

    client = FakeClient(answer)

    out = ClaudeTranslator(client).translate(["Wool coat", "Free delivery", "Notch lapels"])

    assert out == ["번역:Wool coat", "번역:Free delivery", "번역:Notch lapels"]
    assert [len(c) for c in client.calls] == [3, 1]


def test_still_missing_after_retry_keeps_original():
    client = FakeClient(lambda items: [p for p in ko(items) if "lapels" not in p[1]])

    out = ClaudeTranslator(client).translate(["Wool coat", "Notch lapels"])

    assert out == ["번역:Wool coat", "Notch lapels"]


def test_big_input_is_split_and_kept_in_order():
    texts = [f"Item {i}" for i in range(95)]
    client = FakeClient(ko)

    out = ClaudeTranslator(client).translate(texts)

    assert out == [f"번역:Item {i}" for i in range(95)]
    assert all(len(c) <= 40 for c in client.calls)
