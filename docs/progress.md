# 진행 상황

## 현재 상태
- 개발용 기획 문서 작성 완료 (00-overview, architecture, features 14개). 코드는 아직 없음
- 지금은 8주차(1차 Sprint)로 추정: UI/UX 설계, 안드로이드 프로젝트 구성, 기술 검증 단계

## 작업 로그
| 날짜 | 작업 | 비고 |
|------|------|------|
| 2026-10-07 | 프로젝트 뼈대 생성 | CLAUDE.md, docs/ 템플릿 |
| 2026-10-14 | 팀 프로젝트 기획서 제출 | `팀플기획서-12조 (1).pdf` |
| 2026-10-21 | 개발용 기획 문서 작성 | 아이디어_상세.md, 추가 idea.txt, 제출 기획서, UI 시안을 합쳐 정리. 원본은 수정하지 않음 |
| 2026-10-21 | 기능 문서를 종류별 하위 폴더로 분리 | wardrobe, auto-register, translation, shopping, compare, release |
| 2026-10-28 | 쇼핑몰 상품 페이지 확인 (curl, 모바일 UA) | ASOS·ZOZOTOWN: 403 봇 차단(ZOZOTOWN은 Akamai). 유니클로 일본: 받아짐. JSON-LD는 없지만 페이지 데이터(`window.__PRELOADED_STATE__`)에 소재(`composition`)·세탁(`washingInformation`)·핏 정보가 있고 `og:title`·`og:image`도 있음. 서버가 쇼핑몰 페이지를 직접 가져오는 방식은 막히므로 WebView 주입 스크립트로 읽는 설계가 맞다 |
| 2026-10-28 | ZOZOTOWN 상품 페이지 확인 (`docs/site_html/zozotown.html`, 개발자 도구 콘솔에서 `copy(document.documentElement.outerHTML)`로 복사. "웹페이지, 전체" 저장은 실패하며 파일이 지워짐) | **JSON-LD `Product` 있음**, 색상별로 하나씩: 상품명, 이미지, 브랜드, 색, 가격. 소재·세탁·두께·신축성은 `description` 안의 일본어 자유 글(`【Material】表地：...手洗い可`)에 섞여 있어 LLM으로 정리해야 함. 페이지 본문 `素材` 항목에도 소재가 있음 |
| 2026-10-28 | 사용자가 쇼핑몰을 직접 추가하는 기능을 넣기로 함 | [shop-browser.md](features/shopping/shop-browser.md)에 요구사항 추가 |
| 2026-10-28 | 브라우저로 저장한 ASOS 상품 페이지 확인 (`docs/site_html/`) | **JSON-LD `Product` 있음**: 상품명, 색, 이미지, 브랜드, 설명(핏 포함, 예: "Regular fit"), 가격·통화(USD). 소재·세탁·모델 키는 `window.asos.pdp.config` 안 `aboutMe`·`careInfo`·`sizeAndFit`에 있음. ZOZOTOWN은 저장이 끝나지 않아 다시 해야 함 (Ctrl+S에서 "웹페이지, HTML만"으로 저장 권장). 저장한 페이지의 부속 파일 폴더(`*_files/`)는 `.gitignore`에 넣고 HTML만 올림 |
| 2026-10-28 | 서버 뼈대·테스트 환경 생성 | `src/server/`(FastAPI, `/health`), `tests/server/`(pytest), `tests/inject/`(Node 내장 테스트 + jsdom), ruff 설정(`pyproject.toml`). 실행: `python -m venv .venv` → `.venv/Scripts/pip install -r src/server/requirements-dev.txt` → `.venv/Scripts/python -m pytest`, `npm install` → `npm run test:inject`. 안드로이드 프로젝트는 이 PC에 Android Studio가 없어 아직 못 만듦 |
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
| 2026-10-28 | 서버 호스팅은 **AWS Lightsail** (서울 리전, 4GB 요금제 예정) | 학교 지원금으로 받기로 함. 월 고정 요금이라 예산 관리가 쉽다. 비공개 테스트 때문에 11주차 안에 배포 |
| 2026-10-28 | 서버 호스팅을 **Vultr** (서울 리전, 선불 크레딧)로 바꾼다. 위 Lightsail 결정을 대신함 | 학교가 AWS는 후불제라 지원할 수 없다고 함. Vultr는 크레딧을 미리 충전해 차감한다. 수업 프로젝트라 서버는 첫 비공개 테스트 직전(약 11/10)부터 15주차 평가(약 12/13)까지만 켜고, 그 전에는 각자 컴퓨터에서 개발한다. 신청액 약 $40(4GB 5주 약 $30 + 여유). 학교 카드 해외 결제와 인보이스 정산 가능 여부 확인 필요. 서버를 지울 때 스토어 앱을 어떻게 할지(비공개 전환 등) 팀에서 정한다 |
| 2026-10-28 | minSdk **26** (Android 8.0) | 거의 모든 폰을 지원하고 최신 라이브러리를 쓰기에 문제없다 |
| 2026-10-28 | 계정은 **Google 로그인** | 폰을 바꿔도 옷장이 유지된다. Play 정책상 앱 안과 웹 두 곳에 계정 삭제가 필요해서 기능 문서 [account/google-login.md](features/account/google-login.md)를 추가하고 기능 순서 3번에 넣었다. 이메일·Apple 로그인은 넣지 않는다 |
| 2026-10-28 | 안드로이드 테스트는 Gradle 기본 위치(`src/android/app/src/test`)에 둔다. 서버·주입 JS 테스트는 `tests/` | Android Studio에서 바로 돌릴 수 있다. CLAUDE.md 규칙 5번에 예외로 적음 |
| 2026-10-28 | 코딩 규칙: Kotlin은 **ktlint**, Python은 **ruff** | 누가 작성해도 코드 모양이 같아진다. CLAUDE.md 코딩 규칙에 적음 |

