"""wardrobe/browse.md 완료 조건 [서버]"""

import sqlite3

from conftest import add_cloth, make_image_bytes, upload


def count(settings, sql, *args) -> int:
    with sqlite3.connect(settings.db_path) as db:
        return db.execute(sql, args).fetchone()[0]


# 목록·필터


def test_filter_results_only_contain_matching_clothes(client, analyzer):
    add_cloth(client, analyzer, "top", "black", seed=1)
    add_cloth(client, analyzer, "top", "white", seed=2)
    add_cloth(client, analyzer, "bottom", "black", seed=3)
    add_cloth(client, analyzer, "outer", "navy", seed=4)

    by_category = client.get("/v1/clothes", params={"category": "top"}).json()["items"]
    by_color = client.get("/v1/clothes", params={"color": "black"}).json()["items"]
    both = client.get("/v1/clothes", params={"category": "top", "color": "black"}).json()["items"]

    assert len(by_category) == 2 and all(c["category"] == "top" for c in by_category)
    assert len(by_color) == 2 and all(c["color"] == "black" for c in by_color)
    assert len(both) == 1 and both[0]["category"] == "top" and both[0]["color"] == "black"


def test_list_without_filter_returns_all_newest_first_with_total(client, analyzer):
    first = add_cloth(client, analyzer, seed=1)
    second = add_cloth(client, analyzer, seed=2)

    body = client.get("/v1/clothes").json()

    assert body["total"] == 2
    assert [c["id"] for c in body["items"]] == [second, first]


def test_list_item_has_display_fields(client, analyzer):
    add_cloth(client, analyzer, "other", "gray", seed=1)

    item = client.get("/v1/clothes").json()["items"][0]

    assert item["image_url"] == f"/v1/clothes/{item['id']}/image"
    assert item["confirmed"] is False
    assert item["needs_category"] is True
    assert item["needs_photo"] is False
    for key in ("name", "brand", "material", "size"):
        assert item[key] is None


def test_empty_closet_returns_empty_list(client):
    body = client.get("/v1/clothes").json()

    assert body == {"items": [], "total": 0}


def test_unknown_filter_value_returns_400(client):
    assert client.get("/v1/clothes", params={"category": "shoes"}).status_code == 400
    assert client.get("/v1/clothes", params={"color": "rainbow"}).status_code == 400


def test_paging_with_limit_and_offset(client, analyzer):
    ids = [add_cloth(client, analyzer, seed=i) for i in range(5)]

    page = client.get("/v1/clothes", params={"limit": 2, "offset": 2}).json()

    assert page["total"] == 5
    assert [c["id"] for c in page["items"]] == [ids[2], ids[1]]


def test_image_is_served(client, analyzer):
    data = make_image_bytes(color=(9, 9, 9))
    cloth_id = upload(client, data).json()["id"]

    response = client.get(f"/v1/clothes/{cloth_id}/image")

    assert response.status_code == 200
    assert response.content == data
    assert response.headers["content-type"] == "image/png"


# 수정


