# 대화 기록

AI와 작업한 세션의 대화 흐름을 남긴다. 다른 PC나 다른 AI가 이어서 작업할 때 맥락을 확인하는 용도다.
결정 사항과 할 일의 최신본은 [progress.md](progress.md)가 기준이다.

---

## 2026-10-21 세션 (Claude Code, VS Code)

### 한 줄 요약
흩어져 있던 기획 자료(아이디어 문서, 추가 아이디어, UI 시안, 제출 기획서 PDF)를 합쳐 개발용 기획 문서를 만들었다. 목표는 **안드로이드 스튜디오(Kotlin) 앱 개발 + Google Play 정식 출시**로 정했다.

### 대화 흐름
1. **"여기 파일 읽고 기획 작성 도움좀"**
   - 읽은 자료: `아이디어_상세.md`, `추가 idea.txt`, docs 템플릿, UI 시안 2장(`docs/image/`), 경험 저장소 README와 `general/` 교훈
   - 짚은 문제: 추가 아이디어와 UI 시안(모바일 앱, 실시간 번역, AI 점원)이 아이디어 문서의 MVP 범위(Streamlit 웹, 실시간 번역 제외)와 충돌. 카드 결제내역에는 상품 정보가 없고 마이데이터 API는 허가 사업자만 쓸 수 있음

2. **AI가 질문 4개를 하고 사용자가 답함**
   | 질문 | 답 |
   |------|-----|
   | 기획 문서 용도 | 개발용 문서만 (docs/ 채우기) |
   | 서비스 형태 | 모바일 앱 + 인앱 브라우저 (페이지 글자를 그 자리에서 번역, AI 점원은 옆/아래 패널) |
   | 옷 자동 등록 경로 | 카드 결제 빼고 전부: 앱에서 산 옷 감지, 주문내역 캡처, 영수증 (사진 직접 등록은 기본 유지) |
   | AI 점원 MVP 범위 | 상품 질문 답변 + 비교 리포트 전달 + '어울리는 내 옷' 간단 추천 |

3. **첫 문서 저장 실패**: Claude Code 자동 모드의 안전 검사기가 판정을 못 해 파일 쓰기가 거부됨. 우회하지 않고 사용자에게 알림. 이후 사용자가 자동 모드를 끄고 진행

4. **"pdf 확인좀"**: 제출 기획서 `팀플기획서-12조 (1).pdf`(2026-10-14) 검토
   - 확인된 기술 스택: Kotlin + Jetpack Compose, Retrofit, FastAPI, EasyOCR 또는 ML Kit, SQLite + Room, FAISS. Sprint 평가는 8·12·15주차
   - 모순: 웹(Streamlit)과 앱이 섞임, 오버레이 번역이 '장기 확장'이면서 '필수', 배포가 '스토어'이면서 'Firebase App Distribution', 9장 일정에 수정 전·후 문장이 함께 있음
   - 빠진 것: 위험요소, 외부 자원 출처, 호스팅·API 비용. 제출 전 지울 안내문과 별첨, 오타 목록
   - 상세 내용은 [progress.md](progress.md)의 'PDF 확인 결과'

5. **"일단 목표는 안드로이드 스튜디오로 앱개발, 및 구글 스토어 출시임"**
   - Google Play 조건을 웹에서 확인함 (2026-10-21 기준)
     - 2023-11-13 이후 만든 개인 개발자 계정은 테스터 12명 이상, 14일 연속 비공개 테스트를 거쳐야 프로덕션 출시 가능
     - 2026-08-31부터 새 앱은 Android 16(API 36) 이상 타깃
     - 남의 웹사이트를 WebView로 보여 주는 게 주목적인 앱은 스팸 정책 위반 ("다른 회사 쇼핑몰을 감싼 앱"이 위반 예시) → 인앱 브라우저의 심사 위험
   - 작성한 문서: `CLAUDE.md`, `docs/00-overview.md`, `docs/architecture.md`, `docs/progress.md`, 기능 문서 14개

6. **"피쳐에 전부 모으지 말고 종류별로 쪼개서 보관해"**
   - `docs/features/` 아래 6개 폴더로 나눔: `wardrobe/`, `auto-register/`, `translation/`, `shopping/`, `compare/`, `release/`. 목록은 [features/README.md](features/README.md)

7. **경험 저장소에 교훈 추가 → "ㅇㅇ 전부해"**
   - `moneyhipo/AI_Experience-repository`에 교훈 7개 푸시 (커밋 `001ed4b`): 기획 자료 대조, 데이터 출처 필드 확인, 기능 문서 하위 폴더, 마크다운 링크 괄호 인코딩, Play 출시 조건, WebView 정책, 도구가 막혔을 때 우회 금지

8. **"딴데 가야 해서 대화기록 저장하고 깃푸쉬"**
   - 이 문서를 만들고 커밋·푸시함
   - 이 저장소는 **공개**라서 제출 기획서 PDF(팀원 이름·학번 포함)는 올리지 않고 `.gitignore`에 넣음. 문서에 있던 팀원 이름도 역할 표현으로 바꿈
   - 세션 중 사용자가 추가한 시안 `docs/image/로그인-메인화면 초안.png`(로그인·회원가입·로그아웃 화면)도 함께 올림. 이메일·Google·Apple 로그인이 있어 '계정 방식' 미결정 사항에 반영함. 문서에는 아직 로그인 기능 문서가 없음

### 이번 세션에서 정한 것
- 안드로이드(Kotlin + Jetpack Compose) 앱 개발 + Google Play 정식 출시
- 쇼핑 화면 연동은 인앱 브라우저 그 자리 번역 + 공유하기 유지 (제출 기획서의 오버레이 번역 대신)
- 자동 등록 3종 (카드 결제내역 제외), AI 점원 MVP 범위, 단위·사이즈 변환 MVP 포함
- MVP 지원: 영어·일본어, 상의·하의·아우터

