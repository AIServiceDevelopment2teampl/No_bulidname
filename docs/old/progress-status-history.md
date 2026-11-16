# 지난 '현재 상태' (progress.md에서 옮김)

## 2026-11-10
- 서비스 이름 **DupeCheck**, applicationId `com.dupecheck.app`. **Play 비공개 테스트(Alpha) 출시됨** (11-10 20:34 제출 → 같은 날 승인). 첫 빌드 versionCode 4는 로그인 없음·서버 주소가 개발용이라 옷장 기능은 동작하지 않는다. 테스터 18명 신청(11-10), Play 목록에 등록 → 참여 링크 배포 중 (12명 이상 참여한 날부터 14일)
- 구현(sandbox, 검수 전): 기능 1·2 서버+앱, 기능 3 서버(앱은 컴파일 안 되는 WIP), 기능 5. 구현 전: 기능 4·6·7 등
- 다음: 11-18 SP2 MVP 발표·시연 (발표 덱은 claude.ai 아티팩트 'DupeCheck SP2 MVP 발표', moneyhipo 소유), 서버 HTTPS 배포, 기능 3 앱 마무리

## 2026-11-16 이전 '다음 할 일'
0. **기능 3 Google 로그인 실제 확인** (앱·서버 모두 sandbox에서 완료, 자동 테스트만 통과). 필요한 것: ① Google Cloud에 Android 클라이언트를 패키지 `com.dupecheck.app` + 빌드하는 PC의 디버그 SHA-1로 다시 등록 (mmminsung02 PC `42:B6:6F:0A:35:B8:A5:4D:ED:67:58:DB:B9:FA:2A:FD:C3:CB:2A:F2`, moneyhipo PC는 progress.md 위쪽 값) ② 폰에서 닿는 서버에 `GOOGLE_CLIENT_ID` 설정(Vultr HTTPS 배포) ③ `-PgoogleWebClientId=599165364681-s9frbpm05o8nko732c27715j557fuvni.apps.googleusercontent.com -PserverBaseUrl=https://...`로 빌드해 폰에서 로그인 → 옷 등록 → 로그아웃 → 계정 삭제. `KeystoreSessionStore`(토큰 암호화 저장)는 Robolectric에서 못 돌려서 이때 처음 확인된다
1. **Play Console 개인 개발자 계정 만들기**, 비공개 테스트 테스터 15명 정도 모으기 시작 (14일 테스트가 일정의 병목)
2. 미결정 사항 중 OCR·지원 쇼핑몰 정하기
3. **기술 검증**: 후보 쇼핑몰마다 WebView에서 열림, 로그인·장바구니·결제 화면 진입, JS 주입으로 글자 읽기·바꾸기, JSON-LD Product 유무를 확인하고 결과를 여기에 기록
4. 안드로이드 프로젝트 뼈대 검수 후 `src/android/`로 옮기기 (sandbox에서 생성 완료). 옮기기 전에 3번째 탭 이름을 `번역`에서 `쇼핑`으로 바꾼다 (`ClosetApp.kt`의 `SHOPPING("번역", ...)`, 테스트에 탭 이름이 있으면 같이). **applicationId 정하기** (Play에 올리면 바꿀 수 없음, 서비스 이름과 함께 첫 비공개 테스트 전까지)
5. 평가 데이터 준비 시작: 의류 사진 100~200장 라벨링, 영어·일본어 상품 페이지·캡처 30~50개
6. 기능 1번 [wardrobe/photo-register.md](features/wardrobe/photo-register.md): 서버는 sandbox에서 완료(검수 전). 앱 화면도 sandbox에서 완료(검수 전, 실제 폰 미확인). 남은 것: **[평가]** DeepFashion으로 쟀다(2026-11-07): 지금 서버 설정 79.8%(400장), FashionCLIP + v2 문장이면 88.0%. 실제 옷장 사진 29장(2026-11-08): 지금 서버 설정 82.8%(색 65.5%), FashionCLIP + v2 75.9%(색 44.8%)로 순위가 반대. **옷장 사진을 더 찍어(특히 상의, 팀원 옷도) 모델을 정하기**, 색 정확도 올릴 방법 찾기 (`sandbox/moneyhipo/eval/README.md`). 기능 2번은 sandbox에서 완료(검수 전, 오프라인 캐시 남음). 기능 3은 sandbox에서 완료(실제 로그인 미확인, 0번). 기능 4는 sandbox에서 완료, 남은 것: **[평가] Recall@3 정답 세트**(상품 사진 + 사람이 고른 비슷한 내 옷)와 단계 기준 점수. 그다음 기능 6번부터 순서대로
