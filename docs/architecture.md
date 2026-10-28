# 아키텍처

## 전체 구조
```text
┌──────────────────────── 안드로이드 앱 (Kotlin + Jetpack Compose) ────────────────────────┐
│  홈 / 내 옷장 / 번역(쇼핑) / 마이                                                        │
│                                                                                          │
│  인앱 브라우저 (Android WebView)                 공유 받기 (ACTION_SEND: 링크·이미지)    │
│   └ 주입 스크립트 (src/inject)                    AI 점원 패널                           │
│      ├ translate.js : 화면 글자 수집 → 번역문으로 치환                                   │
│      ├ product.js   : 상품 페이지 감지·데이터 수집 (JSON-LD, 본문 글자)                  │
│      └ order.js     : 주문 완료 페이지 감지·주문 상품 수집                               │
│   (JS ↔ 앱: addJavascriptInterface / evaluateJavascript)                                │
│                                                                                          │
│  Room: 옷장 목록·번역 결과 캐시                                                          │
└──────────────────────────────────────┬───────────────────────────────────────────────────┘
                                       │ Retrofit, REST(JSON), HTTPS
┌──────────────────────────────────────┴──── AI 서버 (Python + FastAPI) ─────────────────────┐
│  API: 옷장 · 번역 · 상품 추출 · 유사 검색 · 비교 리포트 · AI 점원 · 자동 등록            │
│  AI 모듈: CLIP(임베딩·분류) · OCR · LLM API · 단위 변환                                   │
│  저장: SQLite(메타데이터) + FAISS(임베딩 검색) + 이미지 파일                              │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

## 핵심 흐름
**1. 페이지 번역**
인앱 브라우저가 쇼핑몰을 연다 → `translate.js`가 화면에 보이는 글자를 모은다 → 앱이 서버 `/translate`로 보낸다 → 서버가 캐시를 확인하고 없는 문장만 번역한 뒤 단위 변환을 붙인다 → 스크립트가 글자를 그 자리에서 바꾼다.

**2. 상품 비교**
`product.js`가 상품 페이지를 알아챈다(JSON-LD `Product` 등) → 서버 `/products/extract`가 상품 정보를 구조화한다 → `/similar`가 상품 이미지를 CLIP으로 임베딩해 같은 종류 안에서 TOP 3를 찾는다 → `/report`가 비교 리포트를 만든다 → AI 점원 패널에 표시한다.
공유받은 스크린샷도 OCR을 거쳐 같은 경로(`/products/extract`부터)를 탄다.

**3. 자동 등록**
주문내역 캡처·영수증(OCR) 또는 `order.js`(주문 완료 페이지)에서 상품 후보를 뽑는다 → 사용자 확인 화면 → 확인한 것만 옷장에 저장한다. 사용자 확인 없이 저장하지 않는다.

## 기술 스택
| 구성 | 선택 | 상태 |
|------|------|------|
| 앱 | Kotlin + Jetpack Compose, Android Studio | 확정 (제출 기획서) |
| targetSdk | 36 (Android 16) 이상 | 확정 (2026-08-31부터 신규 앱 필수) |
| minSdk | - | 미결정 |
| 앱-서버 통신 | Retrofit, REST(JSON) | 확정 |
| 앱 로컬 저장 | Room | 확정 |
| 서버 | Python + FastAPI | 확정 |
| 이미지 임베딩·분류 | CLIP (사전학습 모델, 미세조정 없음). 모델 종류는 평가 후 결정 (후보: 일반 CLIP, 패션 특화 CLIP) | 모델 미결정 |
| 유사 검색 | FAISS | 확정 (제출 기획서) |
| OCR | EasyOCR(서버) 또는 Google ML Kit(기기 안) | 미결정 |
| 번역 | LLM API로 전부 하거나, 페이지 문장은 번역 API로 하고 상품 구조화만 LLM으로 | 미결정 |
| LLM API | Claude API, 우선 Claude Haiku 5.5 (`claude-haiku-5-5`). 공식 `anthropic` Python SDK. 키는 서버에만 둔다 | 확정 (모델은 AI 점원 품질 보고 바꿀 수 있음) |
| DB | 서버 SQLite | 확정 |
| 서버 배포 | AWS Lightsail 서울 리전 (CLIP 실행에 메모리 필요, 4GB 요금제 예정) | 확정 (요금제는 메모리 측정 후) |
| 앱 배포 | Google Play (비공개 테스트 → 프로덕션) | 확정 |
| 테스트 | 서버 pytest, 주입 JS는 Node + jsdom, 앱은 JUnit·Compose UI 테스트 | 확정 (초안) |

## 폴더 규칙
```text
.
├── CLAUDE.md
├── docs/
│   ├── features/          # 기능별 문서 (종류별 하위 폴더, features/README.md 참고)
│   └── image/             # UI 시안
├── src/
│   ├── android/           # 안드로이드 스튜디오 프로젝트 (Gradle 루트)
│   ├── inject/            # WebView 주입 JS (빌드 때 앱 assets로 복사)
│   └── server/            # FastAPI 서버
│       ├── api/           # 라우터
│       ├── ai/            # clip, ocr, llm, units
│       ├── services/      # 기능 로직
│       └── db/            # 모델, 마이그레이션
└── tests/
    ├── server/            # pytest
    ├── inject/            # Node + jsdom
    ├── fixtures/          # 고정 HTML 페이지, 테스트 이미지, 캡처
    └── eval/              # 정확도 평가 스크립트와 라벨 데이터 (통과 조건 아님, 결과를 progress.md에 기록)