### 아직 정하지 않은 것 (자세한 표는 progress.md)
서비스 이름, 홈 화면 시안, 역할 분담, LLM API, 번역 방식, OCR, CLIP 모델, 서버 호스팅, 계정 방식, minSdk, 지원 쇼핑몰, 쇼핑몰 목록 표시(로고/텍스트), 안드로이드 테스트 위치, 코딩 규칙

### 다음에 할 일
1. **Play Console 개인 개발자 계정 만들기, 테스터 15명 정도 모으기** (11주차 안에 비공개 테스트를 시작해야 15주차 출시 가능)
2. 미결정 사항 정하기 (우선: 역할 분담, LLM API, OCR, 지원 쇼핑몰)
3. 기술 검증: 후보 쇼핑몰이 WebView에서 열리는지, 로그인·결제 화면 진입, JS 주입, JSON-LD 유무
4. 안드로이드 프로젝트(targetSdk 36), FastAPI 서버 뼈대, 테스트 환경
5. 기능 1번 [wardrobe/photo-register.md](features/wardrobe/photo-register.md)부터 구현

### 다음 세션 AI에게
- `CLAUDE.md` → 경험 저장소 README → `docs/progress.md` → 이 문서 순서로 읽는다
- 제출 기획서 PDF는 처음 작업한 PC에만 있다. 필요하면 사용자에게 요청한다 (내용 요약은 이 문서 4번과 progress.md에 있음)

---

## 2026-10-28 세션 (Claude Code, VS Code)

### 한 줄 요약
팀원이 올린 결정(Claude API, AWS Lightsail, 역할 분담 없음, 쇼핑몰 확인 결과)을 받고, 9~11주차 작업 순서를 제안했다. Git 작업 규칙을 CLAUDE.md에 추가했다.

### 대화 흐름
1. **"깃헙 변경사항 있으면 받아줘"**
   - 커밋 2개를 받음 (`28286a9`, `18101d1`, moneyhipo)
   - LLM API는 Claude API(우선 Haiku 5.5), 서버는 AWS Lightsail 서울 리전(4GB 예정), 역할 분담은 하지 않음
   - 쇼핑몰 확인: ASOS·ZOZOTOWN은 서버에서 직접 받으면 403, 유니클로 일본은 받아짐 → WebView 주입 스크립트로 읽는 설계 유지

2. **"지금 우리가 해야하는 순서를 잡아줘"**
   - 지금은 9주차인데 8주차 일(Play 계정, 기술 검증, 프로젝트 뼈대)이 아직 안 됨. 마감은 **11/15까지 비공개 테스트 시작**
   - 제안한 순서
     - 9주차: Play Console 계정·본인 확인, 테스터 15명 이상 모집 / 코딩 전 결정 4가지 / 프로젝트 뼈대(Android, FastAPI, 테스트 환경, Lightsail, API 키) / 실제 폰으로 쇼핑몰 WebView 확인
     - 10주차: 기능 1·2(옷장), 서버 Lightsail 배포, 개인정보처리방침·데이터 보안 양식 초안, 평가 데이터 라벨링 시작
     - 11주차: 첫 빌드 비공개 테스트 업로드(늦어도 11/15), 기능 3·4, 기능 5~7은 테스트 중 업데이트로
   - 제안한 결정값 (**아직 팀 확정 전**): minSdk 26, 계정은 기기 단위 익명 ID, 앱 테스트는 Gradle 기본 위치에 두고 CLAUDE.md에 예외로 적기, ktlint·ruff
   - 첫 비공개 테스트 빌드에는 옷장 기능(1·2)만 넣고 인앱 브라우저는 나중 업데이트로 넣자고 제안 (14일을 일찍 시작하고, 첫 심사에서 WebView 정책 위험을 빼기 위해). **아직 팀 확정 전**

3. **"앞으로 깃헙 변경사항 있으면 확인하고 작업하기전에 푸쉬해줘, 그리고 md파일에 기록 써줘"**
   - CLAUDE.md 작업 규칙 6·7번 추가: 작업 전 `git fetch`·`git pull`, 작업 후 progress.md·conversation-log.md 기록 후 커밋·푸시

### 다음 세션 AI에게
- 위 2번의 제안(작업 순서, 결정값 4가지, 첫 빌드 범위)은 팀이 아직 확정하지 않았다. 확정되면 progress.md '결정 사항'과 '다음 할 일'에 반영한다

---

## 2026-10-28 세션 2 (Claude Code, VS Code, moneyhipo)

### 한 줄 요약
SP2 MVP 범위와 비용 항목을 정리하고, Claude API·AWS Lightsail을 확정했다. 한국어 미지원 쇼핑몰 후보를 골라 상품 페이지 구조를 확인했다.

