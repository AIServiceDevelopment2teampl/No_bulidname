# No_bulidname

국내·해외 쇼핑몰에서 옷을 볼 때 내 옷장 속 비슷한 옷과 바로 비교해 구매 판단을 돕는 안드로이드 앱. 서비스 이름은 2026-11-10에 **DupeCheck**으로 잠정 결정했다(저장소 이름은 그대로). 외국어 페이지는 그 자리에서 한국어로 바꿔 준다. 개요는 [docs/00-overview.md](docs/00-overview.md), AI 작업 규칙은 [CLAUDE.md](CLAUDE.md).

## AI에게 요청 (작업 시작 전에 꼭 읽기)

팀원 두 명(`moneyhipo`, `mmminsung02`)이 각자 AI로 같은 저장소에서 작업한다. 아래는 서로의 AI에게 하는 요청이다. 처리한 요청은 `[x]`로 바꾸고 progress.md 작업 로그에 적는다.

### 항상 지킬 것
1. **대화 기록(`docs/conversation-log.md`)은 내 세션 칸 안에만 쓴다.**
   - 세션을 시작하면 파일 맨 끝에 새 제목을 만든다: `## 2026-11-04 세션 3 (Claude Code, VS Code, <GitHub 아이디>)`. 같은 날 같은 번호가 있으면 다음 번호를 쓴다.
   - 그 세션 안에서 일이 더 생기면 **내 세션 칸**의 `대화 흐름`에 번호를 이어 붙인다. 파일 맨 끝에 그냥 덧붙이면 다른 사람 세션 칸에 들어가니 하지 않는다 (11-04에 두 번 일어남).
2. **바로바로 동기화한다.** 작업을 시작할 때 `git pull --rebase`, 파일을 고치면 작은 단위로 **곧바로 커밋·푸시**한다(푸시 직전에 다시 `git pull --rebase`). 여러 변경을 모아 두었다가 한꺼번에 올리지 않는다. 충돌이 나면 한쪽을 지우지 말고 양쪽 내용을 다 살려 합친다. progress.md·conversation-log.md는 둘 다 자주 고쳐서 충돌이 잦다.
3. **다른 사람 `sandbox/<아이디>/`는 고치지 않는다.** 고쳐야 할 게 보이면 아래 '지금 요청'에 적는다.
4. **결정이 바뀌면 문서부터 고친다.** UI·기능 결정은 progress.md `결정 사항`이 기준이다. 코드와 문서가 다르면 문서를 따르고, 문서가 틀렸다고 생각하면 사용자에게 묻는다.

### 앱 화면을 만들 때 따를 결정 (2026-11-04, 자세한 이유는 progress.md)
- 로그인은 **Google 로그인만**. 이메일·비밀번호·회원가입·Apple 화면은 만들지 않는다.
- 하단 탭: **홈 / 내 옷장 / 쇼핑 / 마이**. 쇼핑 탭은 쇼핑몰 목록으로 바로 간다.
- 쇼핑몰은 로고 대신 **이름 글자**로 보여 준다. **국내 쇼핑몰(무신사 등)과 한국어를 지원하는 대형 해외 쇼핑몰(Amazon, ZARA 등)도** 목록에 넣는다. **번역은 부가 기능**이라 한국어가 아닌 페이지에서만 켜진다.
- 유사도는 숫자(%)를 보여 주지 않고 **`매우 비슷` / `비슷`** 같은 단계로 보여 준다.
- 테마는 **라이트 / 다크** 두 가지. 처음에는 기기 설정을 따르고 마이에서 바꾼다. 색 값은 화면 시안 [docs/app-sitemap/index.html](docs/app-sitemap/index.html) 맨 위 `:root`(라이트)와 `[data-theme="dark"]`(다크)에 모여 있다. 주 색은 라이트 `#5B5CEB`, 다크 `#8E8FFF`.
- 화면 배치는 위 시안을 기준으로 한다. 시안에 없는 화면은 사이트맵(시안 맨 위)에서 위치를 확인한다.

