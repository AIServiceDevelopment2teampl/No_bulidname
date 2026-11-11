"""compare/ai-clerk.md 완료 조건 [서버]: 상품 질문 답변 + 어울리는 내 옷 추천.

LLM(Claude)은 가짜. 서버가 LLM 답을 다시 검사한다:
- 추천: 내 옷장에 실제로 있는 옷만, 상품과 같은 종류는 빼고 (2026-11-11 결정), 최대 3벌
- 답변: 상품 정보에 없으면 '페이지에 그 정보가 없어요', 데이터에 없는 숫자·구매 권유는 뺀다
"""

import json
import sqlite3

import pytest

from conftest import make_image_bytes, upload
from server.ai.clerk_llm import ClerkAnswer, Pick, Picks

NO_INFO = "페이지에 그 정보가 없어요"


def field(ko, original=None):
    return {"original": original or ko, "ko": ko}


PRODUCT_FIELDS = {
    "name": field("울 블렌드 크루넥 카디건", "ウールブレンド クルーネック カーディガン"),
    "color": field("차콜", "チャコール"),
    "material": field("울 50%, 아크릴 50%", "ウール50% アクリル50%"),
    "fit": None,
    "size": field("총장 63cm", "着丈 63cm"),
    "care": field("손세탁 가능", "手洗い可"),
    "price": None,
}


def add_product(settings, user_id, category="top", url="https://zozo.jp/shop/x/goods/1/") -> int:
    with sqlite3.connect(settings.db_path) as db:
        cur = db.execute(
            "INSERT INTO products (user_id, source, url, category, fields, lang)"
            " VALUES (?, 'page', ?, ?, ?, 'ja')",
            (user_id, url, category, json.dumps(PRODUCT_FIELDS, ensure_ascii=False)),
        )
        return cur.lastrowid


def add_cloth(client, analyzer, category, color="black", seed=0, **edits) -> int:
    analyzer.category, analyzer.color = category, color
    response = upload(client, make_image_bytes(color=(seed, 40, 90)))
    assert response.status_code == 201, response.text
    cloth_id = response.json()["id"]
    if edits:
        assert client.patch(f"/v1/clothes/{cloth_id}", json=edits).status_code == 200
    return cloth_id


class FakeClerk:
    """정해 둔 답·추천을 돌려주고 LLM에 간 내용을 모두 기록한다"""

    def __init__(self):
        self.inputs: list = []
        self.reply = ClerkAnswer(answer="손세탁할 수 있어요.", found=True, needs_product=False)
        self.picks: list[int] = []
        self.fail = False

    def answer(self, context, history, question):
        self.inputs.append({"context": context, "history": history, "question": question})
        if self.fail:
            raise RuntimeError("LLM down")
        return self.reply

    def pick(self, context, closet):
        self.inputs.append({"context": context, "closet": closet})
        if self.fail:
            raise RuntimeError("LLM down")
        return Picks(items=[Pick(cloth_id=i, reason="색이 잘 어울려요") for i in self.picks])


@pytest.fixture
def clerk(app):
    fake = FakeClerk()
    app.state.test_clerk_llm = fake
    app.state.clerk_llm = fake
    return fake


@pytest.fixture
def me(as_user):
    return as_user("me")


def ask(client, text, product_id=None):
    response = client.post("/v1/clerk/messages", json={"product_id": product_id, "text": text})
    assert response.status_code == 201, response.text
    return response.json()["message"]


def history(client, product_id=None):
    params = {} if product_id is None else {"product_id": product_id}
    response = client.get("/v1/clerk/messages", params=params)
    assert response.status_code == 200, response.text
    return [(m["role"], m["content"]) for m in response.json()["items"]]


def matching(client, product_id):
    response = client.post(f"/v1/products/{product_id}/matching")
    assert response.status_code == 200, response.text
    return response.json()


# 추천


def test_recommended_ids_are_all_in_my_closet(client, analyzer, settings, as_user, clerk):
    other = as_user("someone-else")
    others_cloth = add_cloth(client, analyzer, "bottom", seed=1)
    me = as_user("me")
    pants = add_cloth(client, analyzer, "bottom", seed=2)
    product = add_product(settings, me, category="top")
    clerk.picks = [pants, 99999, others_cloth]

    body = matching(client, product)

    assert body["status"] == "ok"
    assert [i["cloth"]["id"] for i in body["items"]] == [pants]
    assert body["items"][0]["reason"] == "색이 잘 어울려요"
    assert other != me