### 대화 흐름
1. **MVP가 뭔지, SP2 계획** — SP2용 MVP는 "옷 등록 → 상품 페이지 열기 → 한국어 상품 정보 → 내 옷장 유사 TOP 3" 흐름 하나로 좁히자고 제안. 발표 자료는 만들지 않기로 함 (사용자: "발표는 치우고 앱 개발만")
2. **AI Hub K-Fashion 데이터** — 쓸 만하지만 MVP에는 불필요. 착용 사진 위주라 옷 영역을 잘라 쓰고, CLIP은 고정한 채 분류층만 학습하는 쪽을 추천. 이용 조건(상업적 이용) 확인 필요
3. **체형 키워드 추천** — 크롤링·학습 대신 사이즈표 규칙 계산 + LLM 설명으로 하자고 제안. 완성본 범위
4. **학교 지원금** — Claude API와 AWS Lightsail 두 가지를 받기로 함. Play Console 등록비(25달러)는 별도로 필요
5. **Haiku 5.5 vs GPT-5.6 Luna** — 가격은 Haiku가 더 쌈. 성능 비교 자료는 못 찾음. 우리 데이터로 확인하기로
6. **역할 분담 없음** — 구현은 모두 AI가 하므로
7. **쇼핑몰 후보** — 기준: 많이 쓰이는 해외 쇼핑몰 중 한국어 미지원. 사용자가 직접 확인: Amazon·SSENSE·라쿠텐은 한국어 지원(제외), ASOS·ZOZOTOWN·유니클로 일본은 미지원(후보)
8. **상품 페이지 확인** — curl로는 ASOS·ZOZOTOWN 403, 유니클로는 받아짐(JSON-LD 없음, `__PRELOADED_STATE__`에 소재·세탁). 브라우저로 저장한 ASOS 페이지에는 JSON-LD `Product`가 있음. 결과는 progress.md 작업 로그
9. **Git 규칙** — 2명이 같이 작업하므로 변경사항이 있으면 바로 pull·push

### 다음 세션 AI에게
- ZOZOTOWN 상품 페이지 HTML이 아직 없다. 받으면 같은 방식으로 확인한다
- 상품 정보 추출 순서 초안: JSON-LD → `og:` 태그·페이지 데이터 → 페이지 글자를 Claude Haiku로 정리

---

## 2026-10-28 세션 3 (Claude Code, VS Code)

### 한 줄 요약
학교가 AWS는 후불제라 지원할 수 없다고 해서, 선불로 쓸 서버 호스팅을 비교하고 Vultr로 정했다.

### 대화 흐름
1. **"AWS 대신 선결제인 게 없을까"**: Vultr(크레딧 미리 충전, 서울 리전, 4GB 월 약 $24), 국내 VPS(카페24 등, 기간 선결제·세금계산서), Oracle 무료(서울 자리 부족, 춘천 무료 A1 불가, 무료 한도 절반으로 줄어 비추천)를 비교
2. **Cloudflare**: 후불제라 같은 문제. 컨테이너 디스크가 다시 켜지면 초기화된다는 글이 있음(공식 확인 못 함). Workers는 PyTorch 불가. 대신 개인정보처리방침 페이지는 Cloudflare Pages(무료)에 올리기 좋다
3. **"한 달 정도만 쓸 거야"**: 첫 비공개 테스트(11주차)부터 15주차 평가까지라 약 5주(11/10~12/13). 신청액 약 $40 추천. 서버를 끄면 스토어 앱이 동작하지 않으니 내릴지 팀에서 정하기
4. **통신사 클라우드 (KT Cloud, 네이버, NHN)**: 기본은 후불. 네이버 클라우드는 신규 가입 크레딧(3개월 유효)이 있지만 금액은 확인 못 함
5. **결정: Vultr**. CLAUDE.md, architecture.md, progress.md 반영

### 다음 세션 AI에게
- 학교에 확인할 것: 학교 카드로 해외 결제 가능 여부, 세금계산서 없이 인보이스로 정산 가능 여부. 안 되면 국내 VPS로 바꾼다
- 개인정보처리방침 페이지는 Cloudflare Pages에 올리자고 제안함 (아직 결정 아님)

---

## 2026-10-28 세션 4 (Claude Code, VS Code, moneyhipo)

### 한 줄 요약
ZOZOTOWN 상품 페이지 구조를 확인하고, 사용자가 쇼핑몰을 직접 추가하는 기능을 shop-browser.md에 넣었다.

### 대화 흐름
1. **ZOZOTOWN 페이지 받기** — "웹페이지, 전체" 저장은 145/150에서 멈추고 파일이 지워짐(부속 파일 요청이 봇 차단에 막힌 것으로 추정). 개발자 도구 콘솔에서 `copy(document.documentElement.outerHTML)`로 복사해 `docs/site_html/zozotown.html`에 붙여 넣음
2. **결과** — JSON-LD `Product`가 색상별로 있음. 소재·세탁은 `description` 일본어 자유 글 안에 있어 LLM 정리가 필요
3. **다른 쇼핑몰 후보** — 한국어 미지원 의류 쇼핑몰 후보 링크를 제안함. 한국어 지원 여부는 사용자가 직접 확인하기로 함
4. **내 쇼핑몰 추가 기능** — 사용자가 이름·주소로 추가, Room에 저장, 상품 정보는 공통 순서(JSON-LD → og → LLM)로 읽음

### 다음 세션 AI에게
- 쇼핑몰 후보 확인 결과가 오면 progress.md '지원 쇼핑몰 목록'에 반영한다
5. **쇼핑몰 후보 확인 결과 (사용자 직접 확인)** — 한국어 미지원: Urban Outfitters, Abercrombie & Fitch, Hollister(A&F와 같은 회사), END. Clothing, J.Crew, Nordstrom, 유니클로 미국, GU 일본, BEAMS, Amazon 일본. 한국어 지원(제외): UNITED ARROWS. Amazon 일본은 종합몰이라 기본 목록에서 빼고 사용자 추가로 두자고 제안(초안). progress.md '지원 쇼핑몰 목록'에 반영
6. **해외배송 구분** — 웹 검색으로 한국 직배송 여부를 정리함. 확정은 ASOS(있음)뿐이고 나머지는 결제 화면에서 국가를 한국으로 바꿔 확인 필요. progress.md '지원 쇼핑몰 목록'에 반영
7. **배송 태그** — 쇼핑몰마다 `배송 가능` / `배송대행 필요` / `그 외` 태그를 붙이기로 함. shop-browser.md와 progress.md에 반영
8. **배송대행 브로커 아이디어** — 사업화 아이디어로만 기록. 완성본 후속에 '예상 총액 계산 + 배대지 업체 목록 링크'를 넣음 (00-overview.md 제외 목록)

