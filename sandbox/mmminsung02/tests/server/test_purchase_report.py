"""compare/purchase-report.md 완료 조건 [서버]

비교 대상은 가장 비슷한 옷 1벌 (2026-11-11 결정).
색은 쇼핑몰 색 이름을 15색 표로 바꿔 비교하고, 표에 없으면 상품 사진 색을 쓴다 (같은 날 결정).
비슷한 점 / 다른 점 / 확인 안 된 점은 규칙으로 정하고, LLM(가짜)은 문장만 다듬는다.
"""

import json
import sqlite3

import pytest

from conftest import make_image_bytes, upload
from server.ai.report_writer import ReportText, WrittenItem
from server.services.report import color_from_name

BLACK = (0, 0, 0)
NEAR_BLACK = (20, 20, 20)


def field(ko, original=None):
    return {"original": original or ko, "ko": ko}


def add_product(settings, user_id, fields, category="outer", rgb=BLACK) -> int:
    image_path = f"products/{user_id}/{rgb[0]}-{rgb[1]}-{rgb[2]}.png"
    file = settings.data_dir / image_path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(make_image_bytes(color=rgb))
    all_fields = dict.fromkeys(("name", "color", "material", "fit", "size", "care", "price"))
    all_fields.update(fields)
    with sqlite3.connect(settings.db_path) as db:
        cur = db.execute(
            "INSERT INTO products (user_id, source, image_path, category, fields, lang)"
            " VALUES (?, 'page', ?, ?, ?, 'en')",
            (user_id, image_path, category, json.dumps(all_fields, ensure_ascii=False)),
        )
        return cur.lastrowid


def add_cloth(client, analyzer, category="outer", color="black", rgb=NEAR_BLACK, **edits) -> int:
    analyzer.category, analyzer.color = category, color
    response = upload(client, make_image_bytes(color=rgb))
    assert response.status_code == 201, response.text
    cloth_id = response.json()["id"]
    if edits:
        assert client.patch(f"/v1/clothes/{cloth_id}", json=edits).status_code == 200
    return cloth_id


class FakeWriter:
    """사실마다 '[문장] 항목'을 돌려준다. 테스트마다 바꿔 끼운다"""

    def __init__(self, write=None):
        self.calls = []
        self._write = write

    def write(self, facts, cloth_name, product_name):
        self.calls.append(facts)
        if self._write:
            return self._write(facts)
        return ReportText(
            summary="갖고 있는 코트와 비교했어요.",
            items=[WrittenItem(id=f["id"], text=f"[문장] {f['label']}") for f in facts],
        )


@pytest.fixture
def me(as_user):
    return as_user("me")


@pytest.fixture
def writer(app):
    fake = FakeWriter()
    app.state.test_report_writer = fake
    app.state.report_writer = fake
    return fake


@pytest.fixture
def no_writer(app):
    app.state.report_writer = None


def report(client, product_id):
    response = client.post(f"/v1/products/{product_id}/report")
    assert response.status_code == 201, response.text
    return response.json()


def fields_of(items):
    return [i["field"] for i in items]


def find(body, name):
    for part in ("similar", "different", "unknown"):
        for item in body[part]:
            if item["field"] == name:
                return part, item
    return None, None


def test_output_follows_three_part_format(client, analyzer, settings, me, writer):
    add_cloth(client, analyzer, name="카멜 코트", material="울 100%")
    product = add_product(settings, me, {"color": field("블랙", "Black")})

    body = report(client, product)

    assert body["status"] == "ok"
    assert body["product_id"] == product
    assert body["cloth"]["name"] == "카멜 코트"
    assert body["level"] in ("very_similar", "similar")
    for part in ("similar", "different", "unknown"):
        assert isinstance(body[part], list)
        for item in body[part]:
            # 각 항목에 근거 필드명과 양쪽 값이 있다
            assert set(item) >= {"field", "label", "product", "cloth", "text"}
    assert body["summary"] == "갖고 있는 코트와 비교했어요."


def test_missing_cloth_material_goes_to_unknown_without_llm(
    client, analyzer, settings, me, no_writer
):
    add_cloth(client, analyzer)  # 소재를 적지 않은 옷
    product = add_product(settings, me, {"material": field("울 60%, 폴리에스터 40%")})

    body = report(client, product)
    part, item = find(body, "material")

    assert part == "unknown"
    assert item["product"] == "울 60%, 폴리에스터 40%"
    assert item["cloth"] is None
    assert item["text"] is None  # LLM 없이 규칙으로만


def test_same_values_are_similar_and_different_values_are_different(
    client, analyzer, settings, me, no_writer
):
    add_cloth(client, analyzer, category="outer", color="black", material="면 100%")
    product = add_product(
        settings,
        me,
        {"color": field("블랙", "Black"), "material": field("울 100%", "Wool 100%")},
        category="outer",
    )

    body = report(client, product)

    assert "color" in fields_of(body["similar"])
    assert "category" in fields_of(body["similar"])
    assert "material" in fields_of(body["different"])
    assert find(body, "color")[1]["cloth"] == "블랙"


def test_same_fibers_in_different_languages_are_similar(client, analyzer, settings, me, no_writer):
    add_cloth(client, analyzer, material="울 60% 폴리에스터 40%")
    product = add_product(
        settings, me, {"material": field("울 60%, 폴리에스터 40%", "ウール60% ポリエステル40%")}
    )

    assert find(report(client, product), "material")[0] == "similar"


