"""shopping/share-intake.md 완료 조건 [서버]: 공유받은 스크린샷 한 장 → 상품 정보 → TOP 3 → 리포트.

글자 읽기는 Claude가 사진을 직접 읽는다 (2026-11-11 결정, 미결정 'OCR').
옷 사진 부분은 Claude가 알려 준 위치로 서버가 잘라 낸다 (같은 날 결정).
여기서 Claude(사진 읽기·리포트 문장)는 가짜다.
"""

import io
import sqlite3

import pytest
from PIL import Image

from conftest import make_image_bytes, upload
from server.ai.product_llm import ExtractedProduct, FieldValue
from server.ai.screenshot_reader import Box, ScreenshotRead

W, H = 1080, 2340
BOX = (140, 300, 940, 1300)  # 스크린샷 안 옷 사진 자리 (픽셀)
TRANSCRIPT = "\n".join(
    [
        "ZOZOTOWN",
        "ウールブレンド クルーネック カーディガン",
        "¥8,800 税込",
        "カラー：チャコール",
        "素材：ウール50% アクリル50%",
        "サイズ：M L",
    ]
)


def screenshot_bytes(fmt="PNG") -> bytes:
    """흰 화면 가운데에 검은 옷 사진이 있는 고정 스크린샷"""
    image = Image.new("RGB", (W, H), (255, 255, 255))
    image.paste((0, 0, 0), BOX)
    buf = io.BytesIO()
    image.save(buf, format=fmt)
    return buf.getvalue()


def fv(original, ko):
    return FieldValue(original=original, ko=ko)


def sample_product(**overrides) -> ExtractedProduct:
    values = {
        "lang": "ja",
        "name": fv("ウールブレンド クルーネック カーディガン", "울 블렌드 크루넥 카디건"),
        "category": "top",
        "category_evidence": "カーディガン",
        "color": fv("チャコール", "차콜"),
        "material": fv("ウール50% アクリル50%", "울 50%, 아크릴 50%"),
        "fit": None,
        "size": fv("M L", "M L"),
        "care": None,
    }
    values.update(overrides)
    return ExtractedProduct(**values)


class FakeReader:
    """사진을 받은 것처럼 정해 둔 결과를 돌려준다. 받은 사진 크기를 기록한다"""

    def __init__(self):
        self.transcript = TRANSCRIPT
        self.box: tuple[int, int, int, int] | None = BOX
        self.unclear: list[str] = []
        self.product = sample_product()
        self.fail = False
        self.sizes: list[tuple[int, int]] = []

    def read(self, image_bytes: bytes, media_type: str) -> ScreenshotRead:
        image = Image.open(io.BytesIO(image_bytes))
        self.sizes.append(image.size)
        if self.fail:
            raise RuntimeError("Claude down")
        box = None
        if self.box:
            # 테스트는 원본 픽셀로 적어 두고, 받은 사진 크기에 맞춰 바꿔 준다
            sx, sy = image.size[0] / W, image.size[1] / H
            x0, y0, x1, y1 = self.box
            box = Box(x0=round(x0 * sx), y0=round(y0 * sy), x1=round(x1 * sx), y1=round(y1 * sy))
        return ScreenshotRead(
            transcript=self.transcript, unclear=self.unclear, product_box=box, product=self.product
        )


@pytest.fixture
def reader(app):
    fake = FakeReader()
    app.state.test_screenshot_reader = fake
    app.state.screenshot_reader = fake
    return fake


@pytest.fixture(autouse=True)
def no_report_writer(app):
    app.state.report_writer = None


def send(client, data=None, name="shot.png", mime="image/png"):
    return client.post(
        "/v1/products/from-screenshot",
        files={"image": (name, data or screenshot_bytes(), mime)},
    )


def add_cloth(client, analyzer, category="top", color="gray", rgb=(10, 10, 10)) -> int:
    analyzer.category, analyzer.color = category, color
    response = upload(client, make_image_bytes(color=rgb))
    assert response.status_code == 201, response.text
    return response.json()["id"]


def test_screenshot_goes_through_product_similar_and_report_at_once(client, analyzer, reader):
    cloth = add_cloth(client, analyzer)

    response = send(client)

    assert response.status_code == 201, response.text
    body = response.json()
    product = body["product"]
    assert product["source"] == "screenshot"
    assert product["fields"]["name"]["ko"] == "울 블렌드 크루넥 카디건"
    assert product["category"] == "top"
    assert product["has_image"] is True
    assert body["similar"]["status"] == "ok"
    assert body["similar"]["items"][0]["cloth"]["id"] == cloth
    assert body["report"]["status"] == "ok"
    assert body["report"]["product_id"] == product["id"]
    assert body["notice"] is None


