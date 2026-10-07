# CLAUDE.md

이 파일은 AI 에이전트가 가장 먼저 읽는 진입점 문서입니다.

## 프로젝트 개요
- 이름: (TODO)
- 한 줄 설명: (TODO)
- 자세한 목표/범위: [docs/00-overview.md](docs/00-overview.md)

## 기술 스택
- 언어: (TODO)
- 프레임워크: (TODO)
- DB: (TODO)
- 테스트: (TODO)

## 문서 안내
작업 전 관련 문서를 반드시 먼저 읽을 것.
- `docs/00-overview.md` — 전체 목표, 범위
- `docs/architecture.md` — 구조, 폴더 규칙, DB 설계
- `docs/features/*.md` — 기능별 요구사항 + 완료 조건
- `docs/progress.md` — 진행 상황, 결정 사항 기록

## 작업 규칙
1. 한 번에 하나의 기능(`docs/features/` 문서 하나) 단위로 작업한다.
2. 기능 문서의 **완료 조건**을 테스트로 먼저 작성하고, 테스트가 통과해야 완료로 본다.
3. 문서에 없는 결정이 필요하면 임의로 정하지 말고 질문한다.
4. 작업이 끝나면 `docs/progress.md`에 한 일과 결정 사항을 업데이트한다.
5. 소스 코드는 `src/`, 테스트는 `tests/` 아래에 둔다.

## 코딩 규칙
- (TODO: 네이밍, 포맷터, 린터 등)