---

## 2026-10-28 세션 5 (Claude Code, VS Code)

### 한 줄 요약
코딩 전에 필요한 결정 4가지를 정하고, Google 로그인 기능 문서를 추가했다.

### 대화 흐름
1. **학교 요청서에 Vultr를 어떻게 적을지**: "Vultr 클라우드 서버 선불 크레딧 $40 (서울 리전 4GB 서버, 11/10~12/13 AI 서버 운영용)"으로 안내. 사용자가 학교에 요청함
2. **이제 할 일**: Play Console 계정 → 코딩 전 결정 4가지 → 프로젝트 뼈대 → 기능 1·2
3. **결정 4가지**
   - minSdk 26
   - 계정: **Google 로그인** (추천은 익명 ID였지만 사용자가 Google 로그인을 고름). 앱 안·웹 계정 삭제가 필요해서 [account/google-login.md](features/account/google-login.md)를 만들고 기능 순서 3번에 넣음. 서버 인증 방식(Google ID 토큰 검증 → 자체 세션 토큰)과 이메일 미저장은 초안
   - 안드로이드 테스트: Gradle 기본 위치 (CLAUDE.md 규칙 5번 예외)
   - 코딩 규칙: ktlint, ruff
4. **프로젝트 뼈대**: 이 PC에 Android Studio·Java·Android SDK가 없어 앱 프로젝트는 보류. 서버 뼈대(FastAPI `/health`), pytest, ruff, jsdom 테스트 환경을 만들고 테스트 통과 확인

### 다음 세션 AI에게
- 기능 번호가 하나씩 밀렸다 (로그인이 3번). 00-overview.md 기능 목록 기준
- 웹 계정 삭제 페이지는 개인정보처리방침과 같은 곳(Cloudflare Pages 제안)에 두면 된다. 아직 결정 아님

---

## 2026-10-28 세션 5 (Claude Code, VS Code, moneyhipo)

### 한 줄 요약
각자 작업물을 올릴 `sandbox/` 폴더를 만들고 규칙을 CLAUDE.md 8번에 적었다.

### 대화 흐름
1. **"업로드 오류가 나서 각자 작업물을 임시 폴더에 올리자"** — 팀원이 `임시_작업물_저장폴더/<실명>/`을 만듦. 저장소가 공개라 실명이 드러나고 한글 경로는 도구 오류가 날 수 있어 `sandbox/moneyhipo/`, `sandbox/mmminsung02/`로 바꿈. 빈 폴더가 올라가도록 `.gitkeep` 추가
2. **저장소 비공개 전환** — 두 사람 모두 admin이라 비공개로 바꿔도 그대로 쓸 수 있음. 교수·조교 열람이 필요하면 collaborator 초대가 필요. 아직 바꾸지 않음 (팀에서 정하기)

### 다음 세션 AI에게
- 업로드 오류가 동시 푸시 충돌이었다면 sandbox로는 해결되지 않는다. 푸시 전에 항상 `git pull --rebase`
3. **SP2 과제 (10/28 제출)** — 과제 항목: MVP 구현 계획, 완성본 구현 계획, 팀 GitHub 주소(오늘 커밋). 교수님이 저장소 공개를 확인해야 해서 비공개 전환은 하지 않기로 함. `docs/sp2-plan.md` 작성 (SP2 MVP = 기능 1~5, 7, 8 + 비공개 테스트 시작, 페이지 전체 번역은 도전 목표)

---

## 2026-11-04 세션 (Claude Code, VS Code, mmminsung02)

### 한 줄 요약
Android Studio를 설치하고 안드로이드 프로젝트 뼈대를 `sandbox/mmminsung02/android/`에 만들었다. 빌드와 테스트 통과.

### 대화 흐름
1. **Android Studio 설치**: 이 PC는 RAM 7.7GB, 디스크가 빠듯해서 가상 폰(에뮬레이터) 없이 설치하라고 안내. 테스트는 안드로이드 폰이 있는 팀원에게 APK를 보내는 방식(추천) 또는 팀원 PC에서 실행. 사용자가 디스크를 25GB까지 비움. 에뮬레이터(1.1GB)는 해제가 안 되고 설치돼 있음
2. **프로젝트 뼈대**: 최신 안정 버전 확인 후 생성 (AGP 9.5.0으로 만들었다가 Android Studio가 9.4.0까지만 지원해서 9.4.0으로 낮춤, Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00). 하단 탭 4개 빈 화면, Robolectric 테스트 3개
3. **빌드 중 고친 것**: ktlint가 Compose 함수 이름(대문자)을 오류로 봐서 `.editorconfig`에 Composable 예외 추가 / Robolectric이 JDK 25에서 실패해서 테스트 JVM 인자 추가 / `createComposeRule`을 v2로 바꿈 (기존 것은 deprecated)
4. **기능 1 서버**: sandbox에서 테스트 먼저 쓰고 구현. 가짜 분석기 테스트 10개, 실제 CLIP 테스트 2개 통과. CLIP 메모리 약 500MB 측정
   - 사용자에게 알린 임의 결정: 일반 CLIP으로 시작, 업로드 상한 10MB, 신뢰도 0.5 미만은 `기타`, 요청당 사진 1장, 색 영어 코드 저장, 로그인 전 개발용 사용자
