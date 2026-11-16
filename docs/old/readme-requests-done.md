# 끝난 'AI에게 요청' (README에서 옮김)

README '지금 요청' 표에서 `[x]`가 된 줄을 여기로 옮긴다. 기록용이라 고치지 않는다.

| 받는 사람 | 요청 | 상태 |
|-----------|------|------|
| mmminsung02 | 앱 하단 탭 3번째 이름을 `번역` → **`쇼핑`**으로 바꾸기 (`sandbox/mmminsung02/android/.../ui/ClosetApp.kt`의 `SHOPPING("번역", ...)`, 아이콘도 쇼핑백 계열로). 탭 이름을 확인하는 테스트가 있으면 같이 | [x] |
| mmminsung02 | 앱 테마를 라이트/다크 두 가지로 만들기. 색은 위 '따를 결정'의 시안 값 사용 | [x] |
| mmminsung02 | 화면 시안이 `sandbox/moneyhipo/mockup/`에서 **`docs/app-sitemap/index.html`**로 옮겨졌다. 앱 화면은 이 시안을 보고 만든다 (CLAUDE.md 문서 안내). 확인했으면 `[x]` | [x] |
| mmminsung02 | 앱 버전을 빌드할 때 넘길 수 있게 하기: `versionCode`·`versionName`을 `-PversionCode=`·`-PversionName=`으로 받고, 없으면 지금 값(1, `0.1.0`)을 쓴다 (`serverBaseUrl`과 같은 방식, `app/build.gradle.kts`). 시험용 APK는 moneyhipo가 빌드해 구글 드라이브에 `closet-v0.1.n-<커밋>.apk`로 한 단계씩 올리고 예전 것은 `old/`에 보관한다. 지금 v0.1.1 파일도 앱 안에는 0.1.0으로 찍혀서 맞추려는 것 | [x] |
| mmminsung02 | 경험 저장소 https://github.com/moneyhipo/Personal_AI_Work_Experience_Repository 를 참고하기: 그 저장소 README의 '공유받은 분께'를 먼저 읽고, `general/`(범용)과 `mobile/`(안드로이드·Play 출시)의 **규칙**을 이 프로젝트 작업에 적용한다. **읽기만** 하고 고치거나 푸시하지 않는다. `game/`, `handoff/`는 되도록 보지 않는다. 읽고 나서 지금 작업에 바로 걸리는 규칙이 있으면 사용자에게 알리고, 확인했으면 `[x]` | [x] |
| mmminsung02 | **applicationId가 `com.dupecheck.app`으로 정해졌다** (2026-11-10, Play Console에 앱을 만들어 바꿀 수 없음). 기능 3 앱을 마무리할 때 `app/build.gradle.kts`의 `applicationId`를 바꾸고 TODO 주석을 지운다(`namespace`는 그대로 둬도 된다). 앱 이름 `app_name`도 `DupeCheck`으로. 배포용 서버 주소는 **HTTPS**여야 한다(릴리스 빌드는 `http://`를 막는다). Play 업로드 키는 moneyhipo PC에만 있으니 Play용 AAB는 moneyhipo가 빌드한다. 자세한 건 [play-console-setup.md](docs/features/release/play-console-setup.md) | [x] |
| moneyhipo | **주의: 지금 `sandbox/mmminsung02/android`는 컴파일되지 않는다** (기능 3 로그인 앱 작업 중간, 커밋 `2981181`). 시험용 APK는 그 전 커밋 `0b240cd`로 빌드해 주세요 (`git worktree add ../closet-0b240cd 0b240cd` 등). mmminsung02가 마무리하면 이 줄을 `[x]`로 바꾼다 | [x] |
