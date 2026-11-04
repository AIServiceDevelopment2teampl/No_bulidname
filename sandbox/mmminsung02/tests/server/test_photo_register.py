"""wardrobe/photo-register.md 완료 조건 [서버]"""

import sqlite3

import numpy as np

from conftest import make_image_bytes
from server.domain import CATEGORIES, COLORS


def upload(client, data: bytes, name="shirt.png", mime="image/png"):
    return client.post("/v1/clothes", files={"image": (name, data, mime)})


def test_upload_creates_cloth_with_category_and_color_from_fixed_lists(client):
    response = upload(client, make_image_bytes())

    assert response.status_code == 201
    body = response.json()
    assert isinstance(body["id"], int)
    assert body["category"] in CATEGORIES
    assert body["color"] in COLORS
    assert body["source"] == "photo"


def test_embedding_is_stored_with_model_output_dimension(client, settings, analyzer):
    cloth_id = upload(client, make_image_bytes()).json()["id"]

    with sqlite3.connect(settings.db_path) as db:
        model, blob = db.execute(
            "SELECT model, vector FROM clothes_embeddings WHERE cloth_id = ?", (cloth_id,)
        ).fetchone()
    vector = np.frombuffer(blob, dtype=np.float32)
    assert model == analyzer.model_name
    assert vector.shape == (analyzer.dim,)


def test_non_image_file_returns_400_with_message(client):
    response = upload(client, b"this is not an image", name="note.txt", mime="text/plain")

    assert response.status_code == 400
    assert response.json()["detail"] == "이미지를 읽을 수 없어요"


def test_broken_image_returns_400(client):
    broken = make_image_bytes()[:20]

    response = upload(client, broken)

    assert response.status_code == 400


def test_same_image_twice_is_not_saved_again_and_returns_existing_id(client, settings):
    data = make_image_bytes(color=(10, 20, 30))
    first = upload(client, data)

    second = upload(client, data)

    assert second.status_code == 200
    assert second.json()["duplicate"] is True
    assert second.json()["id"] == first.json()["id"]
    assert second.json()["message"] == "이미 등록된 사진이에요"
    with sqlite3.connect(settings.db_path) as db:
        assert db.execute("SELECT COUNT(*) FROM clothes").fetchone()[0] == 1
        assert db.execute("SELECT COUNT(*) FROM clothes_embeddings").fetchone()[0] == 1


def test_new_cloth_is_not_confirmed(client):
    body = upload(client, make_image_bytes()).json()

    assert body["confirmed"] is False


# 예외 상황


def test_too_large_file_returns_413(client, settings):
    big = b"\x00" * (settings.max_upload_bytes + 1)

    response = upload(client, big)

    assert response.status_code == 413


def test_low_confidence_category_becomes_other_and_asks_user(client, analyzer):
    analyzer.category = "outer"
    analyzer.category_confidence = 0.2

    body = upload(client, make_image_bytes()).json()

    assert body["category"] == "other"
    assert body["needs_category"] is True


def test_jpeg_and_webp_are_accepted(client):
    jpg = upload(client, make_image_bytes(fmt="JPEG"), name="a.jpg", mime="image/jpeg")
    webp = upload(
        client, make_image_bytes(color=(1, 2, 3), fmt="WEBP"), name="a.webp", mime="image/webp"
    )

    assert jpg.status_code == 201
    assert webp.status_code == 201


def test_image_file_is_saved_to_disk(client, settings):
    body = upload(client, make_image_bytes()).json()

    with sqlite3.connect(settings.db_path) as db:
        (image_path,) = db.execute(
            "SELECT image_path FROM clothes WHERE id = ?", (body["id"],)
        ).fetchone()
    assert (settings.data_dir / image_path).is_file()