5. **기능 1 앱 화면**: 내 옷장 탭 → 사진으로 등록 → 사진 선택기 → 업로드 → 결과 카드. 앱 테스트 16개 통과. 실제 서버(uvicorn) + 실제 CLIP으로 curl 업로드 확인. 실제 폰 확인은 서버 배포 뒤
6. **평가 데이터**: 사용자가 "평가 데이터가 필요할 때마다 알려 달라"고 함. 지금 필요한 것: 기능 1 분류 평가용 의류 사진 100장 + 정답 라벨(종류·색)
7. **화면 확인 방법**: 사진 등록 화면 미리보기 3개(선택 전·업로드 중·결과) 추가. Android Studio에서 `RegisterPhotoScreen.kt` → Split
8. **README 요청 3건 처리**: 탭 `쇼핑`·아이콘 4개(시안 SVG), 라이트/다크 테마(마이 탭에서 바꿈), 시안 위치 확인. 앱 테스트 20개 통과
9. **README 요청: 앱 버전 빌드 값** `-PversionCode`·`-PversionName` 처리
10. **기능 2 옷장 조회·수정·삭제**: 서버 테스트 28개, 앱 테스트 39개 통과. Robolectric 기본 화면이 작아 상세 화면 아래 칩 클릭이 빗나가서 테스트 화면을 `w411dp-h891dp`로 맞춤. 오프라인 Room 캐시와 등록 방법 시트는 남김
11. **README 요청: 경험 저장소 규칙 확인** (`general/`·`mobile/`만, 읽기만). diff 다시 읽기로 기능 2 버그(전체 삭제 뒤 옛 목록) 찾아 고침. 기능 1·2 상태를 `구현했지만 미검증`으로. 긴 작업 진행 창 규칙은 사용자에게 물어봄
12. **진행 창**: 사용자가 '창 띄우기'를 고름. `sandbox/mmminsung02/tools/run-gradle.ps1`로 Gradle을 돌리면 진행 창이 뜬다. 앞으로 몇 분 걸리는 빌드·테스트는 이걸로 돌린다
13. **기능 3 Google 로그인**: Google 로그인용 ID(OAuth 클라이언트 ID)가 아직 없어서, ID 없이 만들 수 있는 부분부터 하기로 함(사용자 선택). 서버 완료(테스트 72개 통과). 시안과 문서가 달라 물어봄 → **로그인해야만 쓰기**. 앱은 로그인 화면·마이 화면·세션 저장·인증 헤더까지 쓰다가 사용자가 자리를 비워 **중간에 멈춤(컴파일 안 됨)**. 이어서 할 일은 progress.md '다음 할 일' 0번

### 다음 세션 AI에게
- **앱이 지금 컴파일되지 않는다** (기능 3 작업 중간). progress.md '다음 할 일' 0번부터 이어서 한다
- 앱 빌드·테스트는 `sandbox/mmminsung02/tools/run-gradle.ps1`로 돌린다 (사용자가 진행 창을 원함)
- applicationId는 임시값이다. Play 업로드 전에 꼭 정한다
- 실제 폰 실행은 아직 안 했다. 디버그 APK: `sandbox/mmminsung02/android/app/build/outputs/apk/debug/app-debug.apk`
- 이 PC는 RAM 7.7GB라 Gradle 힙을 2GB로 잡았다 (`gradle.properties`)

- AGP 버전은 Android Studio가 지원하는 최신까지만 쓴다. 지원 버전은 `%LOCALAPPDATA%\Google\AndroidStudio*\log\idea.log`의 "Latest supported version"에서 확인할 수 있다

---

## 2026-11-04 세션 2 (Claude Code, VS Code, moneyhipo)

### 한 줄 요약
`docs/image/`의 화면 시안 PNG 3장을 HTML 목업으로 옮기고(`sandbox/moneyhipo/mockup/index.html`), 시안과 기획 문서가 어긋나는 4곳을 정했다.

### 대화 흐름
1. **지금 앱의 사용자 흐름**: 안드로이드 뼈대에는 하단 탭 4개와 "준비 중" 글자만 있어 흐름이 없다. 기획 문서 기준으로 앞으로 만들 흐름(로그인 → 홈 → 쇼핑몰 목록 → 인앱 브라우저 → AI 점원 패널 / 내 옷장 → 등록)을 정리했다. 시안이 더 필요한 화면은 9개(쇼핑몰 목록, 인앱 브라우저, AI 점원 패널, 내 옷장 목록, 옷 상세, 등록 방법 고르기, 자동 등록 후보 확인, 공유 결과, 마이)
2. **PNG 시안을 HTML로**: 화면 사이 이동을 버튼으로 연결했다. 헤드리스 Chrome으로 찍어 PNG와 비교했다
3. **시안과 문서가 어긋난 곳 결정**: Google 로그인만(이메일 가입·Apple 화면 삭제) / 쇼핑몰은 로고 대신 이름 글자, 예시는 지원 후보로 / 유사도는 % 대신 `매우 비슷`·`비슷` / 하단 탭 3번째 이름은 `쇼핑`. 문서 반영: progress.md 결정 사항, 00-overview.md·architecture.md·shop-browser.md 탭 이름, similar-search.md 유사도 표시
4. **테마·사이트맵·미리 입어보기**: 테마는 라이트/다크 두 가지(처음엔 기기 설정). 사용자가 "고른 옷으로 개인 마네킹, 서버에 저장 안 함, 미리 입어보기"를 제안 → 2D 겹치기(폰, 무료)와 AI 가상 피팅(외부 API, 장당 약 $0.06~0.08)을 비교해 안내했고, 사용자가 **AI 가상 피팅 / 도전 목표 / AI 점원 패널 + 내 옷장 두 곳에서 열기**를 고름. 목업에 사이트맵과 화면 4개(AI 점원 패널, 내 옷장 고르기, 미리 입어보기, 마이)를 넣고 기능 문서 `compare/virtual-fitting.md`를 만듦
5. **사이트맵 시안 채우기**: `시안 없음` 9개를 기능 문서대로 그림. 하는 중에 사용자가 "쇼핑 탭이니 아마존 같은 한국어 지원 대형 사이트도 넣고, 번역은 번외로" 결정 → 쇼핑몰 목록 기준을 바꾸고(progress.md 결정 사항) 목록에 `한국어` / `영어 → 번역` 표시. 시안은 `docs/app-sitemap/`으로 옮겨 CLAUDE.md에서 "앱 화면은 이걸 보고 만든다"로 연결

