"""compare/similar-search.md 완료 조건 [서버]

상품 정보 저장(기능 7)이 아직 없어서 상품은 DB와 파일에 직접 넣는다.
가짜 분석기는 사진 색으로 임베딩을 만든다 (conftest.color_embedding).
"""

import sqlite3

import numpy as np
import pytest

from conftest import color_embedding, make_image_bytes, upload

BLACK = (0, 0, 0)
NEAR_BLACK = (20, 20, 20)  # 검정과 매우 비슷
DARK_GRAY = (70, 70, 70)  # 비슷
WHITE = (255, 255, 255)  # 다르다


def add_cloth(client, analyzer, category, rgb) -> int:
    analyzer.category = category
    response = upload(client, make_image_bytes(color=rgb))
    assert response.status_code == 201, response.text
    return response.json()["id"]


def add_product(settings, user_id, rgb=BLACK, category="top", with_image=True) -> int:
    image_path = None
    if with_image:
        image_path = f"products/{user_id}/{rgb[0]}-{rgb[1]}-{rgb[2]}.png"
        file = settings.data_dir / image_path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(make_image_bytes(color=rgb))
    with sqlite3.connect(settings.db_path) as db:
        cur = db.execute(
            "INSERT INTO products (user_id, source, image_path, category, fields, lang)"
            " VALUES (?, 'page', ?, ?, '{}', 'en')",
            (user_id, image_path, category),
        )
        return cur.lastrowid


def similar(client, product_id):
    return client.post(f"/v1/products/{product_id}/similar")


@pytest.fixture
def me(as_user):
    return as_user("me")


def test_same_image_in_closet_comes_first(client, analyzer, settings, me):
    add_cloth(client, analyzer, "top", WHITE)
    same = add_cloth(client, analyzer, "top", BLACK)
    add_cloth(client, analyzer, "top", DARK_GRAY)

    body = similar(client, add_product(settings, me, BLACK)).json()

    assert body["status"] == "ok"
    assert body["items"][0]["cloth"]["id"] == same
    assert body["items"][0]["image_similarity"] == pytest.approx(1.0, abs=1e-4)


def test_at_most_three_results_in_descending_order(client, analyzer, settings, me):
    for rgb in [BLACK, NEAR_BLACK, (35, 35, 35), (45, 45, 45), (55, 55, 55)]:
        add_cloth(client, analyzer, "top", rgb)

    items = similar(client, add_product(settings, me, BLACK)).json()["items"]

    scores = [i["image_similarity"] for i in items]
    assert len(items) == 3
    assert scores == sorted(scores, reverse=True)


def test_top_product_only_matches_tops(client, analyzer, settings, me):
    top = add_cloth(client, analyzer, "top", NEAR_BLACK)
    add_cloth(client, analyzer, "bottom", BLACK)
    add_cloth(client, analyzer, "outer", (5, 5, 5))  # 같은 사진은 중복 등록이 안 돼서 살짝 다른 색

    items = similar(client, add_product(settings, me, BLACK, category="top")).json()["items"]

    assert [i["cloth"]["id"] for i in items] == [top]
    assert all(i["cloth"]["category"] == "top" for i in items)


def test_unknown_category_searches_whole_closet(client, analyzer, settings, me):
    bottom = add_cloth(client, analyzer, "bottom", BLACK)
    add_cloth(client, analyzer, "top", NEAR_BLACK)

    items = similar(client, add_product(settings, me, BLACK, category=None)).json()["items"]

    assert items[0]["cloth"]["id"] == bottom
    assert {i["cloth"]["category"] for i in items} == {"bottom", "top"}


def test_fewer_than_three_returns_what_exists(client, analyzer, settings, me):
    only = add_cloth(client, analyzer, "outer", BLACK)

    items = similar(client, add_product(settings, me, BLACK, category="outer")).json()["items"]

    assert [i["cloth"]["id"] for i in items] == [only]


