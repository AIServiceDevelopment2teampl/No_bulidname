"""자동 등록 완료 조건 [서버]

- auto-register/order-capture.md (기능 13)
- auto-register/in-app.md (기능 14)
- auto-register/receipt.md (기능 15)

Claude가 사진을 읽는 부분은 가짜(FakeImportReader)로 바꾼다.
"""

import io
import json
import re
import sqlite3

import pytest
from PIL import Image

from conftest import make_image_bytes
from server.ai.import_reader import ImportItem, ImportRead
from server.ai.screenshot_reader import Box

# 개인 정보 형식 (완료 조건의 정규식 검사)
PERSONAL = [
    re.compile(r"01[016789][-\s]?\d{3,4}[-\s]?\d{4}"),  # 휴대전화
    re.compile(r"\d{4}-[\d*]{4}-[\d*]{4}-[\d*]{4}"),  # 카드 번호
    re.compile(r"[\w.]+@[\w-]+\.\w+"),  # 이메일
    re.compile(r"홍길동|테헤란로|강남구|승인번호|12345678"),  # 가짜 입력에 넣은 이름·주소·승인번호
]


def capture_bytes(color=(200, 30, 30)) -> bytes:
    """세로 화면 캡처. (20,20)-(220,220)에 썸네일 색 칸이 있다"""
    image = Image.new("RGB", (600, 1200), (255, 255, 255))
    image.paste(Image.new("RGB", (200, 200), color), (20, 20))
    buf = io.BytesIO()
    image.save(buf, format="PNG")
    return buf.getvalue()


def item(name, **kw) -> ImportItem:
    values = dict(
        name=name,
        option=None,
        color=None,
        size=None,
        price=None,
        date=None,
        product_code=None,
        is_clothing=True,
        category=None,
        thumbnail=None,
    )
    values.update(kw)
    return ImportItem(**values)


BOX = Box(x0=20, y0=20, x1=220, y1=220)


def order_read(**kw) -> ImportRead:
    values = dict(
        transcript=(
            "주문내역 주문번호 2611-0412-88 2026.10.12 오버핏 코튼 셔츠 화이트 / L ₩39,900 "
            "와이드 데님 팬츠 인디고 / 30 ₩59,000 핸드크림 50ml ₩12,000"
        ),
        order_number="2611-0412-88",
        store=None,
        items=[
            item(
                "오버핏 코튼 셔츠",
                option="화이트 / L",
                color="white",
                size="L",
                price="₩39,900",
                date="2026-10-12",
                category="top",
                thumbnail=BOX,
            ),
            item(
                "와이드 데님 팬츠",
                option="인디고 / 30",
                color="blue",
                size="30",
                price="₩59,000",
                date="2026-10-12",
                category="bottom",
            ),
            item("핸드크림 50ml", price="₩12,000", is_clothing=False),
        ],
    )
    values.update(kw)
    return ImportRead(**values)


def receipt_read(**kw) -> ImportRead:
    values = dict(
        transcript=(
            "UNIQLO 강남점 2026-11-01 울 니트 베스트 GRAY M 45,000 쇼핑백 100 "
            "포장비 500 카드 1234-56**-****-7890 승인번호 12345678"
        ),
        order_number=None,
        store="UNIQLO 강남점",
        items=[
            item(
                "울 니트 베스트",
                option="GRAY M",
                color="gray",
                size="M",
                price="45,000",
                date="2026-11-01",
                category="top",
            ),
            item("쇼핑백", price="100", is_clothing=False),
            item("포장비", price="500", is_clothing=True),  # LLM이 틀려도 줄 이름으로 거른다
        ],
    )
    values.update(kw)
    return ImportRead(**values)


class FakeImportReader:
    def __init__(self):
        self.reads: list[ImportRead] = []
        self.calls: list[str] = []

    def read(self, image_bytes, media_type, kind):
        self.calls.append(kind)
        if not self.reads:
            raise RuntimeError("읽을 결과가 없음")
        return self.reads.pop(0)


@pytest.fixture
def reader(app):
    fake = FakeImportReader()
    app.state.test_import_reader = fake
    app.state.import_reader = fake
    return fake


def send(client, kind, count=1):
    files = [("images", (f"shot{i}.png", capture_bytes(), "image/png")) for i in range(count)]
    return client.post("/v1/imports", data={"type": kind}, files=files)


def closet(client) -> list[dict]:
    return client.get("/v1/clothes").json()["items"]


def db_dump(settings) -> str:
    with sqlite3.connect(settings.db_path) as db:
        return "\n".join(db.iterdump())


def embedding_count(settings) -> int:
    with sqlite3.connect(settings.db_path) as db:
        return db.execute("SELECT COUNT(*) FROM clothes_embeddings").fetchone()[0]