### 다음 세션 AI에게
- 홈 시안(베이스 / 초안)은 아직 미결정이다. 목업에는 둘 다 있다
- 안드로이드 앱 코드의 탭 이름은 아직 `번역`이다 (`sandbox/mmminsung02/.../ClosetApp.kt`, 다른 팀원 sandbox라 고치지 않음). progress.md 다음 할 일 4번에 적어 둠
- 목업을 다시 찍을 때: `chrome.exe --headless=new --blink-settings=preferredColorScheme=1 --screenshot=<출력.png> --window-size=1150,3300 file:///.../index.html` (1 = 라이트, 0 = 다크. 이 값을 안 주면 PC 설정을 따라가서 다크로 찍힐 수 있다)
- 목업 색은 `:root`(라이트)와 `[data-theme="dark"]`의 변수로만 쓴다. 새 화면에 색 코드를 직접 넣지 말 것
- 가상 피팅 서비스, 하루 횟수, 본인 사진 사용은 미결정이다 (progress.md)
- README.md에 "AI에게 요청"(서로의 AI에게 남기는 규칙·할 일)을 만들고 CLAUDE.md 0-1번에서 읽게 했다. 팀원 AI가 대화 기록을 파일 끝에 덧붙여 남의 세션 칸에 들어가는 일이 두 번 있어서다. 요청을 처리하면 README 표에서 `[x]`로 바꾼다
- 앱 시험: 이 노트북에서 디버그 APK를 빌드해 구글 드라이브로 폰에 설치하는 방식으로 함(사용자 결정). moneyhipo 폰에서 설치·실행 확인. Expo Go는 Kotlin 앱이라 불가. 앱 안 삭제 버튼은 하지 않기로 함(같은 PC 빌드면 덮어쓰기 설치가 됨)

## 2026-11-06 세션 (Copilot SDK, VS Code, moneyhipo)

### 한 줄 요약
DeepFashion-MultiModal 데이터 아카이브의 로컬 확보 사실, 출처, 라이선스 주의사항을 AI 진입 문서와 진행 기록에 남겼다.

