"""shopping/shop-browser.md 완료 조건 [서버]"""

import json

import pytest
from fastapi.testclient import TestClient

from server.config import Settings
from server.main import create_app

SHOPS_FILE = Settings().shops_file


def test_shops_returns_config_file_as_is(client):
    expected = json.loads(SHOPS_FILE.read_text(encoding="utf-8"))["shops"]

    response = client.get("/v1/shops")

    assert response.status_code == 200
    assert response.json() == {"shops": expected}


def test_every_shipping_tag_is_one_of_three(client):
    shops = client.get("/v1/shops").json()["shops"]

    assert shops
    assert {s["shipping"] for s in shops} <= {"direct", "forwarding", "unknown"}


def test_config_file_is_valid():
    shops = json.loads(SHOPS_FILE.read_text(encoding="utf-8"))["shops"]

    ids = [s["id"] for s in shops]
    assert len(ids) == len(set(ids))
    for s in shops:
        assert s["url"].startswith("https://"), s
        assert s["region"] in ("domestic", "overseas"), s
        assert s["lang"] in ("ko", "en", "ja"), s
        assert isinstance(s["korean_page"], bool), s


def test_shops_need_no_login(app):
    with TestClient(app) as anon:
        assert anon.get("/v1/shops").status_code == 200


def test_changing_the_file_changes_the_list_without_restart(tmp_path, analyzer):
    shops_file = tmp_path / "shops.json"
    shops_file.write_text(json.dumps({"shops": []}), encoding="utf-8")
    app = create_app(settings=Settings(data_dir=tmp_path, shops_file=shops_file), analyzer=analyzer)

    with TestClient(app) as c:
        assert c.get("/v1/shops").json() == {"shops": []}
        one = {
            "id": "x",
            "name": "X",
            "url": "https://x.example/",
            "region": "overseas",
            "lang": "en",
            "korean_page": False,
            "shipping": "unknown",
        }
        shops_file.write_text(json.dumps({"shops": [one]}), encoding="utf-8")

        assert c.get("/v1/shops").json() == {"shops": [one]}


@pytest.mark.parametrize(
    "content", ["not json", json.dumps({"shops": [{"id": "x", "shipping": "boat"}]})]
)
def test_broken_config_returns_503(tmp_path, analyzer, content):
    shops_file = tmp_path / "shops.json"
    shops_file.write_text(content, encoding="utf-8")
    app = create_app(settings=Settings(data_dir=tmp_path, shops_file=shops_file), analyzer=analyzer)

    with TestClient(app) as c:
        assert c.get("/v1/shops").status_code == 503
