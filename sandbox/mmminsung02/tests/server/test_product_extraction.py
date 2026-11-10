"""translation/product-extraction.md 완료 조건 [서버]

LLM은 가짜(FakeProductLLM)로 바꾸고, 이미지 받기도 가짜로 바꾼다.
단위 변환은 기능 5의 실제 함수를 쓴다.
"""

import json
import sqlite3

import pytest

from conftest import make_image_bytes
from server.ai.product_llm import ExtractedProduct, FieldValue

ZOZO_JSON_LD = {
    "@context": "https://schema.org",
    "@type": "Product",
    "name": "ウールブレンド クルーネック カーディガン",
    "image": ["https://img.zozo.jp/goods/7781/1.jpg", "https://img.zozo.jp/goods/7781/2.jpg"],
    "brand": {"@type": "Brand", "name": "BEAMS"},
    "color": "チャコール",
    "description": "【Material】表地：ウール50% アクリル50%\n手洗い可",
    "offers": {"@type": "Offer", "price": 8800, "priceCurrency": "JPY"},
}

PAGE_TEXT = (
    "BEAMS ウールブレンド クルーネック カーディガン ¥8,800 税込 "
    "カラー チャコール アイボリー ネイビー サイズ M L "
    "Length: 25 in, Chest: 21 in 素材 ウール50% アクリル50% 手洗い可"
)


def page(**overrides):
    body = {
        "url": "https://zozo.jp/shop/beams/goods/7781/",
        "title": "ウールブレンド クルーネック カーディガン | BEAMS - ZOZOTOWN",
        "lang": "ja",
        "json_ld": [ZOZO_JSON_LD],
        "og": {"og:image": "https://img.zozo.jp/goods/7781/og.jpg"},
        "text": PAGE_TEXT,
        "image_url": None,
    }
    body.update(overrides)
    return body


def fv(original, ko):
    return FieldValue(original=original, ko=ko)


class FakeProductLLM:
    """정해 둔 결과를 돌려준다. raises를 주면 실패를 흉내 낸다"""

    def __init__(self, result=None, raises=None):
        self.result = result
        self.raises = raises
        self.calls = []

    def extract(self, page_text):
        self.calls.append(page_text)
        if self.raises:
            raise self.raises
        return self.result


GOOD = ExtractedProduct(
    lang="ja",
    name=fv("ウールブレンド クルーネック カーディガン", "울 블렌드 크루넥 카디건"),
    category="top",
    category_evidence="カーディガン",
    color=fv("チャコール", "차콜"),
    material=fv("ウール50% アクリル50%", "울 50%, 아크릴 50%"),
    fit=None,
    size=fv("Length: 25 in, Chest: 21 in", "총장: 25 in, 가슴너비: 21 in"),
    care=fv("手洗い可", "손세탁 가능"),
)


@pytest.fixture
def images():
    """이미지 주소 → 내려받을 바이트. 없는 주소는 None (받지 못함)"""
    store = {
        "https://img.zozo.jp/goods/7781/1.jpg": make_image_bytes(color=(40, 40, 40), fmt="JPEG"),
    }
    fetched = []

    def fetch(url):
        fetched.append(url)
        return store.get(url)

    fetch.store = store
    fetch.fetched = fetched
    return fetch


@pytest.fixture
def llm():
    return FakeProductLLM(GOOD)


@pytest.fixture
def wired(app, llm, images):
    app.state.test_product_llm = llm
    app.state.test_image_fetcher = images
    return app


def extract(client, body):
    response = client.post("/v1/products/extract", json=body)
    assert response.status_code == 201, response.text
    return response.json()


def all_input_text(body) -> str:
    def walk(v):
        if isinstance(v, dict):
            return " ".join(walk(x) for x in v.values())
        if isinstance(v, list):
            return " ".join(walk(x) for x in v)
        return str(v) if v is not None else ""

    return " ".join(walk(body).split())


# 완료 조건


def test_name_price_image_come_from_json_ld_without_llm(wired, client, llm):
    llm.raises = RuntimeError("LLM down")

    product = extract(client, page())

    assert product["fields"]["name"]["original"] == "ウールブレンド クルーネック カーディガン"
    assert product["fields"]["price"] == {"original": "8800", "ko": "8800 JPY"}
    assert product["image_url"] == "https://img.zozo.jp/goods/7781/1.jpg"
    assert product["has_image"] is True


def test_missing_material_stays_null_even_if_llm_makes_it_up(wired, client, llm):
    text_without_material = "BEAMS ウールブレンド クルーネック カーディガン ¥8,800 サイズ M L"
    json_ld = {k: v for k, v in ZOZO_JSON_LD.items() if k != "description"}
    llm.result = GOOD.model_copy(update={"material": fv("コットン100%", "면 100%")})

    product = extract(client, page(text=text_without_material, json_ld=[json_ld]))

    assert product["fields"]["material"] is None


def test_every_saved_original_is_in_the_input(wired, client, llm):
    # LLM이 일부는 맞게, 일부는 지어내서 준다
    llm.result = GOOD.model_copy(
        update={
            "fit": fv("Relaxed fit", "릴랙스 핏"),
            "care": fv("ドライクリーニング", "드라이클리닝"),
        }
    )
    body = page()

    product = extract(client, body)

    haystack = all_input_text(body)
    for key, value in product["fields"].items():
        if value is not None:
            assert value["original"] in haystack, key
    assert product["fields"]["fit"] is None
    assert product["fields"]["care"] is None


def test_size_measurements_get_converted_units(wired, client):
    product = extract(client, page())

    size = product["fields"]["size"]
    assert size["ko"] == "총장: 25 in, 가슴너비: 21 in"
    assert "63.5 cm (25 in)" in size["converted"]
    assert "53.3 cm (21 in)" in size["converted"]


