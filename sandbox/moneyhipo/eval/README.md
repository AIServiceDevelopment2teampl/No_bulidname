# 기능 1 종류 분류 평가 (moneyhipo, 검수 전)

기능 1 완료 조건 `[평가] 라벨링한 의류 100장으로 종류 분류 정확도` 를 재는 도구.
평가 사진은 두 묶음이고, 각각 따로 재고 합쳐서도 잰다 (사용자 결정 2026-11-07).

| 묶음 | 폴더 (Git에 안 올림) | 장수 | 만드는 법 |
|---|---|---|---|
| DeepFashion | `DeepFashin_dataset/eval/deepfashion_100/` | 종류마다 25장 = 100장 | `deepfashion_set.py`가 자동으로 뽑는다 |
| 실제 옷장 사진 | `DeepFashin_dataset/eval/closet/` | 종류마다 같은 수로 | 팀이 직접 찍고 `labels.csv`를 쓴다 (아래) |

## 종류 기준

`server/domain.py`의 `top / bottom / outer / other`.

- 상의: 티셔츠, 셔츠·블라우스, 니트, 맨투맨, **지퍼 없는 후드티**
- 아우터: 재킷, 코트, 조끼, **가디건, 후드 집업**
- 하의: 바지, 반바지, 치마, 레깅스
- 기타: 원피스, 점프수트, 그 밖의 것

DeepFashion은 파일 이름의 상품 종류로 정답을 정한다. `Sweatshirts_Hoodies`(풀오버·집업 섞임),
`Denim`(청바지·청재킷 섞임), `Suiting`은 이름만으로 못 가려서 뺐다.
모델이 입은 전신 사진이라, 사람이 칠한 부위 마스크로 해당 옷 부분만 잘라 "사진 1장에 옷 1벌"로 만든다.
DeepFashion에는 색 정답이 없다 (README의 color 라벨은 실제로는 무늬). 색은 옷장 사진에서만 잰다.

## 실제 옷장 사진 넣는 법

1. `DeepFashin_dataset/eval/closet/`에 사진을 넣는다 (한 장에 옷 한 벌, JPG·PNG).
2. 같은 폴더에 `labels.csv`를 UTF-8로 만든다. 색은 `server/domain.py`의 `COLORS` 코드 (`black`, `navy`, `multi` 등).

```csv
file,category,color,source
IMG_0001.jpg,top,white,closet
IMG_0002.jpg,outer,black,closet
```

## 실행 (저장소 루트에서)

```
python -m venv .venv
.venv/Scripts/python -m pip install torch==2.15.0 --index-url https://download.pytorch.org/whl/cpu
.venv/Scripts/python -m pip install -r sandbox/mmminsung02/src/server/requirements.txt -r sandbox/mmminsung02/src/server/requirements-ml.txt -r src/server/requirements-dev.txt
.venv/Scripts/python sandbox/moneyhipo/eval/deepfashion_set.py
.venv/Scripts/python sandbox/moneyhipo/eval/eval_category.py DeepFashin_dataset/eval/deepfashion_100 DeepFashin_dataset/eval/closet
.venv/Scripts/python -m pytest sandbox/moneyhipo/eval -q
```

## 라이선스

DeepFashion-MultiModal은 비상업 연구용이고 원본·파생 데이터의 복사·배포가 금지다.
잘라 낸 사진과 `labels.csv`는 Git·공개 드라이브에 올리지 않는다 (`DeepFashin_dataset/`는 `.gitignore` 대상).
평가에만 쓰고, 이 데이터로 학습한 모델을 앱·서버에 넣지 않는다.