def test_top_product_never_gets_top_recommendations(client, analyzer, settings, me, clerk):
    shirt = add_cloth(client, analyzer, "top", seed=1)
    pants = add_cloth(client, analyzer, "bottom", seed=2)
    coat = add_cloth(client, analyzer, "outer", seed=3)
    shoes = add_cloth(client, analyzer, "other", seed=4)
    product = add_product(settings, me, category="top")
    clerk.picks = [shirt, pants, coat, shoes]

    body = matching(client, product)

    ids = [i["cloth"]["id"] for i in body["items"]]
    assert shirt not in ids
    assert ids == [pants, coat, shoes]
    # LLM에도 상의 후보는 보내지 않는다
    assert shirt not in [c["id"] for c in clerk.inputs[-1]["closet"]]


def test_at_most_three_recommendations(client, analyzer, settings, me, clerk):
    ids = [add_cloth(client, analyzer, "bottom", seed=i) for i in range(5)]
    clerk.picks = ids

    assert len(matching(client, add_product(settings, me))["items"]) == 3


def test_empty_closet_gives_register_guide_without_llm(client, settings, me, clerk):
    body = matching(client, add_product(settings, me))

    assert body["status"] == "empty_closet"
    assert body["items"] == []
    assert body["message"] == "옷장에 옷을 등록하면 어울리는 옷을 골라 드려요"
    assert clerk.inputs == []


def test_only_same_category_clothes_counts_as_nothing_to_pair(
    client, analyzer, settings, me, clerk
):
    add_cloth(client, analyzer, "top", seed=1)

    body = matching(client, add_product(settings, me, category="top"))

    assert body["status"] == "none"
    assert body["message"] == "어울리는 옷을 찾지 못했어요"
    assert clerk.inputs == []


def test_all_picks_filtered_gives_not_found(client, analyzer, settings, me, clerk):
    add_cloth(client, analyzer, "bottom", seed=1)
    clerk.picks = [12345]

    body = matching(client, add_product(settings, me))

    assert body["status"] == "none"
    assert body["message"] == "어울리는 옷을 찾지 못했어요"


def test_matching_llm_failure_is_reported(client, analyzer, settings, me, clerk):
    add_cloth(client, analyzer, "bottom", seed=1)
    clerk.fail = True

    response = client.post(f"/v1/products/{add_product(settings, me)}/matching")

    assert response.status_code == 502


# 대화


def test_conversation_is_saved_per_product_and_reloaded(client, settings, me, clerk):
    cardigan = add_product(settings, me, url="https://zozo.jp/a/")
    coat = add_product(settings, me, url="https://zozo.jp/b/")

    ask(client, "세탁기 돌려도 돼?", cardigan)
    clerk.reply = ClerkAnswer(answer="총장은 63cm예요.", found=True, needs_product=False)
    ask(client, "길이는?", cardigan)

    assert history(client, cardigan) == [
        ("user", "세탁기 돌려도 돼?"),
        ("assistant", "손세탁할 수 있어요."),
        ("user", "길이는?"),
        ("assistant", "총장은 63cm예요."),
    ]
    assert history(client, coat) == []
    # 같은 페이지를 다시 열어 상품을 새로 정리해도 대화가 이어진다
    again = add_product(settings, me, url="https://zozo.jp/a/")
    assert len(history(client, again)) == 4
    # 이전 대화가 LLM에도 간다
    assert clerk.inputs[-1]["history"][0] == {"role": "user", "content": "세탁기 돌려도 돼?"}


def test_other_users_cannot_see_or_use_my_product(client, settings, as_user, clerk):
    me = as_user("me")
    product = add_product(settings, me)
    ask(client, "세탁기 돌려도 돼?", product)

    as_user("someone-else")

    assert client.get("/v1/clerk/messages", params={"product_id": product}).status_code == 404
    response = client.post("/v1/clerk/messages", json={"product_id": product, "text": "?"})
    assert response.status_code == 404