### 지금 요청
| 받는 사람 | 요청 | 상태 |
|-----------|------|------|
| mmminsung02 | 앱 하단 탭 3번째 이름을 `번역` → **`쇼핑`**으로 바꾸기 (`sandbox/mmminsung02/android/.../ui/ClosetApp.kt`의 `SHOPPING("번역", ...)`, 아이콘도 쇼핑백 계열로). 탭 이름을 확인하는 테스트가 있으면 같이 | [x] |
| mmminsung02 | 앱 테마를 라이트/다크 두 가지로 만들기. 색은 위 '따를 결정'의 시안 값 사용 | [x] |
| mmminsung02 | 화면 시안이 `sandbox/moneyhipo/mockup/`에서 **`docs/app-sitemap/index.html`**로 옮겨졌다. 앱 화면은 이 시안을 보고 만든다 (CLAUDE.md 문서 안내). 확인했으면 `[x]` | [x] |
| mmminsung02 | 앱 버전을 빌드할 때 넘길 수 있게 하기: `versionCode`·`versionName`을 `-PversionCode=`·`-PversionName=`으로 받고, 없으면 지금 값(1, `0.1.0`)을 쓴다 (`serverBaseUrl`과 같은 방식, `app/build.gradle.kts`). 시험용 APK는 moneyhipo가 빌드해 구글 드라이브에 `closet-v0.1.n-<커밋>.apk`로 한 단계씩 올리고 예전 것은 `old/`에 보관한다. 지금 v0.1.1 파일도 앱 안에는 0.1.0으로 찍혀서 맞추려는 것 | [x] |
| mmminsung02 | 경험 저장소 https://github.com/moneyhipo/Personal_AI_Work_Experience_Repository 를 참고하기: 그 저장소 README의 '공유받은 분께'를 먼저 읽고, `general/`(범용)과 `mobile/`(안드로이드·Play 출시)의 **규칙**을 이 프로젝트 작업에 적용한다. **읽기만** 하고 고치거나 푸시하지 않는다. `game/`, `handoff/`는 되도록 보지 않는다. 읽고 나서 지금 작업에 바로 걸리는 규칙이 있으면 사용자에게 알리고, 확인했으면 `[x]` | [x] |
| mmminsung02 | **applicationId가 `com.dupecheck.app`으로 정해졌다** (2026-11-10, Play Console에 앱을 만들어 바꿀 수 없음). 기능 3 앱을 마무리할 때 `app/build.gradle.kts`의 `applicationId`를 바꾸고 TODO 주석을 지운다(`namespace`는 그대로 둬도 된다). 앱 이름 `app_name`도 `DupeCheck`으로. 배포용 서버 주소는 **HTTPS**여야 한다(릴리스 빌드는 `http://`를 막는다). Play 업로드 키는 moneyhipo PC에만 있으니 Play용 AAB는 moneyhipo가 빌드한다. 자세한 건 [play-console-setup.md](docs/features/release/play-console-setup.md) || [x] |
| mmminsung02 | **Google 로그인 ID가 생겼다** (2026-11-10, moneyhipo). Web 클라이언트 ID `599165364681-s9frbpm05o8nko732c27715j557fuvni.apps.googleusercontent.com` → 앱 빌드 `-PgoogleWebClientId=`, 서버 `GOOGLE_CLIENT_ID`. 코드는 고칠 필요 없다. 기능 3 앱을 마무리하면 이 값으로 실제 로그인을 확인해 달라. mmminsung02 PC에서 빌드한 APK는 그 PC 디버그 키 SHA-1을 Android 클라이언트로 등록해야 로그인이 되니, SHA-1을 이 표에 적어 주면 moneyhipo가 등록한다 (progress.md 다음 할 일 0번) | **(mmminsung02, 2026-11-10) 기능 3 앱 마무리함. 이 PC 디버그 키 SHA-1: `42:B6:6F:0A:35:B8:A5:4D:ED:67:58:DB:B9:FA:2A:FD:C3:CB:2A:F2`.** applicationId가 `com.dupecheck.app`으로 바뀌어서, Android 클라이언트는 **패키지 `com.dupecheck.app`**으로 이 SHA-1과 moneyhipo PC SHA-1 각각 다시 만들어야 한다(지금 등록된 건 `com.team12.closet`). 실제 로그인 확인은 ① 위 등록 ② 폰에서 닿는 서버(`GOOGLE_CLIENT_ID` 설정) 뒤에 한다. 그때까지 열어 둔다 | [ ] |
| mmminsung02 | (moneyhipo, 2026-11-11) **전체 테스트는 아래 4단계로 한다.** ① Haiku 5.5(`claude-haiku-5-5`, effort **max**)로 1차 정보 수집: 테스트 실행 결과·실패·의심 지점을 모은다 ② Sonnet 5.5(`claude-sonnet-5-5`, effort **high**)가 ①을 검수해 틀린 것·빠진 것을 거른다 ③ Fable 5.2(`claude-fable-5-2`, 이번에 나온 모델)가 ②를 보고 **수정 계획서**를 쓴다 ④ Opus 5.5(`claude-opus-5-5`, effort **high**)가 계획서대로 고친다. **전체 테스트는 AI가 임의로 시작하지 말고 매번 사용자(mmminsung02)에게 허락을 받은 뒤** 한다. 평소에는 고친 부분과 관련된 테스트만 돌린다. 확인했으면 `[x]` | [ ] |
| mmminsung02 | (moneyhipo, 2026-11-11) **로그인 화면에 `게스트로 둘러보기`를 넣었다** (네 sandbox 앱을 moneyhipo가 직접 고침, 커밋 메시지 "guest"). Vultr 서버가 아직 없어 시연·테스터가 로그인 화면을 못 넘어가서다. 서버 없이 폰에만 게스트 세션(`Session.guest`)을 만들고, 게스트는 세션 토큰을 보내지 않는다. 바뀐 파일: `Session.kt`·`AuthRepository.kt`(`loginAsGuest`)·`LoginScreen.kt`·`MyScreen.kt`·`ClosetApp.kt`(미리보기)·테스트 3개. 네 작업과 겹치면 맞춰 고쳐 주고, 서버가 생기면 게스트를 서버 계정으로 바꿀지 사용자와 정한다. 확인했으면 `[x]` | [ ] |
| moneyhipo | **주의: 지금 `sandbox/mmminsung02/android`는 컴파일되지 않는다** (기능 3 로그인 앱 작업 중간, 커밋 `2981181`). 시험용 APK는 그 전 커밋 `0b240cd`로 빌드해 주세요 (`git worktree add ../closet-0b240cd 0b240cd` 등). mmminsung02가 마무리하면 이 줄을 `[x]`로 바꾼다 || [x] |
| moneyhipo | 기능 1 분류 평가용 **의류 사진 100장 + 정답 라벨(종류·색)** 준비 (mmminsung02 요청). **2026-11-07 진행**: DeepFashion 100장(종류마다 25장, 색 정답 없음)으로 종류 정확도 86% 측정. **2026-11-08 진행**: 실제 옷장 사진 29장(종류·색 라벨)으로 종류 82.8%, 색 65.5%. 남은 것: 옷장 사진을 더(특히 상의, mmminsung02 옷도) 찍어 종류마다 같은 수로 맞추기. 넣는 법은 `sandbox/moneyhipo/eval/README.md` | [ ] |
| moneyhipo | (mmminsung02 요청, 2026-11-10) **기능 4 유사 검색 평가용 정답 세트**: 상품 사진 20~30장과 각 사진마다 사람이 고른 '비슷한 내 옷' 1~3벌 (종류가 같은 옷 중에서). DeepFashion의 같은 상품 다른 각도 사진을 쓰면 정답을 사람 손 없이 만들 수 있다(평가에만, 저장소에 올리지 않음). 이걸로 Recall@3과 단계 기준 점수(지금 초안: 매우 비슷 0.90, 비슷 0.80, `sandbox/mmminsung02/src/server/config.py`)를 정한다. 평가 스크립트는 `sandbox/moneyhipo/eval/`처럼 만들어 주거나, 데이터만 주면 mmminsung02가 만든다 | [ ] |
| moneyhipo | (mmminsung02, 2026-11-11) **기능 5 단위 변환을 기능 7에서 쓰기 시작함**: `sandbox/mmminsung02/src/server/ai/units.py`가 `sandbox/moneyhipo/units/units.py`의 `convert_text`를 파일 경로로 불러온다(sandbox끼리라 임시). 두 가지 부탁: ① `25 in 가슴너비`·`25 in 身幅`처럼 `in` 바로 뒤에 **한글·일본어 단어**가 오면 지금은 단위로 안 본다. 그 언어에서 `in`은 전치사가 아니니 단위로 봐도 된다 (LLM이 번역한 한국어 문장에 변환을 걸기 때문에 자주 생긴다. 지금 테스트는 `Length: 25 in, Chest: 21 in`처럼 쉼표가 있는 영어로 맞춰 둠) ② 검수가 끝나면 `src/server/ai/`로 옮겨 주면 경로 불러오기를 지운다 | [ ] |
| mmminsung02 | (제안) **CLIP 모델과 종류 문장 바꾸기 검토**: DeepFashion 400장에서 지금 서버 설정(일반 CLIP B/32 + 지금 문장)은 **79.8%로 목표 85% 미달**. `patrickjohncyh/fashion-clip`(같은 크기·속도, MIT) + `sandbox/moneyhipo/eval/eval_category.py`의 `PROMPTS_V2` 문장(`"a photo of " + 각 항목`, 확률을 종류별로 더하는 지금 방식 그대로)이면 **88.0%**. 자세한 비교는 progress.md 작업 로그 2026-11-07. 바꾸면 `clip_analyzer.py`의 `DEFAULT_MODEL`과 `CATEGORY_PROMPTS`만 고치면 된다. 종류 기준은 progress.md 결정 사항 2026-11-07. 실제 옷장 사진으로 확인한 뒤 정해도 된다. **2026-11-08 옷장 사진 29장에서는 반대**: 지금 설정 82.8% > FashionCLIP + v2 75.9%(색도 65.5% > 44.8%). 장수가 적어 **아직 바꾸지 말고** 사진이 더 모이면 다시 비교한다 | [ ] |
