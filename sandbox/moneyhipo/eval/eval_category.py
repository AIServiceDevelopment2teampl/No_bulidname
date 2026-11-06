"""라벨 붙인 옷 사진으로 CLIP 종류(·색) 분류 정확도를 잰다.

평가 폴더마다 labels.csv(열: file, category, color, source)가 있어야 한다. color는 비워 두면
색 정확도 계산에서 빠진다. 여러 폴더를 주면 폴더별·source별과 전체를 함께 보여 준다.

서버와 같은 규칙으로 최종 종류를 정한다: 신뢰도가 0.5 미만이면 other
(sandbox/mmminsung02/src/server/config.py min_category_confidence).

실행 (저장소 루트에서):
  .venv/Scripts/python sandbox/moneyhipo/eval/eval_category.py \
      DeepFashin_dataset/eval/deepfashion_100 DeepFashin_dataset/eval/closet
"""

import argparse
import csv
import sys
import time
from collections import Counter
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
# 분류기는 기능 1 서버 코드를 그대로 쓴다 (고치지 않고 읽기만 함)
sys.path.insert(0, str(ROOT / "sandbox" / "mmminsung02" / "src"))

from server.ai.clip_analyzer import DEFAULT_MODEL, ClipAnalyzer  # noqa: E402
from server.domain import CATEGORIES, COLORS  # noqa: E402

MIN_CONFIDENCE = 0.5

# 시험용 종류 문장 (서버 코드는 고치지 않고 평가에서만 바꿔 본다). 종류 기준은
# progress.md 결정 사항 2026-11-07: 가디건·후드 집업은 아우터, 지퍼 없는 후드티는 상의
PROMPTS_V2 = {
    "top": [
        "a t-shirt",
        "a shirt",
        "a blouse",
        "a sweater",
        "a tank top",
        "a sweatshirt",
        "a pullover hoodie",
        "a polo shirt",
    ],
    "bottom": ["pants", "jeans", "shorts", "a skirt", "leggings"],
    "outer": [
        "a jacket",
        "a coat",
        "a cardigan",
        "a zip-up hoodie",
        "a vest",
        "a blazer",
        "a padded jacket",
    ],
    "other": ["a dress", "a jumpsuit", "a romper", "shoes", "a bag", "a hat"],
}


def use_prompts(analyzer: ClipAnalyzer, variant: str) -> None:
    """analyzer의 종류 문장을 바꾼다.

    server: 서버 코드 그대로 (문장별 확률을 종류별로 더함)
    v2: PROMPTS_V2, 서버처럼 확률을 더함 (문장이 많은 종류가 유리해질 수 있음)
    v2-mean: PROMPTS_V2, 종류마다 문장 벡터를 평균 내 종류 하나에 벡터 하나
    """
    if variant == "server":
        return
    texts = {c: [f"a photo of {p}" for p in ps] for c, ps in PROMPTS_V2.items()}
    if variant == "v2":
        analyzer._category_labels = [c for c, ps in texts.items() for _ in ps]
        analyzer._category_text = analyzer._encode_text([t for ts in texts.values() for t in ts])
    elif variant == "v2-mean":
        means = [analyzer._encode_text(ts).mean(dim=0) for ts in texts.values()]
        stacked = analyzer._torch.stack(means)
        analyzer._category_labels = list(texts)
        analyzer._category_text = stacked / stacked.norm(dim=-1, keepdim=True)
    else:
        sys.exit(f"모르는 문장 묶음: {variant}")


def read_labels(set_dir: Path) -> list[dict]:
    with open(set_dir / "labels.csv", encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    for row in rows:
        if row["category"] not in CATEGORIES:
            sys.exit(f"{set_dir}: {row['file']}의 종류 '{row['category']}'가 목록에 없다")
        if row.get("color") and row["color"] not in COLORS:
            sys.exit(f"{set_dir}: {row['file']}의 색 '{row['color']}'가 목록에 없다")
        row["path"] = set_dir / row["file"]
        row["source"] = row.get("source") or set_dir.name
    return rows


def final_category(category: str, confidence: float) -> str:
    return category if confidence >= MIN_CONFIDENCE else "other"


def accuracy(pairs: list[tuple[str, str]]) -> str:
    if not pairs:
        return "-"
    hit = sum(1 for want, got in pairs if want == got)
    return f"{hit}/{len(pairs)} = {hit / len(pairs):.1%}"


def report(results: list[dict], model_name: str, prompts: str) -> str:
    lines = [
        f"모델: {model_name}, 종류 문장: {prompts}, 신뢰도 기준 {MIN_CONFIDENCE} 미만 → other",
        "",
    ]
    groups = {"전체": results}
    for source in sorted({r["source"] for r in results}):
        groups[source] = [r for r in results if r["source"] == source]

    lines.append("| 묶음 | 장수 | 종류 정확도 (서버 규칙) | 종류 정확도 (기준 없이) | 색 정확도 |")
    lines.append("|---|---|---|---|---|")
    for name, rows in groups.items():
        cat = accuracy([(r["category"], r["final"]) for r in rows])
        raw = accuracy([(r["category"], r["raw"]) for r in rows])
        color = accuracy([(r["color"], r["got_color"]) for r in rows if r.get("color")])
        lines.append(f"| {name} | {len(rows)} | {cat} | {raw} | {color} |")

    lines += ["", "혼동 행렬 (전체, 서버 규칙. 행=정답, 열=예측)", ""]
    lines.append("| 정답 \\ 예측 | " + " | ".join(CATEGORIES) + " |")
    lines.append("|---" * (len(CATEGORIES) + 1) + "|")
    confusion = Counter((r["category"], r["final"]) for r in results)
    for want in CATEGORIES:
        cells = " | ".join(str(confusion[(want, got)]) for got in CATEGORIES)
        lines.append(f"| {want} | {cells} |")

    wrong = [r for r in results if r["category"] != r["final"]]
    lines += ["", f"틀린 사진 {len(wrong)}장 (파일, 정답 → 예측, 신뢰도)", ""]
    for r in wrong:
        lines.append(
            f"- {r['source']}/{r['file']}: {r['category']} → {r['final']} "
            f"({r['raw']} {r['confidence']:.2f})"
        )
    return "\n".join(lines)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("sets", nargs="+", type=Path, help="labels.csv가 있는 폴더")
    parser.add_argument("--out", type=Path, help="결과를 이 파일(.md)에도 쓴다")
    parser.add_argument(
        "--model", default=DEFAULT_MODEL, help="Hugging Face CLIP 모델 이름 (모델 비교용)"
    )
    parser.add_argument(
        "--prompts", default="server", choices=["server", "v2", "v2-mean"], help="종류 문장 묶음"
    )
    args = parser.parse_args()

    rows = [row for set_dir in args.sets for row in read_labels(set_dir)]
    load_start = time.perf_counter()
    analyzer = ClipAnalyzer(args.model)
    use_prompts(analyzer, args.prompts)
    print(f"모델 로드 {time.perf_counter() - load_start:.1f}초")
    start = time.perf_counter()
    for row in rows:
        with Image.open(row["path"]) as image:
            analysis = analyzer.analyze(image)
        row["raw"] = analysis.category
        row["confidence"] = analysis.category_confidence
        row["final"] = final_category(analysis.category, analysis.category_confidence)
        row["got_color"] = analysis.color
    print(f"{len(rows)}장 분석, 장당 {(time.perf_counter() - start) / len(rows) * 1000:.0f}ms\n")

    text = report(rows, args.model, args.prompts)
    print(text)
    if args.out:
        args.out.write_text(text + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