def test_empty_closet_returns_empty_closet(client, settings, me):
    body = similar(client, add_product(settings, me, BLACK)).json()

    assert body == {"status": "empty_closet", "items": []}


def test_no_clothes_of_that_category_is_empty_closet(client, analyzer, settings, me):
    add_cloth(client, analyzer, "bottom", BLACK)

    body = similar(client, add_product(settings, me, BLACK, category="top")).json()

    assert body["status"] == "empty_closet"


def test_other_users_clothes_are_not_in_results(client, analyzer, settings, as_user):
    as_user("other")
    add_cloth(client, analyzer, "top", BLACK)
    me = as_user("me")
    mine = add_cloth(client, analyzer, "top", DARK_GRAY)

    items = similar(client, add_product(settings, me, BLACK)).json()["items"]

    assert [i["cloth"]["id"] for i in items] == [mine]


def test_clothes_without_photo_are_not_in_results(client, analyzer, settings, me):
    with_photo = add_cloth(client, analyzer, "top", DARK_GRAY)
    with sqlite3.connect(settings.db_path) as db:
        db.execute(
            "INSERT INTO clothes (user_id, category, color, source)"
            " VALUES (?, 'top', 'black', 'receipt')",
            (me,),
        )

    items = similar(client, add_product(settings, me, BLACK)).json()["items"]

    assert [i["cloth"]["id"] for i in items] == [with_photo]


def test_embeddings_from_another_model_are_skipped(client, analyzer, settings, me):
    cloth = add_cloth(client, analyzer, "top", BLACK)
    with sqlite3.connect(settings.db_path) as db:
        db.execute("UPDATE clothes_embeddings SET model = 'old-model' WHERE cloth_id = ?", (cloth,))

    body = similar(client, add_product(settings, me, BLACK)).json()

    assert body["status"] == "empty_closet"


def test_response_never_says_probability(client, analyzer, settings, me):
    add_cloth(client, analyzer, "top", BLACK)
    add_cloth(client, analyzer, "top", WHITE)

    text = similar(client, add_product(settings, me, BLACK)).text

    assert "확률" not in text
    assert "probability" not in text.lower()


# 단계 (매우 비슷 / 비슷) 와 no_similar. 기준 점수는 초안이라 Settings 값을 기준으로 확인한다


def test_levels_follow_threshold_settings(client, analyzer, settings, me):
    near = add_cloth(client, analyzer, "top", NEAR_BLACK)
    gray = add_cloth(client, analyzer, "top", DARK_GRAY)

    items = similar(client, add_product(settings, me, BLACK)).json()["items"]
    by_id = {i["cloth"]["id"]: i for i in items}

    for cloth_id in (near, gray):
        score = by_id[cloth_id]["image_similarity"]
        expected = "very_similar" if score >= settings.very_similar_score else "similar"
        assert score >= settings.similar_score
        assert by_id[cloth_id]["level"] == expected
    assert by_id[near]["level"] == "very_similar"


def test_all_below_threshold_is_no_similar(client, analyzer, settings, me):
    add_cloth(client, analyzer, "top", WHITE)
    black, white = color_embedding(BLACK), color_embedding(WHITE)
    assert float(np.dot(black, white)) < settings.similar_score

    body = similar(client, add_product(settings, me, BLACK)).json()

    assert body == {"status": "no_similar", "items": []}


# 예외


def test_product_without_image_returns_400(client, analyzer, settings, me):
    add_cloth(client, analyzer, "top", BLACK)

    response = similar(client, add_product(settings, me, with_image=False))

    assert response.status_code == 400
    assert response.json()["detail"] == "상품 사진을 찾지 못했어요"


def test_other_users_product_returns_404(client, settings, as_user):
    owner = as_user("owner")
    product = add_product(settings, owner, BLACK)
    as_user("stranger")

    assert similar(client, product).status_code == 404