def test_llm_gets_only_product_info_and_closet_attributes(client, analyzer, settings, me, clerk):
    add_cloth(client, analyzer, "bottom", seed=1, name="청바지", material="면 100%")
    product = add_product(settings, me)
    clerk.picks = []

    ask(client, "이거 두꺼워?", product)
    matching(client, product)

    sent = json.dumps(clerk.inputs, ensure_ascii=False)
    assert "울 50%, 아크릴 50%" in sent  # 상품 정보
    assert "청바지" in sent and "면 100%" in sent  # 옷장 속성
    # 사용자·계정·파일 정보는 보내지 않는다
    for secret in (me, "default-user", "Bearer", "/v1/clothes", "products/", "image", "zozo.jp"):
        assert secret not in sent, secret
    assert set(clerk.inputs[0]["context"]) <= {"product", "report", "closet"}
    cloth = clerk.inputs[0]["context"]["closet"][0]
    assert set(cloth) <= {"id", "category", "color", "name", "brand", "material", "size"}


def test_question_not_in_product_info_gets_no_info_answer(client, settings, me, clerk):
    clerk.reply = ClerkAnswer(answer="두께감이 있어요.", found=False, needs_product=False)

    assert ask(client, "이거 두꺼워?", add_product(settings, me))["content"] == NO_INFO


def test_answer_with_numbers_not_in_data_is_replaced(client, settings, me, clerk):
    clerk.reply = ClerkAnswer(answer="총장은 70cm예요.", found=True, needs_product=False)

    assert ask(client, "길이는?", add_product(settings, me))["content"] == NO_INFO


def test_buy_or_dont_buy_sentences_are_removed(client, settings, me, clerk):
    clerk.reply = ClerkAnswer(
        answer="손세탁할 수 있어요. 이미 비슷한 옷이 있으니 사지 마세요.",
        found=True,
        needs_product=False,
    )

    assert ask(client, "세탁은?", add_product(settings, me))["content"] == "손세탁할 수 있어요."


def test_general_question_outside_product_page(client, clerk):
    clerk.reply = ClerkAnswer(
        answer="평소 입는 사이즈 표를 먼저 확인해 보세요.", found=True, needs_product=False
    )

    reply = ask(client, "해외 사이즈는 어떻게 골라?")

    assert reply["content"] == "평소 입는 사이즈 표를 먼저 확인해 보세요."
    assert clerk.inputs[-1]["context"]["product"] is None
    assert history(client) == [
        ("user", "해외 사이즈는 어떻게 골라?"),
        ("assistant", reply["content"]),
    ]


def test_comparison_question_outside_product_page_asks_to_open_product(client, clerk):
    clerk.reply = ClerkAnswer(answer="", found=False, needs_product=True)

    assert (
        ask(client, "이거랑 비슷한 옷 있어?")["content"]
        == "상품 비교는 상품 페이지에서 눌러 주세요"
    )


def test_llm_failure_saves_nothing(client, settings, me, clerk):
    product = add_product(settings, me)
    clerk.fail = True

    response = client.post("/v1/clerk/messages", json={"product_id": product, "text": "세탁은?"})

    assert response.status_code == 502
    assert response.json()["detail"] == "답하지 못했어요. 잠시 후 다시 시도해 주세요"
    assert history(client, product) == []


def test_only_recent_history_goes_to_llm(client, settings, me, clerk):
    product = add_product(settings, me)
    for i in range(8):
        ask(client, f"질문 {i}", product)

    assert len(clerk.inputs[-1]["history"]) == 10


def test_too_long_question_is_rejected(client, clerk):
    response = client.post("/v1/clerk/messages", json={"text": "가" * 501})

    assert response.status_code == 422


def test_too_many_messages_are_limited(client, settings, clerk):
    settings.clerk_requests_per_window = 2

    codes = [client.post("/v1/clerk/messages", json={"text": "안녕"}).status_code for _ in range(3)]

    assert codes == [201, 201, 429]


def test_clerk_needs_login(app, clerk):
    from fastapi.testclient import TestClient

    with TestClient(app) as anonymous:
        assert anonymous.post("/v1/clerk/messages", json={"text": "안녕"}).status_code == 401
        assert anonymous.get("/v1/clerk/messages").status_code == 401