def test_shop_color_name_is_mapped_to_closet_color(client, analyzer, settings, me, no_writer):
    add_cloth(client, analyzer, color="gray")
    product = add_product(settings, me, {"color": field("차콜", "チャコール")})

    part, item = find(report(client, product), "color")

    assert part == "similar"
    assert item["basis"] == "page"


def test_unknown_color_name_falls_back_to_product_photo(client, analyzer, settings, me, no_writer):
    add_cloth(client, analyzer, color="black")
    product = add_product(settings, me, {"color": field("미드나이트", "Midnight")})
    analyzer.color = "black"  # 상품 사진을 읽으면 검정

    part, item = find(report(client, product), "color")

    assert part == "similar"
    assert item["basis"] == "photo"


def test_forbidden_sentences_from_llm_are_removed(client, analyzer, settings, me, writer, caplog):
    add_cloth(client, analyzer, color="black", material="면 100%")
    product = add_product(settings, me, {"color": field("블랙"), "material": field("울 100%")})

    def pushy(facts):
        return ReportText(
            summary="색이 비슷해요. 이미 비슷한 옷이 있으니 사지 마세요. 소재는 달라요.",
            items=[
                WrittenItem(
                    id=f["id"],
                    text="중복 구매 확률이 높아요" if f["field"] == "color" else "소재가 달라요",
                )
                for f in facts
            ],
        )

    writer._write = pushy
    body = report(client, product)

    assert body["summary"] == "색이 비슷해요. 소재는 달라요."
    assert find(body, "color")[1]["text"] is None
    assert find(body, "material")[1]["text"] == "소재가 달라요"
    assert "금지 표현" in caplog.text


def test_llm_sentence_with_numbers_not_in_data_is_dropped(client, analyzer, settings, me, writer):
    add_cloth(client, analyzer, material="면 100%")
    product = add_product(settings, me, {"material": field("울 60%, 폴리에스터 40%")})

    def invents(facts):
        return ReportText(
            summary="",
            items=[WrittenItem(id=f["id"], text="새 상품은 울 80%예요") for f in facts],
        )

    writer._write = invents
    body = report(client, product)

    assert find(body, "material")[1]["text"] is None


def test_empty_closet_gives_no_similar_report(client, settings, me, writer):
    product = add_product(
        settings, me, {"color": field("카멜"), "material": field("울 60%, 폴리에스터 40%")}
    )

    body = report(client, product)

    assert body["status"] == "no_similar"
    assert body["headline"] == "옷장에 비슷한 옷이 없어요"
    assert body["cloth"] is None
    assert body["similar"] == [] and body["different"] == []
    # 확인 안 된 점에 상품 정보만 정리
    assert {"color", "material"} <= set(fields_of(body["unknown"]))
    assert all(item["cloth"] is None for item in body["unknown"])


def test_only_different_clothes_gives_no_similar_report(client, analyzer, settings, me, no_writer):
    add_cloth(client, analyzer, rgb=(255, 255, 255))  # 사진이 전혀 다르다
    product = add_product(settings, me, {"color": field("블랙")}, rgb=BLACK)

    body = report(client, product)

    assert body["status"] == "no_similar"


def test_llm_failure_still_gives_comparison_table(client, analyzer, settings, me, writer):
    add_cloth(client, analyzer, color="black", material="면 100%")
    product = add_product(settings, me, {"color": field("블랙"), "material": field("울 100%")})

    def broken(facts):
        raise RuntimeError("LLM down")

    writer._write = broken
    body = report(client, product)

    assert "color" in fields_of(body["similar"])
    assert "material" in fields_of(body["different"])
    assert body["summary"] is None
    assert all(
        i["text"] is None for part in ("similar", "different", "unknown") for i in body[part]
    )


def test_report_is_saved(client, analyzer, settings, me, no_writer):
    cloth = add_cloth(client, analyzer)
    product = add_product(settings, me, {"color": field("블랙")})

    body = report(client, product)

    with sqlite3.connect(settings.db_path) as db:
        row = db.execute(
            "SELECT product_id, similar, body FROM reports WHERE id = ?", (body["id"],)
        ).fetchone()
    assert row[0] == product
    assert json.loads(row[1])[0]["cloth_id"] == cloth
    assert json.loads(row[2])["similar"] == body["similar"]


def test_other_users_product_is_not_found(client, settings, as_user):
    other = as_user("someone-else")
    product = add_product(settings, other, {"color": field("블랙")})
    as_user("me")

    assert client.post(f"/v1/products/{product}/report").status_code == 404


def test_report_needs_login(app, settings):
    from fastapi.testclient import TestClient

    with TestClient(app) as anonymous:
        assert anonymous.post("/v1/products/1/report").status_code == 401


def test_too_many_reports_fall_back_to_table_without_llm(client, analyzer, settings, me, writer):
    # Claude 비용 상한: 넘으면 오류 대신 문장 없는 비교표
    settings.report_llm_per_window = 2
    add_cloth(client, analyzer)
    product = add_product(settings, me, {"color": field("블랙")})

    bodies = [report(client, product) for _ in range(3)]

    assert [b["summary"] is not None for b in bodies] == [True, True, False]
    assert len(writer.calls) == 2
    assert "color" in fields_of(bodies[2]["similar"])


@pytest.mark.parametrize(
    ("name", "color"),
    [
        ("Navy Blue", "navy"),
        ("Multicolor", "multi"),
        ("オフホワイト", "white"),
        ("Black/White", None),
    ],
)
def test_color_names(name, color):
    # 두 색이 섞인 이름은 하나로 정하지 않는다 (사진 색으로 넘어간다)
    assert color_from_name(name) == color