def test_image_without_text_returns_error_message(client, reader):
    reader.transcript = "12:30  ▼ ◀ ●"  # 상태 표시줄 숫자·기호만

    response = send(client)

    assert response.status_code == 422
    assert (
        response.json()["detail"] == "글자를 읽지 못했어요. 상품 설명이 보이게 다시 캡처해 주세요"
    )


def test_no_clothing_photo_gives_info_card_without_search(client, analyzer, reader):
    add_cloth(client, analyzer)
    reader.box = None

    body = send(client).json()

    assert body["product"]["has_image"] is False
    assert body["product"]["fields"]["material"]["ko"] == "울 50%, 아크릴 50%"
    assert body["similar"] is None and body["report"] is None
    assert body["notice"] == "스크린샷에서 옷 사진을 찾지 못해 비슷한 옷은 찾지 않았어요"


def test_tiny_box_counts_as_no_photo(client, reader):
    reader.box = (100, 100, 110, 112)

    assert send(client).json()["product"]["has_image"] is False


def test_photo_is_cropped_from_the_box(client, settings, reader, as_user):
    me = as_user("me")

    product_id = send(client).json()["product"]["id"]

    with sqlite3.connect(settings.db_path) as db:
        (path,) = db.execute(
            "SELECT image_path FROM products WHERE id = ?", (product_id,)
        ).fetchone()
    assert path.startswith(f"products/{me}/")
    cropped = Image.open(settings.data_dir / path).convert("RGB")
    width, height = cropped.size
    assert abs(width / height - (BOX[2] - BOX[0]) / (BOX[3] - BOX[1])) < 0.05
    # 잘라 낸 사진에 흰 화면(글자 자리)이 거의 없다
    assert cropped.getpixel((width // 2, height // 2)) == (0, 0, 0)
    assert cropped.getpixel((2, 2))[0] < 60


def test_unclear_text_marks_field_uncertain(client, reader):
    reader.unclear = ["ウール50%"]

    fields = send(client).json()["product"]["fields"]

    assert fields["material"]["uncertain"] is True
    assert fields["name"].get("uncertain") is not True


def test_value_not_in_read_text_is_dropped(client, reader):
    # 근거 검사 (기능 7과 같다): 읽은 글자에 없는 값은 버린다
    reader.product = sample_product(care=fv("手洗い可", "손세탁 가능"))

    fields = send(client).json()["product"]["fields"]

    assert fields["care"] is None
    assert fields["material"] is not None


def test_reader_gets_a_downscaled_image(client, reader):
    send(client)

    assert max(reader.sizes[0]) <= 1568


def test_reader_failure_is_reported(client, reader):
    reader.fail = True

    response = send(client)

    assert response.status_code == 502
    assert response.json()["detail"] == "스크린샷을 읽지 못했어요. 잠시 후 다시 시도해 주세요"


def test_not_an_image_is_rejected(client, reader):
    response = send(client, data=b"not an image", name="a.txt", mime="text/plain")

    assert response.status_code == 400
    assert reader.sizes == []


def test_too_big_upload_is_rejected(client, settings, reader):
    settings.max_upload_bytes = 1000

    assert send(client).status_code == 413
    assert reader.sizes == []


def test_screenshot_needs_login(app, reader):
    from fastapi.testclient import TestClient

    with TestClient(app) as anonymous:
        assert send(anonymous).status_code == 401
    assert reader.sizes == []


def test_too_many_screenshots_are_limited(client, settings, reader):
    settings.screenshot_requests_per_window = 2

    codes = [send(client).status_code for _ in range(3)]

    assert codes == [201, 201, 429]
    assert len(reader.sizes) == 2


def test_no_reader_configured_is_503(client, app):
    app.state.screenshot_reader = None

    assert send(client).status_code == 503


def test_cropped_photo_can_be_downloaded_by_its_owner_only(client, reader, as_user):
    as_user("me")
    product = send(client).json()["product"]

    assert product["image_path"] == f"/v1/products/{product['id']}/image"
    response = client.get(product["image_path"])
    assert response.status_code == 200
    assert Image.open(io.BytesIO(response.content)).size[0] > 100

    as_user("someone-else")
    assert client.get(product["image_path"]).status_code == 404