def test_update_changes_values_and_confirms(client, analyzer):
    cloth_id = add_cloth(client, analyzer, "top", "black", seed=1)

    response = client.patch(
        f"/v1/clothes/{cloth_id}",
        json={"category": "outer", "color": "navy", "name": "울 코트", "brand": "UNIQLO"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["category"] == "outer"
    assert body["color"] == "navy"
    assert body["name"] == "울 코트"
    assert body["brand"] == "UNIQLO"
    assert body["confirmed"] is True
    assert client.get(f"/v1/clothes/{cloth_id}").json() == body


def test_update_with_no_changes_still_confirms(client, analyzer):
    cloth_id = add_cloth(client, analyzer, seed=1)

    body = client.patch(f"/v1/clothes/{cloth_id}", json={}).json()

    assert body["confirmed"] is True


def test_empty_text_clears_field(client, analyzer):
    cloth_id = add_cloth(client, analyzer, seed=1)
    client.patch(f"/v1/clothes/{cloth_id}", json={"material": "면 100%"})

    body = client.patch(f"/v1/clothes/{cloth_id}", json={"material": ""}).json()

    assert body["material"] is None


def test_update_with_invalid_category_returns_400(client, analyzer):
    cloth_id = add_cloth(client, analyzer, seed=1)

    response = client.patch(f"/v1/clothes/{cloth_id}", json={"category": "shoes"})

    assert response.status_code == 400
    assert client.get(f"/v1/clothes/{cloth_id}").json()["category"] == "top"


def test_update_with_invalid_color_returns_400(client, analyzer):
    cloth_id = add_cloth(client, analyzer, seed=1)

    assert client.patch(f"/v1/clothes/{cloth_id}", json={"color": "rainbow"}).status_code == 400


# 삭제


def test_delete_removes_row_image_and_embedding(client, analyzer, settings):
    cloth_id = add_cloth(client, analyzer, seed=1)
    image_path = settings.data_dir / count_path(settings, cloth_id)

    response = client.delete(f"/v1/clothes/{cloth_id}")

    assert response.status_code == 204
    assert count(settings, "SELECT COUNT(*) FROM clothes WHERE id = ?", cloth_id) == 0
    assert (
        count(settings, "SELECT COUNT(*) FROM clothes_embeddings WHERE cloth_id = ?", cloth_id) == 0
    )
    assert not image_path.exists()
    assert client.get(f"/v1/clothes/{cloth_id}").status_code == 404


def count_path(settings, cloth_id) -> str:
    with sqlite3.connect(settings.db_path) as db:
        return db.execute("SELECT image_path FROM clothes WHERE id = ?", (cloth_id,)).fetchone()[0]


def test_deleted_photo_can_be_registered_again(client):
    data = make_image_bytes(color=(5, 5, 5))
    cloth_id = upload(client, data).json()["id"]
    client.delete(f"/v1/clothes/{cloth_id}")

    again = upload(client, data)

    assert again.status_code == 201
    assert again.json()["id"] != cloth_id


def test_delete_all_my_data_leaves_nothing(client, analyzer, settings, as_user):
    me = as_user("me")
    add_cloth(client, analyzer, seed=1)
    add_cloth(client, analyzer, seed=2)
    with sqlite3.connect(settings.db_path) as db:
        db.execute(
            "INSERT INTO products (user_id, source, fields, lang) VALUES (?, 'page', '{}', 'en')",
            (me,),
        )
        product_id = db.execute("SELECT id FROM products").fetchone()[0]
        db.execute(
            "INSERT INTO reports (product_id, similar, body) VALUES (?, '[]', '{}')", (product_id,)
        )
        db.execute(
            "INSERT INTO clerk_messages (user_id, role, content) VALUES (?, 'user', 'hi')", (me,)
        )
        db.execute(
            "INSERT INTO imports (user_id, type, status, candidates)"
            " VALUES (?, 'receipt', 'pending', '[]')",
            (me,),
        )

    response = client.delete("/v1/me/data")

    assert response.status_code == 204
    for table in ("clothes", "products", "clerk_messages", "imports"):
        assert count(settings, f"SELECT COUNT(*) FROM {table} WHERE user_id = ?", me) == 0
    assert count(settings, "SELECT COUNT(*) FROM reports") == 0
    assert count(settings, "SELECT COUNT(*) FROM clothes_embeddings") == 0
    assert not (settings.image_dir / me).exists()


def test_delete_all_keeps_other_users_data(client, analyzer, settings, as_user):
    as_user("other")
    other_cloth = add_cloth(client, analyzer, seed=1)
    as_user("me")
    add_cloth(client, analyzer, seed=2)

    client.delete("/v1/me/data")

    assert count(settings, "SELECT COUNT(*) FROM clothes WHERE id = ?", other_cloth) == 1


# 다른 사용자


def test_other_users_cloth_returns_404(client, analyzer, as_user):
    as_user("owner")
    cloth_id = add_cloth(client, analyzer, seed=1)
    as_user("stranger")

    assert client.get(f"/v1/clothes/{cloth_id}").status_code == 404
    assert client.get(f"/v1/clothes/{cloth_id}/image").status_code == 404
    assert client.patch(f"/v1/clothes/{cloth_id}", json={"color": "red"}).status_code == 404
    assert client.delete(f"/v1/clothes/{cloth_id}").status_code == 404
    assert client.get("/v1/clothes").json()["total"] == 0

    as_user("owner")
    assert client.get(f"/v1/clothes/{cloth_id}").json()["color"] == "black"


def test_missing_cloth_returns_404(client):
    assert client.get("/v1/clothes/9999").status_code == 404
