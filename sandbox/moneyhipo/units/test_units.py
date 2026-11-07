"""기능 5 단위·사이즈 변환 완료 조건 테스트 (docs/features/translation/unit-conversion.md).

실행 (저장소 루트에서): .venv/Scripts/python -m pytest sandbox/moneyhipo/units -q
"""

import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

from units import convert_clothing_size, convert_shoe_size, convert_text  # noqa: E402

TABLES = json.loads((Path(__file__).with_name("size_tables.json")).read_text(encoding="utf-8"))


# ---- 완료 조건 ----


def test_inch():
    assert convert_text("32 in") == "81.3 cm (32 in)"


def test_inch_quote_mark_same_value():
    assert convert_text('32"') == '81.3 cm (32")'


def test_range():
    assert convert_text("30-32 in") == "76.2-81.3 cm (30-32 in)"


def test_fraction():
    assert convert_text("32 1/4 in") == "81.9 cm (32 1/4 in)"


def test_half_rounds_up_without_float_error():
    # 32.5 in = 82.55 cm. float로 계산하면 82.54999...가 되어 82.5로 내려갈 수 있다
    assert convert_text("32 1/2 in") == "82.6 cm (32 1/2 in)"
    assert convert_text("32.5 in") == "82.6 cm (32.5 in)"


def test_pound():
    assert convert_text("1.5 lb") == "0.68 kg (1.5 lb)"


def test_ounce():
    assert convert_text("8 oz") == "227 g (8 oz)"


def test_shoe_and_clothing_sizes_match_table_and_are_reference_only():
    for gender, systems in TABLES["shoes_mm"].items():
        for system, rows in systems.items():
            for size, mm in rows.items():
                result = convert_shoe_size(system, size, gender)
                assert result.korean == f"{mm} mm"
                assert result.reference_only
                assert result.text == f"{mm} mm ({system} {size}, 참고용)"
    for gender, systems in TABLES["clothing_kr"].items():
        for system, rows in systems.items():
            for size, kr in rows.items():
                result = convert_clothing_size(system, size, gender)
                assert result.korean == kr
                assert "참고용" in result.text


@pytest.mark.parametrize("text", ["32 cubits", "about 5 km", "Size M", "", "8 fl oz"])
def test_unknown_unit_unchanged(text):
    assert convert_text(text) == text


def test_units_inside_sentence():
    assert (
        convert_text("Length: 28 in, Chest: 21 in")
        == "Length: 71.1 cm (28 in), Chest: 53.3 cm (21 in)"
    )


# ---- 예외 상황·그 밖의 형식 ----


def test_unknown_gender_returns_none():
    assert convert_clothing_size("US", "M", None) is None
    assert convert_shoe_size("US", "9", None) is None


def test_size_not_in_table_returns_none():
    assert convert_shoe_size("US", "25", "men") is None
    assert convert_clothing_size("KR", "M", "men") is None


def test_size_input_is_normalized():
    assert convert_shoe_size(" us ", "9.0", "men").korean == "270 mm"
    assert convert_shoe_size("US", "10", "men").korean == "280 mm"
    assert convert_clothing_size("eu", "38", "women").korean == "66"
    assert convert_clothing_size("US", "m", "men").korean == "100"


@pytest.mark.parametrize(
    ("text", "want"),
    [
        ("32 inches", "81.3 cm (32 inches)"),
        ("32in", "81.3 cm (32in)"),
        ("32 IN", "81.3 cm (32 IN)"),
        ("6 ft", "182.9 cm (6 ft)"),
        ("2 lbs", "0.91 kg (2 lbs)"),
        ("30 - 32 in", "76.2-81.3 cm (30 - 32 in)"),
        ("1/2 in", "1.3 cm (1/2 in)"),
    ],
)
def test_other_formats(text, want):
    assert convert_text(text) == want


def test_model_height_feet_and_inches():
    assert convert_text("Model is 5'10\" and wears S") == ("Model is 177.8 cm (5'10\") and wears S")
    assert convert_text("5 ft 7 in") == "170.2 cm (5 ft 7 in)"


def test_inch_followed_by_measure_word():
    assert convert_text("30 in inseam") == "76.2 cm (30 in) inseam"
    assert convert_text("12 in x 8 in") == "30.5 cm (12 in) x 20.3 cm (8 in)"
    assert convert_text("Waist (in): 30") == "Waist (in): 30"


@pytest.mark.parametrize(
    "text",
    ["2 in 1 design", "Only 3 in stock", "Available 2 in black", "1,500 lb", "Size2 in"],
)
def test_not_a_unit_or_unknown_format_unchanged(text):
    # '2 in 1'은 단위가 아니고, 쉼표 천 단위나 글자에 붙은 수는 형식을 몰라 그대로 둔다
    assert convert_text(text) == text