# 그 밖의 동작


def test_llm_fields_and_translation_are_kept_when_grounded(wired, client, llm):
    product = extract(client, page())

    fields = product["fields"]
    assert fields["name"]["ko"] == "울 블렌드 크루넥 카디건"
    assert fields["material"] == {"original": "ウール50% アクリル50%", "ko": "울 50%, 아크릴 50%"}
    assert fields["care"]["ko"] == "손세탁 가능"
    assert product["category"] == "top"
    assert product["lang"] == "ja"
    assert len(llm.calls) == 1


def test_llm_cannot_replace_the_structured_name(wired, client, llm):
    llm.result = GOOD.model_copy(update={"name": fv("BEAMS", "빔즈")})

    product = extract(client, page())

    assert product["fields"]["name"]["original"] == "ウールブレンド クルーネック カーディガン"


def test_category_without_evidence_is_dropped(wired, client, llm):
    llm.result = GOOD.model_copy(update={"category": "outer", "category_evidence": "Coat"})

    product = extract(client, page())

    assert product["category"] is None


def test_without_json_ld_uses_open_graph(wired, client, llm, images):
    images.store["https://img.zozo.jp/goods/7781/og.jpg"] = make_image_bytes(color=(1, 2, 3))
    og = {
        "og:title": "Wool Coat",
        "og:image": "https://img.zozo.jp/goods/7781/og.jpg",
        "product:price:amount": "129.00",
        "product:price:currency": "USD",
    }
    llm.result = GOOD.model_copy(update={"name": None})

    product = extract(client, page(json_ld=[], og=og, text="Wool Coat $129.00"))

    assert product["fields"]["name"]["original"] == "Wool Coat"
    assert product["fields"]["price"]["ko"] == "129.00 USD"
    assert product["image_url"] == "https://img.zozo.jp/goods/7781/og.jpg"


def test_json_ld_inside_graph_is_found(wired, client, llm):
    llm.raises = RuntimeError("down")
    graph = {"@context": "https://schema.org", "@graph": [{"@type": "WebPage"}, ZOZO_JSON_LD]}

    product = extract(client, page(json_ld=[graph]))

    assert product["fields"]["price"]["original"] == "8800"


def test_image_that_cannot_be_downloaded_still_saves_info(wired, client, images):
    images.store.clear()

    product = extract(client, page())

    assert product["has_image"] is False
    assert product["fields"]["name"] is not None
    similar = client.post(f"/v1/products/{product['id']}/similar")
    assert similar.status_code == 400
    assert similar.json()["detail"] == "상품 사진을 찾지 못했어요"


def test_non_https_image_is_not_downloaded(wired, client, images):
    json_ld = {**ZOZO_JSON_LD, "image": "http://img.zozo.jp/x.jpg"}

    product = extract(client, page(json_ld=[json_ld], og={}))

    assert product["has_image"] is False
    assert "http://img.zozo.jp/x.jpg" not in images.fetched


def test_saved_product_feeds_similar_search(wired, client, analyzer):
    analyzer.category = "top"
    client.post(
        "/v1/clothes", files={"image": ("k.jpg", make_image_bytes(color=(40, 40, 40)), "image/png")}
    )

    product = extract(client, page())
    result = client.post(f"/v1/products/{product['id']}/similar").json()

    assert result["status"] == "ok"
    assert result["items"][0]["level"] == "very_similar"


def test_product_is_stored_and_readable(wired, client, settings):
    product = extract(client, page())

    assert client.get(f"/v1/products/{product['id']}").json() == product
    with sqlite3.connect(settings.db_path) as db:
        site, fields = db.execute(
            "SELECT site, fields FROM products WHERE id = ?", (product["id"],)
        ).fetchone()
    assert site == "zozo.jp"
    assert json.loads(fields)["care"]["ko"] == "손세탁 가능"


def test_other_users_product_is_404(wired, client, as_user):
    as_user("owner")
    product = extract(client, page())
    as_user("stranger")

    assert client.get(f"/v1/products/{product['id']}").status_code == 404


def test_extract_needs_login(app):
    from fastapi.testclient import TestClient

    with TestClient(app) as anon:
        assert anon.post("/v1/products/extract", json=page()).status_code == 401


# 실제 저장 페이지(docs/site_html/zozotown.html)로 확인하다 찾은 버그:
# ZOZOTOWN은 색상별 Product를 JSON-LD 배열 하나에 넣는데, 서버가 객체만 받아 422를 돌려줬다
def test_json_ld_block_that_is_an_array_is_accepted(wired, client, llm):
    llm.raises = RuntimeError("down")
    other_color = {**ZOZO_JSON_LD, "color": "ネイビー"}

    product = extract(
        client, page(json_ld=[{"@type": "BreadcrumbList"}, [ZOZO_JSON_LD, other_color]])
    )

    assert product["fields"]["name"]["original"] == "ウールブレンド クルーネック カーディガン"
    assert product["fields"]["price"]["original"] == "8800"


# 실제 Claude로 ZOZOTOWN 페이지를 돌려 보다 찾은 문제:
# JSON-LD에 색이 있는데 LLM이 비우면 정보 없음이 됐다
def test_color_comes_from_json_ld_when_llm_leaves_it_empty(wired, client, llm):
    llm.result = GOOD.model_copy(update={"color": None})

    product = extract(client, page())

    assert product["fields"]["color"]["original"] == "チャコール"


def test_llm_translation_of_json_ld_color_is_used(wired, client, llm):
    product = extract(client, page())

    assert product["fields"]["color"] == {"original": "チャコール", "ko": "차콜"}