# ------------------------------------------------------------------ 기능 13: 주문내역 캡처


def test_order_capture_saves_nothing_before_confirm(client, reader):
    reader.reads = [order_read()]
    response = send(client, "order_capture")
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["status"] == "pending"
    assert [c["name"] for c in body["candidates"]] == [
        "오버핏 코튼 셔츠",
        "와이드 데님 팬츠",
        "핸드크림 50ml",
    ]
    assert closet(client) == []


def test_non_clothing_is_shown_but_unchecked(client, reader):
    reader.reads = [order_read()]
    candidates = send(client, "order_capture").json()["candidates"]
    assert [c["selected"] for c in candidates] == [True, True, False]
    assert candidates[2]["is_clothing"] is False


def test_confirm_saves_only_chosen_as_order_capture(client, reader):
    reader.reads = [order_read()]
    body = send(client, "order_capture").json()
    response = client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 1}]})
    assert response.status_code == 200, response.text
    saved = closet(client)
    assert [c["name"] for c in saved] == ["와이드 데님 팬츠"]
    assert saved[0]["source"] == "order_capture"
    assert saved[0]["category"] == "bottom" and saved[0]["color"] == "blue"
    assert saved[0]["size"] == "30"
    assert client.get(f"/v1/imports/{body['id']}").json()["status"] == "confirmed"


def test_user_edits_on_confirm_are_used(client, reader):
    reader.reads = [order_read()]
    body = send(client, "order_capture").json()
    client.post(
        f"/v1/imports/{body['id']}/confirm",
        json={"items": [{"index": 0, "category": "outer", "name": "셔츠 재킷"}]},
    )
    saved = closet(client)[0]
    assert saved["category"] == "outer" and saved["name"] == "셔츠 재킷"


def test_same_order_twice_does_not_make_two_clothes(client, reader):
    reader.reads = [order_read(), order_read()]
    first = send(client, "order_capture").json()
    client.post(f"/v1/imports/{first['id']}/confirm", json={"items": [{"index": 0}, {"index": 1}]})
    second = send(client, "order_capture").json()
    assert [c["duplicate"] for c in second["candidates"][:2]] == [True, True]
    assert [c["selected"] for c in second["candidates"][:2]] == [False, False]
    # 사용자가 억지로 골라도 새로 생기지 않는다
    response = client.post(
        f"/v1/imports/{second['id']}/confirm", json={"items": [{"index": 0}, {"index": 1}]}
    )
    assert response.status_code == 200
    assert len(response.json()["skipped"]) == 2
    assert len(closet(client)) == 2


def test_confirming_same_import_twice_is_rejected(client, reader):
    reader.reads = [order_read()]
    body = send(client, "order_capture").json()
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    again = client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    assert again.status_code == 409
    assert len(closet(client)) == 1


def test_overlapping_captures_list_each_product_once(client, reader):
    reader.reads = [order_read(), order_read()]
    body = send(client, "order_capture", count=2).json()
    assert len(body["candidates"]) == 3
    assert reader.calls == ["order_capture", "order_capture"]


def test_personal_info_never_reaches_candidates_or_db(client, settings, reader):
    transcript = (
        "주문자 홍길동 010-1234-5678 hong@example.com 서울 강남구 테헤란로 123 "
        "주문번호 2611-0412-88 오버핏 코튼 셔츠 화이트 / L ₩39,900 "
        "카드 1234-5678-****-9012"
    )
    reader.reads = [
        order_read(
            transcript=transcript,
            store="홍길동 010-1234-5678",
            items=[
                item(
                    "오버핏 코튼 셔츠",
                    option="서울 강남구 테헤란로 123",
                    size="L",
                    price="₩39,900",
                    color="white",
                    thumbnail=BOX,
                ),
                item("홍길동 010-1234-5678"),  # 이름·전화번호가 상품명 칸에 들어온 경우
                item("hong@example.com"),
            ],
        )
    ]
    body = send(client, "order_capture").json()
    text = json.dumps(body, ensure_ascii=False)
    for pattern in PERSONAL:
        assert not pattern.search(text), pattern.pattern
    assert [c["name"] for c in body["candidates"]] == ["오버핏 코튼 셔츠"]
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    dump = db_dump(settings)
    for pattern in PERSONAL:
        assert not pattern.search(dump), pattern.pattern
    assert "주문자" not in dump  # 읽은 글자 전체(transcript)는 저장하지 않는다


def test_value_not_in_read_text_is_dropped(client, reader):
    reader.reads = [order_read(items=[item("없는 상품"), item("오버핏 코튼 셔츠", size="XXL")])]
    candidates = send(client, "order_capture").json()["candidates"]
    assert [c["name"] for c in candidates] == ["오버핏 코튼 셔츠"]
    assert candidates[0]["size"] is None