## 제출 기획서와 달라진 점 (Sprint 발표에서 설명할 것)
| 항목 | 제출 기획서 (10/14) | 현재 | 이유 |
|------|---------------------|------|------|
| 쇼핑 화면 연동 | 공유 메뉴 / 오버레이 번역 (필수) | 인앱 브라우저 그 자리 번역 + 공유하기 | 위 결정 사항 참고 |
| 옷 등록 | 사진 업로드 | + 주문내역 캡처, 앱 내 구매 감지, 영수증 | 등록 부담이 가장 큰 사용성 문제 |
| AI 점원 | 없음 | 추가 | 옷을 잘 모르는 사용자 지원 |
| 사이즈표 변환 | 장기 확장 | 단위·사이즈 변환은 MVP | 비용이 작고 효용이 바로 보임 |
| 배포 | Firebase App Distribution | Google Play 정식 출시 (Firebase는 팀 내부 테스트용으로만) | 팀 목표 |

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
| 서비스 이름 | ClosetLens, 옷장 AI | 스토어 등록 전까지 |
| 홈 화면 시안 | 베이스(2카드), 초안(4카드) | `docs/image/` |
| 번역 방식 | LLM 전부 / 번역 API + LLM(구조화만) | 비용·속도 비교 후 |
| OCR | EasyOCR(서버) / ML Kit(기기) | |
| CLIP 모델 | 일반 CLIP / 패션 특화 CLIP | 평가로 결정 |
| 서버 요금제 | Vultr 4GB(예정) / 2GB | CLIP 메모리 사용량을 재고 결정 |
| 지원 쇼핑몰 목록 | 기준: 많이 쓰이는 해외 쇼핑몰 중 **한국어를 지원하지 않는 곳**. 2026-10-28 직접 확인 결과 한국어 미지원 후보: 영어 **ASOS** (https://www.asos.com/women/), **Urban Outfitters** (https://www.urbanoutfitters.com/), **Abercrombie & Fitch** (https://www.abercrombie.com/, Hollister는 같은 회사라 하나만), **END. Clothing** (https://www.endclothing.com/), **J.Crew** (https://www.jcrew.com/), **Nordstrom** (https://www.nordstrom.com/), **유니클로 미국** (https://www.uniqlo.com/us/en/) / 일본어 **ZOZOTOWN** (https://zozo.jp/), **유니클로 일본** (https://www.uniqlo.com/jp/ja/), **GU 일본** (https://www.gu-global.com/jp/ja/), **BEAMS** (https://www.beams.co.jp/). 한국 직배송(웹 검색, 확인 필요): ASOS 있음(공식 배송 페이지), J.Crew 있음 가능성 높음(한국용 `/kr` 스토어), Urban Outfitters·A&F·END.·Nordstrom 불확실, 유니클로 미국·ZOZOTOWN·유니클로 일본·GU 일본 없음(국내 전용, 배송대행지 필요), BEAMS 자료가 엇갈림. **배송 태그로 표시하고 후보에서 빼지는 않는다** (2026-10-28 결정, [shop-browser.md](features/shopping/shop-browser.md)): `배송 가능` ASOS / `배송대행 필요` 유니클로 미국·ZOZOTOWN·유니클로 일본·GU 일본 / `그 외`(확인 전) J.Crew·Urban Outfitters·A&F·Nordstrom·END.·BEAMS. `그 외`는 결제 화면에서 배송 국가를 한국으로 바꿔 확인하면 옮긴다. Amazon 일본은 한국어 미지원이지만 종합몰이라 기본 목록에서는 빼고 사용자가 직접 추가하게 둔다(초안). 한국어를 지원해서 제외: Amazon(미국), SSENSE, 라쿠텐, UNITED ARROWS, ZARA, H&M | 상품 페이지 구조 확인 완료: ASOS·ZOZOTOWN(JSON-LD 있음), 유니클로 일본(페이지 데이터). 나머지는 앱이 생기면 실제 폰 WebView에서 열림·상품 정보 읽기를 확인하고 기본 목록을 정한다 |
| 쇼핑몰 목록 표시 | 텍스트 이름(초안) / 로고 | 로고는 Play 지식재산권 정책 위험 |

## 다음 할 일 (순서대로)
1. **Play Console 개인 개발자 계정 만들기**, 비공개 테스트 테스터 15명 정도 모으기 시작 (14일 테스트가 일정의 병목)
2. 미결정 사항 중 OCR·지원 쇼핑몰 정하기
3. **기술 검증**: 후보 쇼핑몰마다 WebView에서 열림, 로그인·장바구니·결제 화면 진입, JS 주입으로 글자 읽기·바꾸기, JSON-LD Product 유무를 확인하고 결과를 여기에 기록
4. **Android Studio 설치** (팀) → 안드로이드 프로젝트 생성 (minSdk 26, targetSdk 36, ktlint). 서버 뼈대·테스트 환경은 완료
5. 평가 데이터 준비 시작: 의류 사진 100~200장 라벨링, 영어·일본어 상품 페이지·캡처 30~50개
6. 기능 1번 [wardrobe/photo-register.md](features/wardrobe/photo-register.md)부터 순서대로

## 알려진 위험
[architecture.md](architecture.md)의 '주요 위험' 참고. 가장 큰 두 가지:
- Play 스팸 정책(WebView): 인앱 브라우저 때문에 심사에서 거절될 수 있다. 공유하기 경로가 대안이다
- 비공개 테스트 14일: 11주차 안에 시작하지 못하면 15주차 출시가 어렵다