### 대화 흐름
1. `DeepFashin_dataset/`의 압축 파일을 확인하고 출처를 [DeepFashion-MultiModal](https://github.com/yumingj/DeepFashion-MultiModal)로 기록했다.
2. 원본 README와 LICENSE에서 비상업 연구용 및 재배포 제한을 확인했다. 평가·학습 적합성은 아직 검증하지 않았으므로 사용 전에 라이선스와 라벨 적합성을 확인하도록 기록했다.

## 2026-11-07 세션 (Claude Code, VS Code, moneyhipo)

### 한 줄 요약
DeepFashion-MultiModal이 이 프로젝트에 맞는지 확인하고, 기능 1 종류 분류를 DeepFashion 100장으로 재서 86%(목표 85%)를 얻었다.

### 대화 흐름
1. **데이터 확인**: 압축 파일 7개의 내용을 봤다. 쇼핑몰 상품 사진 44,096장(대부분 모델 착용), 부위 마스크 12,701장, 소매·소재·무늬 라벨, 설명문, 관절 위치, DensePose. 종류 정답은 파일 이름에서 얻을 수 있고 같은 상품의 여러 각도 사진은 유사 검색 평가에 쓸 수 있다. 색 정답은 없다. 라이선스는 비상업 연구용 + 원본·파생 데이터 배포 금지
2. **사용자 결정**: 가디건·후드 집업은 아우터, 지퍼 없는 후드티는 상의. 앱은 무료·광고 없음·약 1달 운영이라 비상업. 평가는 종류마다 25장씩, DeepFashion과 실제 옷장 사진을 둘 다 쓴다
3. **평가 도구** (`sandbox/moneyhipo/eval/`): 부위 마스크로 해당 옷만 잘라 종류마다 25장을 뽑고(`deepfashion_set.py`), mmminsung02의 `ClipAnalyzer`로 서버 규칙 그대로 정확도·혼동 행렬·색 정확도를 잰다(`eval_category.py`, 묶음 여러 개 가능). 이 PC에 `.venv`가 없어서 새로 만들었다(torch 2.15.0 CPU)
4. **결과**: 86/100 (top 23, bottom 22, outer 21, other 20). 가디건과 원피스·점프수트에서 많이 틀린다 → README 요청 표에 mmminsung02에게 문장 보강 제안
5. **모델 비교** (사용자가 진행 요청): 100장은 오차가 커서 400장 묶음을 더 만들었다. 400장에서는 지금 서버 설정이 **79.8%로 목표 미달**(100장의 86%는 운이 좋았던 쪽). 모델만 바꾸면 FashionCLIP 77.2%, B/16 82.2%로 부족했고, 종류 기준대로 문장을 늘린 v2와 합치니 **FashionCLIP + v2가 88.0%**(속도·크기는 지금과 같음). mmminsung02 코드는 고치지 않고 평가 스크립트 안에서만 바꿔 쟀고, README 제안을 이 결과로 바꿨다
6. **기능 5 단위·사이즈 변환** (사용자가 '겹치지 않는 일 진행'을 고름. 앱은 11/04 기능 3 작업 중간에 멈춰 컴파일 안 되는 상태라 mmminsung02 확인 필요): `sandbox/moneyhipo/units/`에 순수 함수와 테스트 31개. 검수 중 `Only 3 in stock`을 인치로 바꾸는 문제를 찾아, `in` 뒤가 글 끝·문장부호·치수 단어일 때만 인치로 보게 고쳤다. 사이즈 환산표는 초안이라 미결정 사항에 올렸다

### 다음 세션 AI에게
- 실제 옷장 사진 묶음은 아직 없다. `DeepFashin_dataset/eval/closet/`에 사진과 `labels.csv`(file, category, color, source)를 넣고 `eval_category.py DeepFashin_dataset/eval/deepfashion_100 DeepFashin_dataset/eval/closet`으로 잰다
- 기능 1 완료 조건 [평가]는 옷장 사진까지 재기 전이라 체크하지 않았다
- DeepFashion에서 잘라 낸 사진·라벨·결과는 `DeepFashin_dataset/eval/`에만 있다(Git 제외, 라이선스). 같은 seed로 다시 만들면 같은 100장이 나온다

## 2026-11-08 세션 (Claude Code, VS Code, moneyhipo)

### 한 줄 요약
moneyhipo가 집에서 찍은 실제 옷 사진 29장에 종류·색 라벨을 붙여 기능 1 분류를 쟀다. 지금 서버 설정이 종류 82.8%, 색 65.5%였고 FashionCLIP + v2보다 나았다.

### 대화 흐름
1. **사진 확인**: `옷 사진/`에 폴더 4개(겉옷 12, 바지 8, 양말 5, 티셔츠 4). 겉옷 폴더의 셔츠 2장은 종류 기준(2026-11-07)대로 `top`, 양말은 `other`로 라벨을 붙였다. 원본 폴더는 개인 사진이라 `.gitignore`에 넣었고, 평가용 사본과 `labels.csv`는 `DeepFashin_dataset/eval/closet/`에 둔다. 이 PC(노트북)에는 `.venv`와 DeepFashion이 없어 `.venv`를 새로 만들었다(torch 2.15.0 CPU)
2. **평가**: 지금 서버 설정 종류 82.8%·색 65.5%, FashionCLIP + v2 종류 75.9%·색 44.8%. DeepFashion 400장과 순위가 반대다. 29장이라 아직 결론을 내지 않고 README 제안에 적었다. 색은 어두운 옷의 검정 → 남색·회색 오류가 대부분이다. EXIF 회전을 적용해도 결과는 거의 같다

### 다음 세션 AI에게
- 애매했던 색 라벨 4장은 사용자가 확인해 고쳤다: `121556` 가죽 재킷·`121536` 플리스 navy, `121809` 데님 재킷 blue, `121921` 연한 셔츠 white
- 다시 재기: `.venv/Scripts/python sandbox/moneyhipo/eval/eval_category.py DeepFashin_dataset/eval/closet [--model patrickjohncyh/fashion-clip --prompts v2]`
- v2 기타 문장에는 양말이 없다. 양말·모자 같은 소품을 옷장에 넣을지는 정하지 않았다

## 2026-11-10 세션 1 (Claude Code, VS Code, moneyhipo)

### 한 줄 요약
진행 상황을 정리해 알려 주고, 서비스 이름을 **DupeCheck**으로 잠정 결정했다.

### 대화 흐름
1. 진행 상황 질문: 기능 1·2·5는 sandbox에서 구현(검수 전), 기능 3은 서버만 되고 앱은 컴파일 안 되는 상태, 나머지는 구현 전이라고 정리했다. progress.md '현재 상태' 칸("코드는 아직 없음", 8주차)이 실제와 다르다는 점도 알렸다(아직 고치지 않음)
2. 이름 추천: 옷장각, 겹옷, 살까말까, Twinfit, DupeCheck, Wardrobe Mate, Closy 등을 제안했다. 사용자가 **DupeCheck**을 골랐다("묵직해서 좋다", 잠정)
3. **Google OAuth 클라이언트**: 사용자가 Google Cloud 프로젝트 `dupecheck-514310`에서 Android 클라이언트(패키지 `com.team12.closet`, 이 PC 디버그 SHA-1)를 만들었다. 내려받은 JSON이 `installed`(Android)라 Web 클라이언트를 따로 만들게 안내했고(원본·리디렉션 URI 비움), 그 ID를 progress.md와 README 요청에 적었다. `client_secret`은 쓰지 않아 기록하지 않았다
4. **Play 출시 준비**: 사용자가 아버님 개인 개발자 계정으로 Play Console에 앱 DupeCheck(`com.dupecheck.app`, 한국어, 무료)을 만들었다. 대시보드에 12명·14일 비공개 테스트 조건이 떠 있어 필요한 계정으로 확인. '일단 해 달라'고 해서: 개인정보처리방침·계정 삭제 페이지(`docs/legal/`, 사용자 선택으로 GitHub Pages, 연락처 이메일은 OAuth 개발자 연락처), Play 설정 답안 문서, 스토어 아이콘·그래픽 초안을 만들고, 업로드 키(저장소 밖)와 첫 AAB를 만들었다

5. Play Console 항목(콘텐츠 등급: 웹 브라우저 앱 아니요, 데이터 보안, 광고 ID 없음 등)을 사용자와 함께 채우고 첫 AAB를 비공개 테스트에 올렸다 → 같은 날 출시됨. 테스터 모집용 Google 설문지 문구를 만들어 줬다(한 사람이 계정 여러 개로 채우지 말 것)
6. **SP2 발표 덱**: 다른 팀 자료(KNU Hub PDF, 4조 pptx)의 구성을 참고해 15장 덱을 claude.ai Slides 아티팩트로 만들었다. 화면은 `docs/app-sitemap` 시안을 캡처해 썼다

### 다음 세션 AI에게
- applicationId는 아직 임시 `com.team12.closet`. 첫 비공개 테스트 전에 정해야 한다(Play에 올리면 못 바꿈). Play 스토어에 같은 이름이 있는지도 확인
- 앱 화면 문구·시안(`docs/app-sitemap/index.html`)의 앱 이름은 아직 바꾸지 않았다
- 테스터 이메일 정리 끝(11-10 22시): 설문 응답 19개, 중복 1 → 18명. Play Console 업로드용 CSV·OAuth 쉼표 목록은 moneyhipo PC `Downloads\dupecheck-testers.csv`, `dupecheck-oauth-testers.txt`(저장소 밖). 관리형 게시가 켜져 있어 테스터 목록 저장 뒤 게시 개요에서 게시해야 적용된다. 테스터 이메일은 저장소에 올리지 않는다
- SP2 덱 발표자 노트에 대본을 넣었다: 1~8장 윤재건, 9~15장 김민성(시연은 김민성 설명·윤재건 폰 조작). 표지 김민성 학번, 10장 테스터 수, 11장 현황, 12장 시연 3·4번은 발표 전에 채운다
- Play: 답안은 `docs/features/release/play-console-setup.md`. 첫 AAB는 `C:\git_desk\dupecheck-0.1.3-vc4.aab`(저장소 밖, versionCode 4). 다음 업로드는 versionCode 5부터. 업로드 키·비밀번호는 moneyhipo PC `~/.android/dupecheck-upload.*`에만 있다. Android OAuth 클라이언트를 `com.dupecheck.app`으로 하나 더 만들어야 한다
- Google OAuth: Web 클라이언트 `599165364681-s9frbpm05o8nko732c27715j557fuvni.apps.googleusercontent.com`, Android 클라이언트(moneyhipo PC 디버그 SHA-1만 등록). 실제 로그인은 기능 3 앱이 컴파일되면 확인. 다른 PC 빌드는 SHA-1을 더 등록해야 한다

---

## 2026-11-10 세션 (Claude Code, VS Code, mmminsung02)

### 한 줄 요약
멈췄던 기능 3 앱(Google 로그인)을 마무리했다. README 요청대로 applicationId·앱 이름을 바꾸고 이 PC 디버그 SHA-1을 적었다.

### 대화 흐름
1. **"이제 다시 가보자"**: 11-06~11-10 moneyhipo 작업 확인(서비스 이름 DupeCheck, Play 비공개 테스트 출시, Google 로그인 ID 생성, 기능 1 평가, 기능 5). README에 mmminsung02 요청 3개(applicationId, 로그인 ID 확인·SHA-1, CLIP 모델은 보류)
2. **기능 3 앱 마무리**: 로그인 화면 연결, 마이 화면 교체, 테스트 18개 추가. applicationId `com.dupecheck.app`, 앱 이름 DupeCheck. 앱 테스트 58개 통과
3. **검수**: 다음 사람이 로그인하면 앞사람 옷이 잠깐 보이는 문제 → 재현 테스트가 실패하는 것 확인 → ViewModel을 세션마다 새로 만들어 고침 → 59개 통과
4. **Vultr 결제 아직** → 기능 4로. FAISS 대신 numpy로 전부 비교(사용자 선택). 시안 7번에 맞춰 단계를 3개(매우 비슷/비슷/조금 다름)로. 서버 87개, 앱 65개 통과. 단계 기준 점수는 초안, Recall@3 정답 세트를 moneyhipo에게 요청(README)
5. **기능 8 (11-11 새벽까지)**: Claude API 키는 받았다고 함(키는 서버 환경변수로만, 채팅·저장소에 올리지 않기로 안내). 서버 `/v1/shops` + 앱 홈·쇼핑몰 목록·브라우저. 테스트 실패 4개는 모두 테스트 쪽(LazyColumn 스크롤, 같은 글자 두 곳, 화면 대기). 팝업 안 입력칸에서 테스트가 멈춰 '직접 추가'를 화면으로 바꿈. 검수로 '추가 다녀오면 첫 화면' 문제 고침. 앱 86개 통과
6. **기능 7 (11-11)**: 상품 사진은 서버가 받기로(사용자 선택). Claude API는 claude-api 스킬로 최신 SDK 사용법 확인 후 `messages.parse`. 테스트를 구현보다 늦게 쓴 걸 알아차리고 바로 테스트부터 정리함. 실제 저장 페이지로 끝까지 돌려 ZOZOTOWN 배열 JSON-LD 버그를, 검수로 SSRF 문제를 찾아 고침. 서버 126 / 주입 9 / 앱 98 통과

### 다음 세션 AI에게
- 실제 Claude 호출 테스트는 아직 안 돌렸다. 사용자가 이 PC에서 직접 `ANTHROPIC_API_KEY`를 설정해야 한다(키를 채팅·저장소에 받지 않는다)
- 실제 Google 로그인은 아직 못 해 봤다. Android 클라이언트를 `com.dupecheck.app`으로 다시 등록해야 하고(README 요청 표), 폰에서 닿는 서버가 필요하다
- 앱 빌드·테스트는 `sandbox/mmminsung02/tools/run-gradle.ps1`로 (진행 창)