def test_thumbnail_makes_embedding_and_missing_one_does_not(client, settings, reader):
    reader.reads = [order_read()]
    body = send(client, "order_capture").json()
    assert [c["has_image"] for c in body["candidates"][:2]] == [True, False]
    image = client.get(body["candidates"][0]["image_url"])
    assert image.status_code == 200
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}, {"index": 1}]})
    saved = {c["name"]: c for c in closet(client)}
    assert saved["오버핏 코튼 셔츠"]["needs_photo"] is False
    assert saved["와이드 데님 팬츠"]["needs_photo"] is True
    assert embedding_count(settings) == 1


def test_unreadable_capture_asks_to_capture_again(client, reader):
    reader.reads = [order_read(transcript="...", items=[])]
    response = send(client, "order_capture")
    assert response.status_code == 422
    assert response.json()["detail"] == "주문내역이 잘 보이게 다시 캡처해 주세요"


def test_discard_saves_nothing_and_removes_thumbnails(client, settings, reader, as_user):
    me = as_user("discard-user")
    reader.reads = [order_read()]
    body = send(client, "order_capture").json()
    assert client.delete(f"/v1/imports/{body['id']}").status_code == 204
    assert closet(client) == []
    assert not (settings.data_dir / "imports" / me / str(body["id"])).exists()
    assert client.post(f"/v1/imports/{body['id']}/confirm", json={"items": []}).status_code == 409


def test_other_users_import_is_not_found(client, reader, as_user):
    as_user("owner")
    reader.reads = [order_read()]
    body = send(client, "order_capture").json()
    as_user("stranger")
    assert client.get(f"/v1/imports/{body['id']}").status_code == 404
    assert client.post(f"/v1/imports/{body['id']}/confirm", json={"items": []}).status_code == 404
    assert client.get(body["candidates"][0]["image_url"]).status_code == 404


def test_without_reader_returns_503(client, app):
    app.state.import_reader = None
    assert send(client, "order_capture").status_code == 503


def test_delete_my_data_removes_pending_import_photos(client, settings, reader, as_user):
    me = as_user("cleanup-user")
    reader.reads = [order_read()]
    send(client, "order_capture")
    assert (settings.data_dir / "imports" / me).exists()
    client.delete("/v1/me/data")
    assert not (settings.data_dir / "imports" / me).exists()


# ------------------------------------------------------------------ 기능 15: 영수증


def test_receipt_saves_nothing_before_confirm(client, reader):
    reader.reads = [receipt_read()]
    body = send(client, "receipt").json()
    assert body["type"] == "receipt" and body["status"] == "pending"
    assert closet(client) == []


def test_receipt_drops_bag_and_packaging_lines(client, reader):
    reader.reads = [receipt_read()]
    candidates = send(client, "receipt").json()["candidates"]
    assert [c["name"] for c in candidates] == ["울 니트 베스트"]


def test_receipt_card_and_approval_numbers_are_not_kept(client, settings, reader):
    reader.reads = [
        receipt_read(
            items=[
                item("울 니트 베스트", size="M", color="gray", product_code="1234-56**-****-7890"),
                item("승인번호 12345678"),
            ]
        )
    ]
    body = send(client, "receipt").json()
    text = json.dumps(body, ensure_ascii=False)
    for pattern in PERSONAL:
        assert not pattern.search(text), pattern.pattern
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    dump = db_dump(settings)
    for pattern in PERSONAL:
        assert not pattern.search(dump), pattern.pattern


def test_receipt_clothes_have_no_image_and_skip_similar_search(client, settings, reader, as_user):
    from test_similar_search import add_product

    me = as_user("receipt-user")
    reader.reads = [receipt_read()]
    body = send(client, "receipt").json()
    assert body["candidates"][0]["has_image"] is False
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    cloth = closet(client)[0]
    assert cloth["source"] == "receipt" and cloth["needs_photo"] is True
    assert embedding_count(settings) == 0
    product = add_product(settings, me, rgb=(90, 90, 90))
    result = client.post(f"/v1/products/{product}/similar").json()
    assert all(c["cloth"]["id"] != cloth["id"] for c in result.get("items", []))


def test_attaching_photo_later_makes_it_searchable(client, settings, reader, as_user):
    from test_similar_search import add_product

    me = as_user("attach-user")
    reader.reads = [receipt_read()]
    body = send(client, "receipt").json()
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    cloth_id = closet(client)[0]["id"]
    response = client.put(
        f"/v1/clothes/{cloth_id}/image",
        files={"image": ("vest.png", make_image_bytes(color=(90, 90, 90)), "image/png")},
    )
    assert response.status_code == 200, response.text
    assert response.json()["needs_photo"] is False
    assert embedding_count(settings) == 1
    product = add_product(settings, me, rgb=(90, 90, 90))
    result = client.post(f"/v1/products/{product}/similar").json()
    assert [c["cloth"]["id"] for c in result["items"]] == [cloth_id]


