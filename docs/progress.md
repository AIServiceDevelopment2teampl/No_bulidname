# 진행 상황

## 현재 상태
- 개발용 기획 문서 작성 완료 (00-overview, architecture, features 14개). 코드는 아직 없음
- 지금은 8주차(1차 Sprint)로 추정: UI/UX 설계, 안드로이드 프로젝트 구성, 기술 검증 단계

## 작업 로그
| 날짜 | 작업 | 비고 |
|------|------|------|
| 2026-11-06 | DeepFashion-MultiModal 데이터 아카이브 로컬 확보 | `DeepFashin_dataset/`에 압축 파일을 보관하며 Git에는 올리지 않음. [출처](https://github.com/yumingj/DeepFashion-MultiModal). 저장소 README·LICENSE는 비상업 연구용으로 명시하고 재배포를 제한함. 분류 학습·평가에 쓸 수 있는지 라이선스와 라벨 적합성을 먼저 확인해야 함 |
| 2026-11-07 | DeepFashion 적합성 확인 + **기능 1 종류 분류 평가 (DeepFashion 100장)** (`sandbox/moneyhipo/eval/`, 검수 전) | **결과: 86/100 = 86.0%** (목표 85%, 서버 규칙 = 신뢰도 0.5 미만 `other`). 기준 없이 87%. 종류별: top 23/25, bottom 22/25, outer 21/25, other 20/25. 틀린 14장 중 가디건 3장(outer → top·other), 원피스·점프수트 5장(other → bottom·top·outer). 장당 98ms(이 PC, CPU). 모델 `openai/clip-vit-base-patch32`, mmminsung02 `ClipAnalyzer` 그대로. **데이터 확인 결과**: 쇼핑몰 상품 사진 44,096장(대부분 모델 착용, 옷만 찍은 사진 69장), 종류 정답은 파일 이름의 상품 종류, 전신 12,701장에 사람이 칠한 부위 마스크가 있음. **색 정답은 없음**(README의 color 라벨은 실제로는 무늬). 그래서 마스크가 있는 사진에서 해당 옷 부분만 잘라 종류마다 25장(상품 하나에 한 장, seed 20261107)을 뽑음. 풀오버·집업이 섞인 `Sweatshirts_Hoodies`, 청바지·청재킷이 섞인 `Denim`, `Suiting`은 뺌. 잘린 사진·라벨·결과는 `DeepFashin_dataset/eval/`(Git 제외). 실행: 저장소 루트에서 `.venv/Scripts/python sandbox/moneyhipo/eval/deepfashion_set.py` → `eval_category.py DeepFashin_dataset/eval/deepfashion_100`. 단위 테스트 4개 통과(`pytest sandbox/moneyhipo/eval`), ruff 통과. **남은 것**: 실제 옷장 사진 묶음(색 포함)을 찍어 따로·합쳐서 재기. 쇼핑몰 스튜디오 사진이라 실제 옷장 사진보다 쉬울 수 있다. **→ 바로 아래 줄: 400장으로 다시 재니 지금 서버 설정은 79.8%로 목표 미달** |
| 2026-11-07 | 기능 5 단위·사이즈 변환 (`sandbox/moneyhipo/units/`, 검수 전) | 완료 조건 10개 + 예외·형식 테스트, 모두 31개 통과(`.venv/Scripts/python -m pytest sandbox/moneyhipo/units -q`), ruff 통과. `convert_text`: 글 속 in·inch·`"`·ft·lb·oz를 찾아 `81.3 cm (32 in)`처럼 바꾼다. 범위(`30-32`, `~`, `to`), 분수(`32 1/4`), 키(`5'10"`, `5 ft 7 in`)도 처리한다. Decimal 사사오입: cm 소수 첫째, kg 둘째, g 정수. **구현 때 정한 것**: ① 괄호 안 원문은 쓰인 그대로 둔다(`32"` → `81.3 cm (32")`) ② `in`은 전치사로 더 많이 쓰여서(`3 in stock`, `2 in 1`), 뒤가 글 끝·문장부호·치수 단어(long, waist, inseam, x 등)일 때만 인치로 본다. `inch`·`inches`·`"`는 늘 인치 ③ 쉼표 천 단위(`1,500 lb`), `fl oz`(부피), 모르는 단위는 그대로 둔다 ④ 사이즈는 글에서 찾지 않고 `convert_shoe_size(system, size, gender)`·`convert_clothing_size(...)`로 받는다. 성별·체계는 기능 7(상품 정보 구조화)이 넘긴다. 성별을 모르거나 표에 없으면 None, 결과에는 늘 `참고용`. **사이즈 환산표 `size_tables.json`은 초안**: 흔히 쓰는 해외직구 환산 값(신발은 Nike 표에 가까운 값, 의류는 상의 기준, 남녀 따로)이고 출처 확인 전이다. 의류 하의 표는 없다 |
| 2026-11-08 | **기능 1 평가: 실제 옷장 사진 29장** (moneyhipo가 찍음, `sandbox/moneyhipo/eval/`) | 바닥에 옷걸이째 펼치거나 접어 놓고 폰으로 찍은 사진. 라벨은 AI가 사진을 보고 붙임(종류 기준 2026-11-07, 셔츠 2장은 `겉옷` 폴더에 있었지만 기준대로 `top`, 양말은 `other`). 종류별 장수: top 6, bottom 8, outer 10, other(양말) 5. **결과(서버 규칙)**: 지금 서버 설정(B/32 + 서버 문장) **종류 24/29 = 82.8%, 색 18/29 = 62.1%** / FashionCLIP + v2 문장 **종류 22/29 = 75.9%, 색 14/29 = 48.3%**. 종류마다 같은 비중으로 평균 내면 80.6% / 72.1%. **DeepFashion 400장과 순위가 반대**다(그쪽은 FashionCLIP + v2가 88.0%로 앞섬). 다만 29장이라 한 장이 3.4%p라서 아직 어느 쪽으로도 정할 수 없다. 틀린 곳: B/32는 바닥에 놓은 바지 3장 → outer, 양말 2장 → bottom. FashionCLIP + v2는 양말 3장 → top·bottom(v2 기타 문장에 양말이 없음), 접은 티셔츠 → bottom, 회색 집업 재킷 → top. **색**: 대부분 검정 → 남색·회색(나무 바닥 위 어두운 옷)이다. 라벨이 애매한 사진도 있다(가죽 재킷 갈색/검정, 생지 데님 재킷 남색/파랑, 연한 셔츠 베이지/회색/흰색). 사진 18장에 EXIF 회전값이 있는데 서버(`_open_image`)도 평가도 회전하지 않는다. 회전시켜 다시 재도 종류·색이 한 장씩만 바뀌어서 결론에는 영향 없다. 사진 원본은 `옷 사진/`, 평가용 사본·`labels.csv`는 `DeepFashin_dataset/eval/closet/`(둘 다 Git 제외, 개인 사진) |
| 2026-11-07 | **CLIP 모델·종류 문장 비교** (DeepFashion 400장 + 100장, `sandbox/moneyhipo/eval/`) | 100장은 오차가 커서 종류마다 100장씩 400장 묶음(seed 400, 100장 묶음과 상품 8개 겹침)을 더 만듦. 400장 기준 종류 정확도(서버 규칙): **지금 서버 설정(일반 CLIP B/32 + 서버 문장) 79.8%** / FashionCLIP + 서버 문장 77.2% / CLIP B/16 + 서버 문장 82.2% / B/32 + v2 문장 84.0% / **FashionCLIP + v2 문장 88.0%** (100장 91%, 500장 전체 88.6%) / B/16 + v2 문장 86.8%. 문장 벡터를 종류별로 평균 내는 방식(v2-mean)은 더 나빴다(80.0~85.2%). v2 문장: 종류 기준대로 상의 8개(후드티·맨투맨 포함)·하의 5개·아우터 7개(가디건·후드 집업 포함)·기타 6개(원피스·점프수트 등), `eval_category.py` `PROMPTS_V2`. 속도(이 PC CPU, 장당): B/32 90ms, FashionCLIP 81ms, B/16 217ms. FashionCLIP(`patrickjohncyh/fashion-clip`)은 B/32와 같은 구조·크기라 메모리도 같고 라이선스 MIT. FashionCLIP + v2의 남은 오류는 원피스 → 하의 20장이 가장 많다. **주의**: v2 문장은 100장 결과를 본 뒤 만들었으므로, 최종 확인은 실제 옷장 사진으로 한다. 실행: `eval_category.py <묶음들> --model patrickjohncyh/fashion-clip --prompts v2` |
| 2026-10-07 | 프로젝트 뼈대 생성 | CLAUDE.md, docs/ 템플릿 |
| 2026-10-14 | 팀 프로젝트 기획서 제출 | `팀플기획서-12조 (1).pdf` |
| 2026-10-21 | 개발용 기획 문서 작성 | 아이디어_상세.md, 추가 idea.txt, 제출 기획서, UI 시안을 합쳐 정리. 원본은 수정하지 않음 |
| 2026-10-21 | 기능 문서를 종류별 하위 폴더로 분리 | wardrobe, auto-register, translation, shopping, compare, release |
| 2026-10-28 | 쇼핑몰 상품 페이지 확인 (curl, 모바일 UA) | ASOS·ZOZOTOWN: 403 봇 차단(ZOZOTOWN은 Akamai). 유니클로 일본: 받아짐. JSON-LD는 없지만 페이지 데이터(`window.__PRELOADED_STATE__`)에 소재(`composition`)·세탁(`washingInformation`)·핏 정보가 있고 `og:title`·`og:image`도 있음. 서버가 쇼핑몰 페이지를 직접 가져오는 방식은 막히므로 WebView 주입 스크립트로 읽는 설계가 맞다 |
| 2026-10-28 | ZOZOTOWN 상품 페이지 확인 (`docs/site_html/zozotown.html`, 개발자 도구 콘솔에서 `copy(document.documentElement.outerHTML)`로 복사. "웹페이지, 전체" 저장은 실패하며 파일이 지워짐) | **JSON-LD `Product` 있음**, 색상별로 하나씩: 상품명, 이미지, 브랜드, 색, 가격. 소재·세탁·두께·신축성은 `description` 안의 일본어 자유 글(`【Material】表地：...手洗い可`)에 섞여 있어 LLM으로 정리해야 함. 페이지 본문 `素材` 항목에도 소재가 있음 |
| 2026-10-28 | 사용자가 쇼핑몰을 직접 추가하는 기능을 넣기로 함 | [shop-browser.md](features/shopping/shop-browser.md)에 요구사항 추가 |
| 2026-10-28 | 브라우저로 저장한 ASOS 상품 페이지 확인 (`docs/site_html/`) | **JSON-LD `Product` 있음**: 상품명, 색, 이미지, 브랜드, 설명(핏 포함, 예: "Regular fit"), 가격·통화(USD). 소재·세탁·모델 키는 `window.asos.pdp.config` 안 `aboutMe`·`careInfo`·`sizeAndFit`에 있음. ZOZOTOWN은 저장이 끝나지 않아 다시 해야 함 (Ctrl+S에서 "웹페이지, HTML만"으로 저장 권장). 저장한 페이지의 부속 파일 폴더(`*_files/`)는 `.gitignore`에 넣고 HTML만 올림 |
| 2026-10-28 | 서버 뼈대·테스트 환경 생성 | `src/server/`(FastAPI, `/health`), `tests/server/`(pytest), `tests/inject/`(Node 내장 테스트 + jsdom), ruff 설정(`pyproject.toml`). 실행: `python -m venv .venv` → `.venv/Scripts/pip install -r src/server/requirements-dev.txt` → `.venv/Scripts/python -m pytest`, `npm install` → `npm run test:inject`. 안드로이드 프로젝트는 이 PC에 Android Studio가 없어 아직 못 만듦 |
| 2026-11-04 | 안드로이드 프로젝트 뼈대 생성 (`sandbox/mmminsung02/android/`, 검수 전) | AGP 9.4.0 (설치된 Android Studio 2026.2.1이 지원하는 최신. 9.5.0은 동기화 실패), Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 36, minSdk 26, ktlint. 하단 탭 4개(홈/내 옷장/번역/마이) 빈 화면. Robolectric 테스트 3개(탭 표시, 탭 전환, targetSdk 36 이상) 통과, 디버그 APK 빌드 성공. 실제 폰 실행은 미확인. 실행: `cd sandbox/mmminsung02/android` → `./gradlew ktlintCheck testDebugUnitTest assembleDebug` (JAVA_HOME은 Android Studio의 `jbr`). JDK 25에서 Robolectric을 돌리려고 테스트 JVM에 `--add-opens java.io`, `--add-exports jdk.internal.access`를 넣음. applicationId는 임시 `com.team12.closet` |
| 2026-11-04 | 화면 시안 PNG 3장을 HTML 목업으로 옮김 (`sandbox/moneyhipo/mockup/index.html`, 검수 전) | 화면 6개: 로그인(Google), 로그아웃 확인, 비로그인 홈, 로그인 후 메인(프로필 메뉴), 홈 베이스, 홈 초안. 버튼을 누르면 이어지는 화면으로 이동. 옷 사진·로봇 그림은 이모지·도형으로 대신함. 색은 `:root`에 모아 둠(주 색 `#5B5CEB`), 앱 테마를 만들 때 쓴다. 시안과 문서가 어긋난 4곳은 아래 결정 사항대로 고침 |
| 2026-11-04 | 기능 1 옷 사진 등록·자동 분류 **서버** (`sandbox/mmminsung02/`, 검수 전) | `POST /v1/clothes`. 완료 조건 [서버] 5개 + 예외 상황 5개 테스트 통과(가짜 분석기). 실제 CLIP(`openai/clip-vit-base-patch32`, 제로샷) 동작 테스트 2개 통과(`pytest -m slow`, 단색 이미지로 값 범위·차원만 확인, 정확도는 미측정). 실행: `cd sandbox/mmminsung02` → `../../.venv/Scripts/python -m pytest`. 구현 때 정한 값: 업로드 상한 10MB, 분류 신뢰도 0.5 미만이면 `other`, 요청 하나에 사진 1장, 색은 영어 코드로 저장(`server/domain.py`), 로그인 전까지 개발용 사용자 `dev-user`. transformers 5부터 `get_*_features`가 출력 객체를 돌려줘서 `.pooler_output`을 씀 |
| 2026-11-04 | CLIP 메모리·속도 측정 (이 PC, CPU) | 모델을 올린 Python 프로세스 약 500MB, 모델 로드 11초, 사진 1장 분석 약 180ms. 서버 전체로도 1GB 안쪽으로 예상되어 **Vultr 2GB 요금제로도 될 가능성이 높다** (FAISS·동시 요청을 붙인 뒤 다시 잰다) |
| 2026-11-04 | 목업에 라이트/다크 테마, 사이트맵, 화면 4개 추가 | 화면: AI 점원 패널, 내 옷장 여러 벌 고르기, 미리 입어보기, 마이(테마 선택). 색을 모두 테마 변수로 바꿈(다크 주 색 `#8E8FFF`). 헤드리스 Chrome으로 두 테마 다 찍어 확인. 미리 입어보기 기능 문서 [compare/virtual-fitting.md](features/compare/virtual-fitting.md) 추가 |
| 2026-11-04 | 기능 1 옷 사진 등록 **앱 화면** (`sandbox/mmminsung02/android/`, 검수 전) | 내 옷장 탭 → `사진으로 등록` → 시스템 사진 선택기(여러 장) → 한 장씩 업로드 → 결과 카드(종류·색, `미확인`, `종류를 골라 주세요`, `이미 등록된 사진이에요`, 오류 메시지, 연결 실패 시 `다시 시도`). Retrofit + kotlinx.serialization. 테스트 16개 통과: 화면 흐름 6개(서버는 가짜, 완료 조건 [앱] 포함), 서버 응답 읽기 6개(MockWebServer), 탭 4개. 서버 주소는 `-PserverBaseUrl=`로 넘김(기본값 에뮬레이터용 `http://10.0.2.2:8000/`), 디버그 빌드만 http 허용. **실제 폰에서 사진 선택기·업로드는 미확인** (팀원 폰이 이 PC 서버에 닿지 않음. Vultr 배포 후 확인). 분류 결과 고치기는 기능 2(수정 API)에서 붙인다 |
| 2026-11-04 | 실제 서버 + 실제 CLIP 연결 확인 (이 PC, curl) | uvicorn으로 sandbox 서버를 띄워 사진 업로드 → 201·분류 결과, 같은 사진 다시 → `duplicate: true`, 이미지 아닌 파일 → 400, 사진 파일 저장 확인. 단색 그림이라 종류 분류 결과는 의미 없음 (색 `navy`는 맞음) |
| 2026-11-04 | README.md에 "AI에게 요청" 추가, CLAUDE.md 규칙 0-1 추가 (moneyhipo) | 팀원 AI끼리 지킬 규칙(대화 기록은 내 세션 칸에만, 푸시 전 `pull --rebase`, 남의 sandbox 안 고침, 문서가 기준), 앱 화면 결정 요약, 지금 요청 표(탭 이름 `쇼핑`, 라이트/다크 테마, 평가 사진 100장) |
| 2026-11-04 | **실제 폰 설치·실행 확인** (moneyhipo 폰) | 이 노트북에서 디버그 APK 빌드(`2f09abb`, Android Studio jbr로 `./gradlew assembleDebug`, 약 2분) → 구글 드라이브로 옮겨 설치, 앱이 정상으로 열림. 사진 업로드는 서버 미배포라 아직 확인 못 함. 같은 PC에서 빌드한 APK는 지우지 않고 덮어써서 설치된다(PC가 다르면 디버그 서명이 달라 먼저 지워야 함). Expo Go는 React Native 전용이라 이 앱(Kotlin)에는 못 씀 |
| 2026-11-04 | 시험용 APK **v0.1.1** (`75fd9b8`) 드라이브에 올림 | 탭 `쇼핑`, 라이트/다크 테마(mmminsung02 작업) 포함. APK 이름 규칙: `closet-v0.1.n-<커밋>.apk`, 한 단계씩 올리고 예전 것은 드라이브 `old/`에 보관(첫 APK는 v0.1.0으로 이름을 바꿔 옮김). 앱 안 버전은 아직 0.1.0 고정이라 빌드 때 넘길 수 있게 README로 요청함 |
| 2026-11-04 | 시험용 APK **v0.1.2** (`0b240cd`, versionCode 3) 드라이브에 올림 | 기능 2 앱 화면(옷장 격자·필터·옷 상세 수정·삭제·전체 삭제) 포함. 이번부터 `-PversionCode=3 -PversionName=0.1.2`로 빌드해 앱 안 버전도 0.1.2 (aapt로 확인). 버전 코드 규칙: v0.1.n → versionCode n+1 |
| 2026-11-04 | 화면 시안을 `sandbox/moneyhipo/mockup/index.html` → **`docs/app-sitemap/index.html`**로 옮김 | 앱 화면의 기준 문서라서 sandbox(완성본 아님)가 아닌 docs에 둔다. CLAUDE.md 문서 안내에 "앱 화면은 이 시안을 보고 만든다"를 넣고 README 링크·요청 표를 고침. 위 로그의 옛 경로는 그대로 둠 |
| 2026-11-04 | 사이트맵 시안 다 채움 (`docs/app-sitemap/index.html`, 화면 19개) | 추가 9개: 쇼핑몰 목록, 인앱 브라우저(번역됨), 결제 완료 → 옷장에 추가, 내 옷장, 옷 상세·수정, 등록 방법 고르기, 사진으로 등록 결과(문구는 mmminsung02 앱과 같음), 자동 등록 후보 확인, 공유로 받은 상품. 내용은 각 기능 문서의 입력·출력·예외 상황을 따름. 사이트맵의 `시안 없음`이 0개가 됨. 배송·상태 태그 색을 테마 변수(`--ok-*`, `--warn-*`)로 추가. 헤드리스 Chrome으로 라이트·다크 확인 |
| 2026-11-04 | README 'AI에게 요청' 3건 처리 (mmminsung02) | ① 하단 탭 `번역` → **`쇼핑`**, 탭 아이콘 4개를 시안 SVG(home·hanger·bag·user)로 바꿈 ② **라이트/다크 테마** (`ui/theme/Theme.kt`, 색은 `docs/app-sitemap/index.html` 값 그대로). 저장된 선택이 없으면 기기 설정을 따르고, 마이 탭의 `라이트`/`다크` 버튼으로 바꾸면 기기에 저장 ③ 시안 위치 변경 확인. 앱 테스트 20개 통과(탭 이름, 색 값, 기기 설정 따르기, 테마 저장 추가). 실제 폰 미확인 |
| 2026-11-04 | README 요청: 앱 버전을 빌드 때 넘기기 (mmminsung02) | `./gradlew assembleDebug -PversionCode=3 -PversionName=0.1.2`. 넘기지 않으면 1, `0.1.0`. 빌드한 APK를 `aapt2 dump badging`으로 열어 두 경우 모두 값이 찍힌 것을 확인 |
| 2026-11-04 | 기능 2 옷장 조회·수정·삭제 **서버+앱** (`sandbox/mmminsung02/`, 검수 전) | 서버: `GET /v1/clothes`(category·color 필터, limit·offset, 최신순, total), `GET /v1/clothes/{id}`, `GET /v1/clothes/{id}/image`, `PATCH`(보낸 값만 바꾸고 confirmed=true, 빈 글자는 정보 없음, 목록 밖 값 400), `DELETE`(DB 행·임베딩·사진 파일), `DELETE /v1/me/data`(옷·상품·리포트·대화·등록 기록·사진 폴더). 다른 사용자 옷은 404. 상품·리포트·대화·등록 표는 전체 삭제 확인용으로 미리 만듦. 서버 테스트 28개 통과(기능 2 완료 조건 [서버] 6개 포함). 앱: 내 옷장 격자(3열, Coil로 사진), 종류 칩(전체/아우터/상의/하의/기타)·색 동그라미 필터(다시 누르면 해제), `N벌`, `미확인`·`사진 필요` 표시, 빈 옷장·결과 없음·불러오기 실패(다시 시도) 화면, `+ 옷 등록` 버튼, 옷 상세(종류·색·이름·브랜드·사이즈·소재 수정, `확인하고 저장`, 삭제 확인), 마이 `내 데이터 전체 삭제`(확인 창). 앱 테스트 39개 통과(완료 조건 [앱] 필터 테스트 포함). **안 한 것**: 오프라인 Room 캐시(예외 상황), 등록 방법 고르기 시트(지금은 `+ 옷 등록`이 사진 등록으로 바로 감, 주문내역·영수증은 기능 13·15), `확인하기` 링크, 여러 벌 고르기(도전 목표). 색 동그라미 중 시안에 없는 5색(그린·퍼플·옐로·오렌지·멀티)은 임시 값. 실제 폰 미확인 |
| 2026-11-04 | README 요청: 경험 저장소(`Personal_AI_Work_Experience_Repository`) `general/`·`mobile/` 규칙 확인 (mmminsung02, 읽기만) | 바로 적용한 것: ① 큰 기능 뒤 diff 다시 읽기 → 기능 2에서 버그 발견·수정(마이에서 전체 삭제 뒤 내 옷장으로 돌아오면 지운 옷이 그대로 보임. 목록을 ViewModel 생성 때 한 번만 불렀음 → 화면에 들어올 때마다 다시 불러옴, 재현 테스트 추가, 앱 테스트 40개 통과) ② 기능 목록 상태를 `구현 전 / 구현했지만 미검증 / 확인됨`으로 적기(기능 1·2를 `구현했지만 미검증`으로) ③ 긴 스크립트는 헤레독 대신 임시 .py 파일로 실행(이 PC Git Bash에서 헤레독이 실제로 두 번 깨짐) |
| 2026-11-04 | 긴 빌드·테스트용 **진행 창** (mmminsung02, 사용자 선택) | 경험 저장소 collaboration.md 규칙. `powershell -NoProfile -ExecutionPolicy Bypass -File sandbox/mmminsung02/tools/run-gradle.ps1 [-Tasks "..."] [-Title "..."]` → Gradle을 돌리면서 `[PROGRESS]` 창을 따로 띄운다(단계 ktlint/compile/test/apk, 진행률은 최근 5번 평균 시간 기준, Gradle 프로세스 0이면 빨간 경고, 끝나면 통과·실패 요약 후 멈춤). 창은 읽기만 한다. 로그는 `%TEMP%\gradle-progress\`. 첫 시도는 PowerShell 5.1이 `cmd /c "cd /d ..."`의 따옴표를 깨뜨려 실패 → 앱 폴더로 이동한 뒤 gradlew를 직접 실행하게 고침. 앱 테스트 40개 통과 확인 |
| 2026-11-04 | 기능 3 Google 로그인·계정 삭제 **서버** (`sandbox/mmminsung02/`, 검수 전) | `POST /v1/auth/google`(Google ID 토큰 확인 → 사용자 생성·조회 → 세션 토큰), `POST /v1/auth/logout`, `DELETE /v1/me`(데이터·사진·사용자·세션). 세션 토큰은 SHA-256 값만 저장, 30일 뒤 만료. 옷장 API는 모두 `Authorization: Bearer` 필요(없으면 401), 개발용 사용자 `dev-user` 없앰. 실제 확인은 google-auth `verify_oauth2_token`(aud=`GOOGLE_CLIENT_ID` 환경변수, iss 확인). `GOOGLE_CLIENT_ID`가 없으면 로그인 503. 서버 테스트 72개 통과(`cd sandbox/mmminsung02` → `../../.venv/Scripts/python -m pytest`, 기능 3 완료 조건 [서버] 6개 포함, Google 확인은 가짜). 실제 Google 토큰으로는 미확인 |
| 2026-11-04 | 결정: **로그인해야만 앱을 쓴다** (사용자 선택) | 앱을 켜거나 로그아웃하면 로그인 화면. 시안의 '3. 로그아웃 완료(비로그인 홈)'은 쓰지 않는다 (시안과 다름, 시안 고칠지는 moneyhipo와 정하기) |
| 2026-11-04 | 기능 3 **앱: 작업 중간에 멈춤 (컴파일 안 됨)** | 사용자가 자리를 비워 중단. 아래 '다음 할 일' 맨 위 참고 |
| 2026-10-21 | 대화 기록 저장, 문서 커밋·푸시 | [conversation-log.md](conversation-log.md). 제출 기획서 PDF는 팀원 이름·학번이 있어 공개 저장소에 올리지 않음 (`.gitignore`) |

## 결정 사항
| 날짜 | 결정 | 이유 |
|------|------|------|
| 2026-10-21 | 안드로이드 스튜디오(Kotlin + Jetpack Compose)로 개발하고 Google Play에 정식 출시 | 팀 목표. 제출 기획서와 같음 |
| 2026-10-21 | 쇼핑 화면 연동은 **인앱 브라우저 + 그 자리 번역**으로 한다. 공유하기도 유지 | 다른 앱 위 오버레이는 화면 캡처 권한·OCR 지연 부담이 크다. 인앱 브라우저는 페이지 글자를 직접 읽어 OCR 없이 빠르고 정확하다 |
| 2026-10-21 | 옷 자동 등록: 주문내역 캡처, 앱에서 산 옷 감지, 영수증. 카드 결제내역은 제외 | 하나씩 찍어 올리면 아무도 안 쓴다. 카드 내역에는 상품 정보가 없고 마이데이터 API는 허가 사업자만 쓸 수 있다 |
| 2026-10-21 | AI 점원 MVP 범위: 상품 질문 답변 + 비교 리포트 전달 + '어울리는 내 옷' 간단 추천 | 옷을 잘 모르는 사용자 지원. 전체 코디 추천은 일정상 후속 |
| 2026-10-21 | MVP 지원: 영어·일본어 필수(그 밖의 언어는 가능한 만큼), 상의·하의·아우터 | 제출 기획서 범위 |
| 2026-10-21 | 단위·사이즈 변환을 MVP에 넣는다 (이미지 속 사이즈표는 제외) | 추가 아이디어. 순수 계산이라 비용이 작다 |
| 2026-10-28 | LLM API는 **Claude API**, 모델은 우선 **Claude Haiku 5.5** (`claude-haiku-5-5`) | 학교 지원금으로 받기로 함. 번역·구조화처럼 양이 많은 작업에 싸다. AI 점원 품질이 부족하면 Sonnet으로 바꿀지 비교한다. API 키는 서버에만 둔다 |
| 2026-10-28 | 역할 분담을 하지 않는다. 팀은 기획을 하고, 앱·서버 구현은 모두 AI로 한다 | 구현을 AI가 하므로 담당 구분이 의미 없다. 누가 무엇을 했는지는 작업 로그에만 기록한다 |
| 2026-10-28 | 각자 작업물은 `sandbox/<GitHub 아이디>/`에서 만들고 검수한 뒤 `src/`·`tests/`로 옮긴다 (CLAUDE.md 규칙 8) | GitHub 업로드 오류가 있었다고 함. 처음에 `임시_작업물_저장폴더/<실명>/`으로 만들었으나, 저장소가 공개라 실명 대신 GitHub 아이디로, 한글 경로 문제 때문에 폴더 이름을 영어로 바꿈 |
| 2026-10-28 | 서버 호스팅은 **AWS Lightsail** (서울 리전, 4GB 요금제 예정) | 학교 지원금으로 받기로 함. 월 고정 요금이라 예산 관리가 쉽다. 비공개 테스트 때문에 11주차 안에 배포 |
| 2026-10-28 | 서버 호스팅을 **Vultr** (서울 리전, 선불 크레딧)로 바꾼다. 위 Lightsail 결정을 대신함 | 학교가 AWS는 후불제라 지원할 수 없다고 함. Vultr는 크레딧을 미리 충전해 차감한다. 수업 프로젝트라 서버는 첫 비공개 테스트 직전(약 11/10)부터 15주차 평가(약 12/13)까지만 켜고, 그 전에는 각자 컴퓨터에서 개발한다. 신청액 약 $40(4GB 5주 약 $30 + 여유). 학교 카드 해외 결제와 인보이스 정산 가능 여부 확인 필요. 서버를 지울 때 스토어 앱을 어떻게 할지(비공개 전환 등) 팀에서 정한다 |
| 2026-10-28 | minSdk **26** (Android 8.0) | 거의 모든 폰을 지원하고 최신 라이브러리를 쓰기에 문제없다 |
| 2026-10-28 | 계정은 **Google 로그인** | 폰을 바꿔도 옷장이 유지된다. Play 정책상 앱 안과 웹 두 곳에 계정 삭제가 필요해서 기능 문서 [account/google-login.md](features/account/google-login.md)를 추가하고 기능 순서 3번에 넣었다. 이메일·Apple 로그인은 넣지 않는다 |
| 2026-10-28 | 안드로이드 테스트는 Gradle 기본 위치(`src/android/app/src/test`)에 둔다. 서버·주입 JS 테스트는 `tests/` | Android Studio에서 바로 돌릴 수 있다. CLAUDE.md 규칙 5번에 예외로 적음 |
| 2026-10-28 | 코딩 규칙: Kotlin은 **ktlint**, Python은 **ruff** | 누가 작성해도 코드 모양이 같아진다. CLAUDE.md 코딩 규칙에 적음 |
| 2026-11-04 | 로그인은 **Google 로그인만** 쓴다. 시안에 있던 이메일·비밀번호 로그인, 회원가입, 회원가입 완료, Apple 로그인 화면은 만들지 않는다 | 10/28 결정을 유지했다. 비밀번호 저장·재설정이 필요 없고, 처음 쓰는 사람도 버튼 하나로 바로 가입된다 |
| 2026-11-04 | 쇼핑몰은 로고 대신 **이름 글자**로 보여 준다. 시안의 ZARA·H&M·amazon·asos 로고는 지원 후보(ASOS, ZOZOTOWN, UNIQLO, GU) 이름으로 바꿈 | 로고는 Play 지식재산권 정책 위험이 있다. ZARA·H&M·Amazon 미국은 한국어를 지원해서 후보에서 뺀 곳이다 |
| 2026-11-04 | 유사도는 숫자(%) 대신 **`매우 비슷` / `비슷`** 같은 단계로 보여 준다 | 숫자는 '중복 구매 확률'처럼 읽힐 수 있다. 단계 기준 점수는 Recall@3 평가 때 정한다. [similar-search.md](features/compare/similar-search.md)에 적음 |
| 2026-11-04 | 하단 탭 3번째 이름을 **쇼핑**으로 한다 (탭: 홈 / 내 옷장 / 쇼핑 / 마이) | 누르면 쇼핑몰 목록이 바로 열려서 이름과 맞는다. 베이스 시안의 "화면 번역"은 이미 뺀 오버레이 번역 기능을 떠올리게 하고, "번역"은 글을 넣어 번역하는 화면처럼 오해할 수 있다 |
| 2026-11-04 | 화면 테마는 **라이트 / 다크** 두 가지. 처음에는 기기 설정을 따르고, 마이에서 바꾼다 | 사용자 요청 |
| 2026-11-04 | 쇼핑몰 목록에 **한국어를 지원하는 대형 해외 쇼핑몰도 넣는다** (Amazon, ZARA, H&M, SSENSE 등). 10-28의 "한국어 미지원 쇼핑몰만" 기준을 대신한다. **번역은 부가 기능**: 한국어가 아닌 페이지에서만 켜진다 | 쇼핑 탭이니 대형 사이트를 포기할 이유가 없다. 옷장 비교·AI 점원·자동 등록은 언어와 상관없이 쓸 수 있다. 한국어 페이지는 이미 page-translation.md 예외 규칙대로 번역하지 않는다 |
| 2026-11-04 | **국내 쇼핑몰도 넣는다** (무신사, 29CM, W컨셉 초안). 앱 목표를 "해외 쇼핑 번역"에서 "국내·해외 쇼핑 중 내 옷장과 비교"로 넓힌다 (00-overview.md 목표·CLAUDE.md 한 줄 설명 고침). Amazon·SSENSE는 `배송 가능`으로 바꿈 | 사용자 결정. 옷장 비교·자동 등록(주문내역 캡처)은 국내 쇼핑에서도 그대로 쓸 수 있다. Amazon·SSENSE는 사용자가 결제 화면 배송지에 한국이 뜨는 것을 확인 |
| 2026-11-04 | **미리 입어보기(AI 가상 피팅)**를 **도전 목표**로 넣는다. 여는 곳은 AI 점원 패널(이 상품 + 내 옷)과 내 옷장(여러 벌 고르기) 두 곳. 기본 마네킹(체형 3종, 초안)에 입히고, 결과·코디는 폰에만 저장하며 서버는 중계만 한다 | 구매 판단("사면 내 옷이랑 어울릴까")을 눈으로 확인하게 한다. 2D 겹치기(폰에서 처리, 무료)도 있었지만 실제로 입은 모습에 가까운 AI 방식을 골랐다. 서버에 GPU가 없어 외부 서비스를 쓴다. 장당 과금이라 MVP가 아닌 도전 목표로 둔다. [virtual-fitting.md](features/compare/virtual-fitting.md) |
| 2026-11-07 | 종류 기준: **가디건·후드 집업은 아우터, 지퍼 없는 후드티는 상의**, 원피스·점프수트는 기타(`other`) | 사용자 결정. 대부분의 쇼핑몰 카테고리와 같다 |
| 2026-11-07 | 분류 평가는 **종류마다 같은 장수**로, **DeepFashion 100장(25장씩)과 실제 옷장 사진 두 묶음**으로 재고 따로·합쳐서 기록한다 | 사용자 결정. DeepFashion은 쇼핑몰 스튜디오 사진이라 실제 옷장 사진과 다르다 |
| 2026-11-07 | DeepFashion-MultiModal은 **평가에만** 쓴다. 사진·잘라 낸 사진·라벨은 Git·공개 드라이브에 올리지 않고, 이 데이터로 학습한 모델을 앱·서버에 넣지 않는다 | 라이선스가 비상업 연구용이고 원본·파생 데이터의 복사·배포를 금지한다. 앱은 무료·광고 없음·약 1달 운영이라 비상업이지만, 배포 금지는 따로 걸린다 |

## 제출 기획서와 달라진 점 (Sprint 발표에서 설명할 것)
| 항목 | 제출 기획서 (10/14) | 현재 | 이유 |
|------|---------------------|------|------|
| 쇼핑 화면 연동 | 공유 메뉴 / 오버레이 번역 (필수) | 인앱 브라우저 그 자리 번역 + 공유하기 | 위 결정 사항 참고 |
| 옷 등록 | 사진 업로드 | + 주문내역 캡처, 앱 내 구매 감지, 영수증 | 등록 부담이 가장 큰 사용성 문제 |
| AI 점원 | 없음 | 추가 | 옷을 잘 모르는 사용자 지원 |
| 사이즈표 변환 | 장기 확장 | 단위·사이즈 변환은 MVP | 비용이 작고 효용이 바로 보임 |
| 배포 | Firebase App Distribution | Google Play 정식 출시 (Firebase는 팀 내부 테스트용으로만) | 팀 목표 |
| 쇼핑몰 범위 | 해외 쇼핑몰 (번역이 중심) | 국내·해외 쇼핑몰, 번역은 부가 기능 | 2026-11-04 결정. 대형 사이트를 뺄 이유가 없고 옷장 비교는 어디서나 쓸 수 있다 |

## 제출 기획서 PDF 확인 결과 (다시 낼 일이 있으면 고칠 것)
- 웹과 앱이 섞여 있음: 6장 구조도(p7)·그림 1·2(p4~5)·8장 UI 담당 팀원 역할(p10)이 Streamlit 웹 기준
- 오버레이 번역: 2.3장·10장은 "장기 확장", 5장 표(p6)는 "필수"
- 배포: 2.2장 "스토어 배포" vs 6장 표 "Firebase App Distribution"
- 9장 일정에 고치기 전·후 문장이 같이 남아 있음
- 빠진 것: 위험요소와 극복 방안, 외부 자원 출처 표시, 서버 호스팅비·API 사용료
- 제출 전 지울 것: 8장 파란 안내문, 별첨 페이지
- 오타: ios→iOS(p3), 가메라→카메라(p4), 옵장→옷장·이이미지→이미지·Retrifit→Retrofit·Fast API→FastAPI(p7), Distributuin→Distribution(p8)

## 미결정 사항
| 항목 | 후보 | 비고 |
|------|------|------|
| 서비스 이름 | ClosetLens, 옷장 AI | 스토어 등록 전까지. applicationId(앱 고유 ID, 지금은 임시 `com.team12.closet`)도 같이 정한다 |
| 홈 화면 시안 | 베이스(2카드), 초안(4카드) | `docs/image/` |
| 번역 방식 | LLM 전부 / 번역 API + LLM(구조화만) | 비용·속도 비교 후 |
| OCR | EasyOCR(서버) / ML Kit(기기) | |
| 사이즈 환산표 값 (기능 5) | 지금 초안(`sandbox/moneyhipo/units/size_tables.json`) / 출처 있는 표로 바꾸기 | 의류 하의(허리 인치) 표를 넣을지도 정한다 |
| CLIP 모델 | 일반 CLIP / 패션 특화 CLIP | 평가로 결정. 2026-11-07 DeepFashion 400장 결과로는 **FashionCLIP + v2 문장(88.0%)을 추천** (작업 로그). 실제 옷장 사진으로 확인한 뒤 정한다 |
| 서버 요금제 | Vultr 4GB(예정) / 2GB | CLIP 단독 측정 약 500MB (2026-11-04). 서버를 다 붙인 뒤 다시 재고 결정 |
| 가상 피팅 서비스 (도전 목표) | FASHN (장당 약 $0.075), Google Vertex AI Virtual Try-On (재판매처 기준 장당 약 $0.063), 그 밖의 API | 2026-11-04 웹 검색 결과이고 비교 사이트·재판매처 가격이라 공식 가격을 확인해야 한다. 학교 지원금으로 낼 수 있는지, 업체의 이미지 보관 정책, 여러 벌을 한 번에 입힐 수 있는지 함께 본다 |
| 미리 입어보기 하루 횟수 | 시안은 5회 | 서비스와 예산을 정한 뒤 |
| 마네킹에 본인 사진 쓰기 | 안 씀(초안) / 넣기 | 넣으면 몸 사진이 외부 서비스로 가서 개인정보 부담이 커진다 |
| 지원 쇼핑몰 목록 | **2026-11-04부터 기준이 바뀜: 한국어 지원 여부와 상관없이 많이 쓰는 해외 쇼핑몰을 넣는다** (결정 사항 참고). 시안 기본 목록(초안): 국내 무신사·29CM·W컨셉, 해외 Amazon·ZARA·H&M·SSENSE + 아래 후보. 국내 쇼핑몰도 WebView 열림·상품 정보 읽기를 확인해야 한다(앱 설치 유도 화면이 있을 수 있음). 아래는 10-28 조사 기록이다. 처음 기준: 많이 쓰이는 해외 쇼핑몰 중 한국어를 지원하지 않는 곳. 2026-10-28 직접 확인 결과 한국어 미지원 후보: 영어 **ASOS** (https://www.asos.com/women/), **Urban Outfitters** (https://www.urbanoutfitters.com/), **Abercrombie & Fitch** (https://www.abercrombie.com/, Hollister는 같은 회사라 하나만), **END. Clothing** (https://www.endclothing.com/), **J.Crew** (https://www.jcrew.com/), **Nordstrom** (https://www.nordstrom.com/), **유니클로 미국** (https://www.uniqlo.com/us/en/) / 일본어 **ZOZOTOWN** (https://zozo.jp/), **유니클로 일본** (https://www.uniqlo.com/jp/ja/), **GU 일본** (https://www.gu-global.com/jp/ja/), **BEAMS** (https://www.beams.co.jp/). 한국 직배송(웹 검색, 확인 필요): ASOS 있음(공식 배송 페이지), J.Crew 있음 가능성 높음(한국용 `/kr` 스토어), Urban Outfitters·A&F·END.·Nordstrom 불확실, 유니클로 미국·ZOZOTOWN·유니클로 일본·GU 일본 없음(국내 전용, 배송대행지 필요), BEAMS 자료가 엇갈림. **배송 태그로 표시하고 후보에서 빼지는 않는다** (2026-10-28 결정, [shop-browser.md](features/shopping/shop-browser.md)): `배송 가능` ASOS / `배송대행 필요` 유니클로 미국·ZOZOTOWN·유니클로 일본·GU 일본 / `그 외`(확인 전) J.Crew·Urban Outfitters·A&F·Nordstrom·END.·BEAMS. `그 외`는 결제 화면에서 배송 국가를 한국으로 바꿔 확인하면 옮긴다. Amazon 일본은 한국어 미지원이지만 종합몰이라 기본 목록에서는 빼고 사용자가 직접 추가하게 둔다(초안). 한국어를 지원해서 처음엔 뺐던 곳(11-04부터 다시 후보): Amazon(미국), SSENSE, 라쿠텐, UNITED ARROWS, ZARA, H&M. 이들의 배송 태그는 확인 전 | 상품 페이지 구조 확인 완료: ASOS·ZOZOTOWN(JSON-LD 있음), 유니클로 일본(페이지 데이터). 나머지는 앱이 생기면 실제 폰 WebView에서 열림·상품 정보 읽기를 확인하고 기본 목록을 정한다 |

## 다음 할 일 (순서대로)
0. **[mmminsung02 이어서] 기능 3 앱 마무리.** 커밋 `기능 3 앱 WIP` 상태는 **컴파일 안 됨**. 마지막으로 통과한 앱 테스트는 그 전 커밋(`0b240cd`, 40개). 할 일:
   - `ui/ClosetApp.kt`: 시그니처를 `ClosetApp(clothesRepository, themeStore, authRepository, googleSignIn)`으로 바꾸고, `authRepository.session`이 null이면 `LoginScreen`, 아니면 지금 탭 화면. 마이 탭은 `ui/account/MyScreen.kt`를 쓰고 ClosetApp 안의 옛 `MyTab`은 지운다 (MainActivity는 이미 새 시그니처로 부름)
   - 테스트: 가짜 `AuthRepository`·`GoogleSignIn` 만들기. 기존 테스트의 `ClosetApp(FakeClothesRepository(), InMemoryThemeStore())` 호출을 로그인된 가짜로 바꾸기. `NetworkClothesRepositoryTest`는 없앤 `NetworkClothesRepository.create()` 대신 `ServerConnection(server.url("/").toString(), InMemorySessionStore(Session("t"))).clothes` 사용
   - 새 테스트(완료 조건 [앱]): 로그인 안 했으면 로그인 화면 → 가짜 Google 성공 → 홈 / 취소·설정 없음·서버 실패 메시지 / 마이에서 계정 삭제 확인 → 로그인 화면 / 로그아웃 확인 → 로그인 화면 / 서버 401이면 세션 지우고 로그인 화면(`AuthInterceptor`, MockWebServer) / 요청에 `Bearer` 헤더가 붙는지
   - `tools/run-gradle.ps1`로 빌드·테스트 (사용자가 진행 창을 원함)
   - 만든 파일: `data/Session.kt`(KeystoreSessionStore, Robolectric에서는 테스트 못 함 → 실제 폰 확인 필요), `data/AuthRepository.kt`(SessionHolder, AuthInterceptor, NetworkAuthRepository), `data/GoogleSignIn.kt`(Credential Manager), `data/Server.kt`(ServerConnection: API와 Coil이 같은 OkHttpClient), `ui/account/LoginScreen.kt`, `ui/account/MyScreen.kt`
   - Google 로그인용 ID: 앱은 `-PgoogleWebClientId=...`, 서버는 `GOOGLE_CLIENT_ID` 환경변수. 둘 다 Google Cloud의 'Web' OAuth 클라이언트 ID. 팀이 만들면 넣는다 (무료, Play Console 등록비와 별개). Android용 클라이언트 ID도 패키지 이름 + 디버그 SHA-1로 같이 만들어야 한다
1. **Play Console 개인 개발자 계정 만들기**, 비공개 테스트 테스터 15명 정도 모으기 시작 (14일 테스트가 일정의 병목)
2. 미결정 사항 중 OCR·지원 쇼핑몰 정하기
3. **기술 검증**: 후보 쇼핑몰마다 WebView에서 열림, 로그인·장바구니·결제 화면 진입, JS 주입으로 글자 읽기·바꾸기, JSON-LD Product 유무를 확인하고 결과를 여기에 기록
4. 안드로이드 프로젝트 뼈대 검수 후 `src/android/`로 옮기기 (sandbox에서 생성 완료). 옮기기 전에 3번째 탭 이름을 `번역`에서 `쇼핑`으로 바꾼다 (`ClosetApp.kt`의 `SHOPPING("번역", ...)`, 테스트에 탭 이름이 있으면 같이). **applicationId 정하기** (Play에 올리면 바꿀 수 없음, 서비스 이름과 함께 첫 비공개 테스트 전까지)
5. 평가 데이터 준비 시작: 의류 사진 100~200장 라벨링, 영어·일본어 상품 페이지·캡처 30~50개
6. 기능 1번 [wardrobe/photo-register.md](features/wardrobe/photo-register.md): 서버는 sandbox에서 완료(검수 전). 앱 화면도 sandbox에서 완료(검수 전, 실제 폰 미확인). 남은 것: **[평가]** DeepFashion으로 쟀다(2026-11-07): 지금 서버 설정 79.8%(400장), FashionCLIP + v2 문장이면 88.0%. 실제 옷장 사진 29장(2026-11-08): 지금 서버 설정 82.8%(색 62.1%), FashionCLIP + v2 75.9%(색 48.3%)로 순위가 반대. **옷장 사진을 더 찍어(특히 상의, 팀원 옷도) 모델을 정하기**, 색 정확도 올릴 방법 찾기 (`sandbox/moneyhipo/eval/README.md`). 기능 2번은 sandbox에서 완료(검수 전, 오프라인 캐시 남음). 그다음 기능 3번(Google 로그인)부터 순서대로

## 알려진 위험
[architecture.md](architecture.md)의 '주요 위험' 참고. 가장 큰 두 가지:
- Play 스팸 정책(WebView): 인앱 브라우저 때문에 심사에서 거절될 수 있다. 공유하기 경로가 대안이다
- 비공개 테스트 14일: 11주차 안에 시작하지 못하면 15주차 출시가 어렵다
