// DupeCheck WebView 주입 스크립트 진입점 (shopping/shop-browser.md).
// 앱이 페이지 로드마다 한 번 넣는다. 번역(기능 6)·상품 감지(기능 7)·주문 감지(기능 14)가 여기에 붙는다.
// 로그인·결제 페이지에는 앱이 아예 넣지 않는다 (PageInjector.kt의 SENSITIVE_URL).
(function () {
  if (window.__dupecheck) {
    // 같은 문서에 또 들어오면 아무것도 하지 않는다 (중복 주입 방지의 두 번째 안전장치)
    window.__dupecheck.repeated += 1;
    return;
  }
  window.__dupecheck = { version: 1, repeated: 0, startedAt: Date.now() };
})();