def test_unknown_abbreviation_becomes_other(client, reader):
    reader.reads = [
        receipt_read(
            transcript="UNIQLO 2026-11-01 UT GRPHC 19,900",
            items=[item("UT GRPHC", color="black", category=None)],
        )
    ]
    body = send(client, "receipt").json()
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    cloth = closet(client)[0]
    assert cloth["category"] == "other" and cloth["needs_category"] is True


def test_missing_color_must_be_chosen(client, reader):
    reader.reads = [receipt_read(items=[item("울 니트 베스트", color=None)])]
    body = send(client, "receipt").json()
    response = client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    assert response.status_code == 400
    ok = client.post(
        f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0, "color": "gray"}]}
    )
    assert ok.status_code == 200
    assert closet(client)[0]["color"] == "gray"


def test_receipt_takes_one_photo(client, reader):
    assert send(client, "receipt", count=2).status_code == 400


# ------------------------------------------------------------------ 기능 14: 앱에서 산 옷


def order(**kw) -> dict:
    values = {
        "site": "zozo.jp",
        "url": "https://zozo.jp/_cart/order/complete.html",
        "order_number": "2611-0412-88",
        "items": [
            {
                "name": "ウールブレンド クルーネック カーディガン",
                "color": "チャコール",
                "size": "M",
                "price": "¥8,800",
                "product_url": "https://zozo.jp/shop/beams/goods/123/",
            },
            {"name": "キャンバス トートバッグ", "price": "¥3,300"},
        ],
    }
    values.update(kw)
    return values


def seen_product(settings, user_id, url, name, rgb=(60, 60, 60), category="top") -> int:
    image_path = f"products/{user_id}/seen.png"
    file = settings.data_dir / image_path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(make_image_bytes(color=rgb))
    fields = {
        "name": {"original": name, "ko": "울 블렌드 크루넥 카디건"},
        "material": {"original": "ウール50%", "ko": "울 50%"},
    }
    with sqlite3.connect(settings.db_path) as db:
        cur = db.execute(
            "INSERT INTO products (user_id, source, site, url, image_path, category, fields, lang)"
            " VALUES (?, 'page', 'zozo.jp', ?, ?, ?, ?, 'ja')",
            (user_id, url, image_path, category, json.dumps(fields, ensure_ascii=False)),
        )
        return cur.lastrowid


def test_in_app_order_saves_nothing_before_confirm(client):
    response = client.post("/v1/imports/in-app", json=order())
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["type"] == "in_app" and body["status"] == "pending"
    assert [c["selected"] for c in body["candidates"]] == [True, False]  # 토트백은 의류 아님
    assert closet(client) == []


def test_same_order_number_is_not_registered_twice(client):
    first = client.post("/v1/imports/in-app", json=order()).json()
    again = client.post("/v1/imports/in-app", json=order())
    assert again.status_code == 200 and again.json()["id"] == first["id"]
    client.post(
        f"/v1/imports/{first['id']}/confirm", json={"items": [{"index": 0, "color": "gray"}]}
    )
    assert len(closet(client)) == 1
    # 새로고침해 같은 주문을 다시 보내도 새 후보가 생기지 않는다
    third = client.post("/v1/imports/in-app", json=order())
    assert third.json()["id"] == first["id"] and third.json()["status"] == "confirmed"
    assert len(closet(client)) == 1


def test_matching_seen_product_fills_image_and_attributes(client, settings, as_user):
    me = as_user("shopper")
    seen_product(
        settings,
        me,
        "https://zozo.jp/shop/beams/goods/123/?utm=x",
        "ウールブレンド クルーネック カーディガン",
    )
    body = client.post("/v1/imports/in-app", json=order()).json()
    first = body["candidates"][0]
    assert first["has_image"] is True
    assert first["category"] == "top" and first["material"] == "울 50%"
    client.post(f"/v1/imports/{body['id']}/confirm", json={"items": [{"index": 0}]})
    saved = closet(client)[0]
    assert saved["source"] == "in_app" and saved["needs_photo"] is False
    assert saved["material"] == "울 50%" and saved["category"] == "top"
    assert embedding_count(settings) == 1


def test_unmatched_item_without_image_needs_photo(client):
    body = client.post(
        "/v1/imports/in-app", json=order(items=[{"name": "リネンシャツ"}], order_number="X-1")
    ).json()
    assert body["candidates"][0]["has_image"] is False


def test_in_app_order_with_no_items_is_rejected(client):
    assert client.post("/v1/imports/in-app", json=order(items=[])).status_code == 422