```
- 안드로이드 테스트 위치는 확인 필요. Gradle 관례(`src/android/app/src/test`, `androidTest`)를 따르면 CLAUDE.md의 "테스트는 `tests/`" 규칙과 다르다.
- 단위 테스트에서 LLM·OCR·CLIP 호출은 가짜(mock)로 바꾼다. 실제 모델 성능은 `tests/eval/`에서 따로 잰다.

## 서버 API (초안)
모든 요청에 `X-Device-Id` 헤더를 붙인다 (계정 방식이 정해지면 바꾼다).

| 메서드 | 경로 | 기능 |
|--------|------|------|
| POST | `/v1/clothes` | 사진 업로드 → 자동 분류 결과와 함께 `미확인` 상태로 생성 |
| GET | `/v1/clothes?category=&color=` | 옷장 목록·필터 |
| PATCH | `/v1/clothes/{id}` | 속성 수정·확인 |
| DELETE | `/v1/clothes/{id}` | 삭제 (이미지·임베딩 포함) |
| DELETE | `/v1/me/data` | 내 데이터 전체 삭제 |
| POST | `/v1/translate` | 문장 배열 번역 + 단위 변환 |
| POST | `/v1/products/extract` | 페이지 데이터 또는 스크린샷 → 상품 정보 |
| POST | `/v1/products/{id}/similar` | 유사 의류 TOP 3 |
| POST | `/v1/products/{id}/report` | 비교 리포트 |
| POST | `/v1/clerk/messages` | AI 점원 대화 |
| POST | `/v1/imports` | 주문내역 캡처·영수증·앱 내 구매 → 등록 후보 |
| POST | `/v1/imports/{id}/confirm` | 사용자가 고른 후보만 옷장에 저장 |
| GET | `/v1/shops` | 쇼핑몰 목록 (서버 설정 파일) |

## 데이터 / DB 설계
**users**
| 필드 | 타입 | 설명 |
|------|------|------|
| id | TEXT PK | 기기 단위 익명 ID (초안) |
| created_at | DATETIME | |

**clothes** (내 옷장)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | INTEGER PK | |
| user_id | FK users | |
| image_path | TEXT NULL | 영수증 등록은 사진이 없을 수 있음 |
| image_hash | TEXT NULL | 같은 사진 중복 확인 |
| category | TEXT | `top` / `bottom` / `outer` / `other` |
| color | TEXT | 정해진 색 목록 중 하나 (아래) |
| name, brand, material, size | TEXT NULL | 모르면 NULL (= 정보 없음) |
| source | TEXT | `photo` / `order_capture` / `in_app` / `receipt` |
| source_ref | TEXT NULL | 주문번호 등, 같은 주문 중복 등록 방지 |
| purchased_at | DATE NULL | |
| confirmed | BOOL | 사용자가 분류를 확인했는지 |
| created_at, updated_at | DATETIME | |

**clothes_embeddings**: cloth_id(PK, FK), model(TEXT), vector(BLOB). 사진 없는 옷은 행이 없고 유사 검색에서 빠진다. FAISS 인덱스는 이 표로 다시 만들 수 있다.

**products** (분석한 해외 상품)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | INTEGER PK | |
| user_id | FK users | |
| source | TEXT | `page` (인앱 브라우저) / `screenshot` (공유) |
| site, url | TEXT NULL | |
| image_url / image_path | TEXT NULL | |
| category | TEXT NULL | |
| fields | JSON | 필드마다 `{ko, original}`. 원문에 없으면 `null` |
| lang | TEXT | 원문 언어 |
| created_at | DATETIME | |

**reports**: id, product_id, similar(JSON: cloth_id·점수), body(JSON: `similar[]`, `different[]`, `unknown[]`), created_at
**clerk_messages**: id, user_id, product_id NULL, role, content, created_at
**imports**: id, user_id, type(`order_capture`/`receipt`/`in_app`), image_path NULL, status(`pending`/`confirmed`/`discarded`), candidates(JSON), created_at
**translation_cache**: key(PK: 원문+대상 언어 해시), src_lang, text_ko, created_at

**색 목록 (초안):** 블랙, 화이트, 그레이, 네이비, 블루, 베이지, 브라운, 카키, 그린, 레드, 핑크, 퍼플, 옐로, 오렌지, 멀티(패턴)

## 외부 의존성
| 이름 | 용도 | 비고 |
|------|------|------|
| CLIP 사전학습 모델 | 이미지 임베딩, 종류·색 분류 | 라이선스·출처 표기 |
| FAISS | 벡터 검색 | |
| EasyOCR 또는 ML Kit | 캡처·영수증 글자 인식 | 미결정 |
| Claude API (Anthropic) | 번역, 구조화, 리포트, 점원 | 선불 크레딧, 학교 지원금. 사용료 발생 |
| AWS Lightsail | AI 서버 | 월 고정 요금, 학교 지원금 |
| Google Play Console | 앱 배포 | 개인 개발자 계정 등록비 있음 |

## 보안·개인정보 규칙
- 주입 스크립트는 `input`, `textarea`, 비밀번호 칸의 값을 읽지 않는다.
- 로그인·결제 페이지(URL 패턴 목록)에서는 번역·수집을 하지 않는다. 주문 완료 페이지에서는 상품 영역만 읽고 이름·주소·연락처·결제수단은 서버로 보내지 않는다.
- 쇼핑몰 쿠키·로그인 정보는 WebView 안에만 있고 서버로 보내지 않는다.
- 사용자는 옷 하나, 또는 내 데이터 전체를 지울 수 있다. 지우면 이미지 파일과 임베딩도 지운다.
- 이 내용은 개인정보처리방침과 Play 데이터 보안 양식에 그대로 적는다.

## 주요 위험
| 위험 | 영향 | 대응 |
|------|------|------|
| **Play 스팸 정책 (WebView).** 남의 웹사이트를 허락 없이 WebView로 보여 주는 것이 주목적인 앱은 금지되고, "다른 회사 쇼핑몰을 감싼 앱"이 위반 예시로 나온다 | 심사 거절, 계정 경고 | 앱의 주목적이 옷장·비교임을 화면과 스토어 설명에 분명히 한다. 특정 쇼핑몰 전용이 아닌 일반 브라우저처럼 주소 입력을 지원한다. 거절되면 공유하기·스크린샷 경로로 출시한다 |
| 쇼핑몰 로고 사용 | 지식재산권 정책 위반 가능 | 쇼핑몰 목록은 로고 대신 텍스트 이름으로 보여 준다 (초안, UI 시안과 다름) |
| 비공개 테스트 14일 | 출시 일정 지연 | 11주차 안에 시작한다. 테스터 12명은 미리 모은다 |
| WebView에서 일부 사이트 로그인 차단 (예: Google 계정 로그인) | 앱 안에서 결제 불가 | 해당 사이트는 외부 브라우저로 열기 버튼을 둔다 |
| 사이트 구조 변경·봇 차단 | 상품·주문 감지 실패 | JSON-LD 같은 표준 데이터를 먼저 쓰고 사이트별 규칙은 최소로 둔다 |
| 번역 비용·지연 | 느림, 예산 초과 | 화면에 보이는 부분부터 번역하고 캐시한다 |
| 사이즈 환산 오차 | 잘못된 구매 | 원문을 병기하고 '참고용'으로 표시한다. 페이지의 브랜드 사이즈표를 우선한다 |
| 서버 메모리 (CLIP) | 무료 호스팅으로는 부족할 수 있음 | 호스팅을 정하기 전에 메모리 사용량을 잰다 |
