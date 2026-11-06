"""DeepFashion-MultiModal에서 종류 분류 평가용 사진을 뽑는다.

모델이 입은 쇼핑몰 사진이라 한 장에 옷이 여러 벌 보인다. 그래서 사람이 칠한 부위 마스크(segm)가
있는 전신 사진만 쓰고, 파일 이름의 상품 종류에 해당하는 부위만 잘라 "사진 1장에 옷 1벌"로 만든다.

라이선스(비상업 연구용, 재배포 금지)라 잘라 낸 사진과 라벨 파일은 Git에 올리지 않는
DeepFashin_dataset/ 아래에만 저장한다.

실행 (저장소 루트에서):
  .venv/Scripts/python sandbox/moneyhipo/eval/deepfashion_set.py
"""

import argparse
import csv
import io
import random
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
DATASET = ROOT / "DeepFashin_dataset"
IMAGES_ZIP = DATASET / "images.zip의 사본"
SEGM_OUTER_ZIP = DATASET / "drive-download-20261105T131426Z-1-006.zip"
DEFAULT_OUT = DATASET / "eval" / "deepfashion_100"

# 파일 이름의 상품 종류 → 앱 종류 (server/domain.py CATEGORIES)
# 가디건·후드 집업은 아우터, 지퍼 없는 후드티는 상의 (사용자 결정 2026-11-07)
SHOP_CATEGORY = {
    "Tees_Tanks": "top",
    "Blouses_Shirts": "top",
    "Sweaters": "top",
    "Graphic_Tees": "top",
    "Shirts_Polos": "top",
    "Jackets_Coats": "outer",
    "Jackets_Vests": "outer",
    "Cardigans": "outer",
    "Pants": "bottom",
    "Shorts": "bottom",
    "Skirts": "bottom",
    "Leggings": "bottom",
    "Dresses": "other",
    "Rompers_Jumpsuits": "other",
    # 뺀 것: Sweatshirts_Hoodies(풀오버와 집업이 섞여 파일 이름으로 못 가름),
    # Denim(청바지와 청재킷이 섞임), Suiting(정장 상의·하의가 섞임)
}

# 부위 마스크 번호 (DeepFashion-MultiModal README): 1 top, 2 outer, 3 skirt, 4 dress,
# 5 pants, 6 leggings, 21 rompers
MASK_LABELS = {
    "top": (1,),
    "outer": (2,),
    "bottom": (3, 5, 6),
    "other": (4, 21),
}

MIN_AREA_RATIO = 0.03  # 해당 부위가 사진의 3%도 안 되면 뺀다 (가려졌거나 잘린 것)
PAD_RATIO = 0.05  # 자른 상자 바깥으로 붙이는 여백 (상자 크기 대비)


def parse_name(image_name: str) -> tuple[str | None, str]:
    """이미지 이름 → (앱 종류, 상품 id). 종류를 못 정하면 None.

    'WOMEN-Tees_Tanks-id_00000001-02_1_front.jpg' → ('top', 'WOMEN-Tees_Tanks-id_00000001-02')
    """
    parts = image_name.split("-")
    shop = parts[1] if len(parts) > 1 else ""
    product = image_name.rsplit("_", 2)[0]
    return SHOP_CATEGORY.get(shop), product


def crop_box(mask: np.ndarray, labels: tuple[int, ...]) -> tuple[int, int, int, int] | None:
    """마스크에서 labels 부위를 감싸는 상자(left, top, right, bottom). 너무 작으면 None."""
    hit = np.isin(mask, labels)
    if hit.sum() < MIN_AREA_RATIO * mask.size:
        return None
    ys, xs = np.nonzero(hit)
    top, bottom = int(ys.min()), int(ys.max()) + 1
    left, right = int(xs.min()), int(xs.max()) + 1
    pad_y = int((bottom - top) * PAD_RATIO)
    pad_x = int((right - left) * PAD_RATIO)
    h, w = mask.shape
    return (
        max(0, left - pad_x),
        max(0, top - pad_y),
        min(w, right + pad_x),
        min(h, bottom + pad_y),
    )


def open_segm_zip() -> zipfile.ZipFile:
    # 드라이브에서 받은 zip 안에 segm.zip이 한 번 더 들어 있다
    outer = zipfile.ZipFile(SEGM_OUTER_ZIP)
    return zipfile.ZipFile(io.BytesIO(outer.read("segm.zip")))


def build(out_dir: Path, per_category: int, seed: int) -> None:
    segm = open_segm_zip()
    images = zipfile.ZipFile(IMAGES_ZIP)

    # 상품 하나에서 한 장만 쓴다 (같은 옷의 다른 각도가 섞이면 정확도가 부풀려진다)
    candidates: dict[str, dict[str, list[str]]] = {c: {} for c in MASK_LABELS}
    for entry in segm.namelist():
        if not entry.endswith("_segm.png"):
            continue
        image_name = entry.removeprefix("segm/").removesuffix("_segm.png") + ".jpg"
        category, product = parse_name(image_name)
        if category is None:
            continue
        candidates[category].setdefault(product, []).append(image_name)

    rng = random.Random(seed)
    out_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for category, products in candidates.items():
        keys = sorted(products)
        rng.shuffle(keys)
        picked = 0
        for product in keys:
            if picked == per_category:
                break
            image_name = rng.choice(sorted(products[product]))
            stem = image_name.removesuffix(".jpg")
            mask = np.array(Image.open(io.BytesIO(segm.read(f"segm/{stem}_segm.png"))))
            box = crop_box(mask, MASK_LABELS[category])
            if box is None:
                continue
            image = Image.open(io.BytesIO(images.read(f"images/{image_name}"))).convert("RGB")
            image.crop(box).save(out_dir / image_name, quality=95)
            rows.append(
                {"file": image_name, "category": category, "color": "", "source": "deepfashion"}
            )
            picked += 1
        print(f"{category}: {picked}장 (후보 상품 {len(keys)}개)")
        if picked < per_category:
            sys.exit(f"{category} 종류가 {per_category}장보다 적다")

    with open(out_dir / "labels.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["file", "category", "color", "source"])
        writer.writeheader()
        writer.writerows(rows)
    print(f"저장: {out_dir} ({len(rows)}장)")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--per-category", type=int, default=25)
    parser.add_argument("--seed", type=int, default=20261107)
    args = parser.parse_args()
    build(args.out, args.per_category, args.seed)


if __name__ == "__main__":
    main()
