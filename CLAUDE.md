# CLAUDE.md

이 파일은 AI 에이전트가 가장 먼저 읽는 진입점 문서입니다.

## 프로젝트 개요
- 이름: **DupeCheck** (2026-11-10). applicationId **`com.dupecheck.app`** (Play Console에 앱을 만듦, 바꿀 수 없음). Play 설정 답안은 [docs/features/release/play-console-setup.md](docs/features/release/play-console-setup.md)
- 한 줄 설명: 국내·해외 쇼핑몰에서 옷을 볼 때 내 옷장 속 비슷한 옷과 바로 비교해 구매 판단을 돕는 안드로이드 앱. 외국어 페이지는 그 자리에서 한국어로 바꿔 준다(부가 기능)
- 자세한 목표/범위: [docs/00-overview.md](docs/00-overview.md)
- 제출한 팀 기획서: `팀플기획서-12조 (1).pdf` (인공지능서비스개발II, 2026-10-14). 팀원 개인 정보가 있어 저장소에 올리지 않음(로컬에만 있음). 달라진 점과 PDF 검토 결과는 [docs/progress.md](docs/progress.md)에 기록

## 기술 스택
- 언어: Kotlin (앱), Python (서버)
- 프레임워크: Jetpack Compose (Android Studio, targetSdk 36 이상), FastAPI, Retrofit
- AI: CLIP, FAISS, OCR(미결정), Claude API (우선 Claude Haiku 5.5)
- DB: 서버 SQLite, 앱 Room
- 테스트: pytest (서버), Node + jsdom (WebView 주입 JS), JUnit·Compose UI 테스트 (앱)
- 배포: Google Play (비공개 테스트 → 프로덕션), 서버는 Vultr (서울 리전, 선불 크레딧)

## 데이터셋
- DeepFashion-MultiModal 압축 파일을 로컬 `DeepFashin_dataset/`에 확보했다. 폴더는 `.gitignore` 대상이며 저장소에 추가하지 않는다.
- 출처: [DeepFashion-MultiModal](https://github.com/yumingj/DeepFashion-MultiModal)
- 저장소의 README와 LICENSE에 따르면 비상업 연구용이며 원본·파생 데이터의 복사·배포가 금지다. **평가에만 쓴다**: 사진·잘라 낸 사진·라벨을 Git·공개 드라이브에 올리지 않고, 이 데이터로 학습한 모델을 앱·서버에 넣지 않는다 (progress.md 결정 사항 2026-11-07).
- 적합성 확인 결과(2026-11-07): 종류 정답(파일 이름의 상품 종류)과 부위 마스크는 있고, **색 정답은 없다**. 대부분 모델이 입은 쇼핑몰 사진이다. 같은 상품의 여러 각도 사진이 있어 유사 검색(Recall@3) 평가에도 쓸 수 있다. 기능 1 평가 도구와 사용법: `sandbox/moneyhipo/eval/README.md`. 필요 없는 `densepose.zip의 사본`(6GB)은 지워도 된다.

## 문서 안내
작업 전 관련 문서를 반드시 먼저 읽을 것.
- `docs/00-overview.md` — 전체 목표, 범위, 기능 목록, 일정
- `docs/architecture.md` — 구조, 폴더 규칙, API, DB 설계, 위험
- `docs/features/<종류>/*.md` — 기능별 요구사항 + 완료 조건 (종류 목록: `docs/features/README.md`)
- `docs/progress.md` — 진행 상황, 결정 사항, 미결정 사항, 다음 할 일
- `docs/conversation-log.md` — 세션별 대화 기록 (이어서 작업할 때 맥락 확인용)
- `docs/sp2-plan.md` — SP2 과제 제출용 MVP·완성본 구현 계획 (10/28 제출)
- `docs/app-sitemap/index.html` — **앱 화면 시안 + 사이트맵** (브라우저로 열기). **앱 화면을 만들 때는 반드시 이 파일을 먼저 보고 그대로 만든다.** 화면 배치, 문구, 하단 탭, 라이트/다크 테마 색(맨 위 `:root`와 `[data-theme="dark"]`)이 들어 있다. 시안에 없는 화면은 맨 위 사이트맵에서 위치를 확인하고, 새로 정해야 할 게 있으면 사용자에게 묻는다. 시안을 고치면 이 파일을 고친다

원본 자료 `아이디어_상세.md`, `추가 idea.txt`, 제출 기획서 PDF는 수정하지 않는다.

## 작업 규칙
0. 작업 전에 경험 저장소 https://github.com/moneyhipo/Personal_AI_Work_Experience_Repository 의 README부터 읽고 따른다. 단 **읽는 곳은 `general/`과 `mobile/`만**이고(`game/`, `handoff/`는 개인 프로젝트 기록이라 되도록 보지 않음), 그 저장소는 **읽기만 하고 고치거나 푸시하지 않는다**. 추가할 교훈은 이 저장소 README의 'AI에게 요청' 표에 적어 moneyhipo에게 부탁한다. 채팅 답변은 한국어로 한다.
0-1. `git pull` 다음에 이 저장소 [README.md](README.md)의 **'AI에게 요청'**을 읽는다. 팀원끼리 서로의 AI에게 남긴 규칙과 할 일이 있다. 나에게 온 요청이 있으면 사용자에게 알린다.
1. 한 번에 하나의 기능(`docs/features/` 문서 하나) 단위로 작업한다.
2. 기능 문서의 **완료 조건**을 테스트로 먼저 작성하고, 테스트가 통과해야 완료로 본다.
3. 문서에 없는 결정이 필요하면 임의로 정하지 말고 질문한다.
4. 작업이 끝나면 `docs/progress.md`에 한 일과 결정 사항을 업데이트한다.
5. 소스 코드는 `src/`, 테스트는 `tests/` 아래에 둔다. 예외: 안드로이드 앱 테스트는 Gradle 기본 위치(`src/android/app/src/test`, `src/android/app/src/androidTest`)에 둔다.
6. 작업을 시작하기 전에 `git fetch`로 GitHub 변경사항을 확인하고, 있으면 받는다(`git pull`). 받은 변경 내용은 사용자에게 요약한다.
7. 작업이 끝나면 `docs/progress.md`와 `docs/conversation-log.md`에 기록하고 커밋·푸시한다.
8. 각자의 작업물은 먼저 `sandbox/<GitHub 아이디>/`(`moneyhipo`, `mmminsung02`)에서 만들고, 검수한 뒤 `src/`·`tests/`로 옮긴다. `sandbox/` 코드는 완성본으로 보지 않는다. 자세한 규칙은 [sandbox/README.md](sandbox/README.md)

## 코딩 규칙
- Kotlin: ktlint로 포맷·검사한다.
- Python: ruff로 포맷·검사한다.
- 커밋 전에 두 도구를 통과시킨다.
