# CLAUDE.md

이 파일은 AI 에이전트가 가장 먼저 읽는 진입점 문서입니다.

## 프로젝트 개요
- 이름: 미결정 (후보: ClosetLens, 옷장 AI)
- 한 줄 설명: 해외 쇼핑몰 상품 정보를 그 자리에서 한국어로 번역하고, 내 옷장 속 비슷한 옷과 비교해 구매 판단을 돕는 안드로이드 앱
- 자세한 목표/범위: [docs/00-overview.md](docs/00-overview.md)
- 제출한 팀 기획서: `팀플기획서-12조 (1).pdf` (인공지능서비스개발II, 2026-10-14). 팀원 개인 정보가 있어 저장소에 올리지 않음(로컬에만 있음). 달라진 점과 PDF 검토 결과는 [docs/progress.md](docs/progress.md)에 기록

## 기술 스택
- 언어: Kotlin (앱), Python (서버)
- 프레임워크: Jetpack Compose (Android Studio, targetSdk 36 이상), FastAPI, Retrofit
- AI: CLIP, FAISS, OCR(미결정), Claude API (우선 Claude Haiku 5.5)
- DB: 서버 SQLite, 앱 Room
- 테스트: pytest (서버), Node + jsdom (WebView 주입 JS), JUnit·Compose UI 테스트 (앱)
- 배포: Google Play (비공개 테스트 → 프로덕션), 서버는 AWS Lightsail (서울 리전)

## 문서 안내
작업 전 관련 문서를 반드시 먼저 읽을 것.
- `docs/00-overview.md` — 전체 목표, 범위, 기능 목록, 일정
- `docs/architecture.md` — 구조, 폴더 규칙, API, DB 설계, 위험
- `docs/features/<종류>/*.md` — 기능별 요구사항 + 완료 조건 (종류 목록: `docs/features/README.md`)
- `docs/progress.md` — 진행 상황, 결정 사항, 미결정 사항, 다음 할 일
- `docs/conversation-log.md` — 세션별 대화 기록 (이어서 작업할 때 맥락 확인용)

원본 자료 `아이디어_상세.md`, `추가 idea.txt`, 제출 기획서 PDF는 수정하지 않는다.

## 작업 규칙
0. 작업 전에 경험 저장소 https://github.com/moneyhipo/AI_Experience-repository 의 README부터 읽고 따른다. 채팅 답변은 한국어로 한다.
1. 한 번에 하나의 기능(`docs/features/` 문서 하나) 단위로 작업한다.
2. 기능 문서의 **완료 조건**을 테스트로 먼저 작성하고, 테스트가 통과해야 완료로 본다.
3. 문서에 없는 결정이 필요하면 임의로 정하지 말고 질문한다.
4. 작업이 끝나면 `docs/progress.md`에 한 일과 결정 사항을 업데이트한다.
5. 소스 코드는 `src/`, 테스트는 `tests/` 아래에 둔다.
6. 작업을 시작하기 전에 `git fetch`로 GitHub 변경사항을 확인하고, 있으면 받는다(`git pull`). 받은 변경 내용은 사용자에게 요약한다.
7. 작업이 끝나면 `docs/progress.md`와 `docs/conversation-log.md`에 기록하고 커밋·푸시한다.

## 코딩 규칙
- (TODO: 네이밍, 포맷터, 린터 등)
