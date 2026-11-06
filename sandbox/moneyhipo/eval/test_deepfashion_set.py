"""deepfashion_set의 이름 해석·자르기 상자 테스트.

실행 (저장소 루트에서): .venv/Scripts/python -m pytest sandbox/moneyhipo/eval -q
"""

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))

from deepfashion_set import crop_box, parse_name  # noqa: E402


def test_parse_name_maps_shop_category():
    assert parse_name("WOMEN-Tees_Tanks-id_00000001-02_1_front.jpg") == (
        "top",
        "WOMEN-Tees_Tanks-id_00000001-02",
    )
    assert parse_name("WOMEN-Cardigans-id_00000002-01_7_additional.jpg")[0] == "outer"
    assert parse_name("MEN-Pants-id_00000003-01_4_full.jpg")[0] == "bottom"
    assert parse_name("WOMEN-Dresses-id_00000004-01_2_side.jpg")[0] == "other"


def test_parse_name_skips_ambiguous_categories():
    for shop in ("Sweatshirts_Hoodies", "Denim", "Suiting"):
        assert parse_name(f"MEN-{shop}-id_00000005-01_1_front.jpg")[0] is None


def test_crop_box_wraps_target_parts_with_padding():
    mask = np.zeros((100, 100), dtype=np.uint8)
    mask[20:40, 30:70] = 1  # top
    mask[60:90, 10:20] = 5  # pants
    assert crop_box(mask, (1,)) == (28, 19, 72, 41)
    assert crop_box(mask, (3, 5, 6)) == (10, 59, 20, 91)


def test_crop_box_rejects_tiny_parts():
    mask = np.zeros((100, 100), dtype=np.uint8)
    mask[0:2, 0:10] = 2  # 20픽셀 = 0.2%
    assert crop_box(mask, (2,)) is None
