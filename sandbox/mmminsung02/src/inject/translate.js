// 페이지 그 자리 번역 (translation/page-translation.md).
// 화면의 텍스트 노드를 모아 앱 다리(DupeCheckBridge.translate)로 보내고, 앱이 apply()로 돌려주면 그 자리에서 바꾼다.
// 원문 보기·다시 번역, 나중에 생긴 글자(MutationObserver) 번역, 결제·로그인 주소에서는 보내지 않음.
(function () {
  // 서버 상한은 200이지만 작게 나눠 동시에 보내야 첫 화면이 빨리 바뀐다 (200개 한 번에: 16.7초)
  var MAX_PER_REQUEST = 40;
  var DEBOUNCE_MS = 300;
  var MAIN_AREA = 'main, article, h1, [role="main"], [itemtype*="Product"], [itemprop]';
  var HIDDEN = '[hidden], [aria-hidden="true"], dialog:not([open])';
  // 앱 PageInjector.kt와 같은 목록. 한 화면 안에서 주소만 바뀌는 사이트(SPA)도 있어 보낼 때마다 다시 본다
  var SENSITIVE_URL =
    /(login|signin|sign-in|sign_in|logon|\/auth[\/?]|account\/password|checkout|payment|\/pay[\/?]|billing|order-confirm)/i;
  var SKIP =
    'script, style, noscript, textarea, select, option, code, pre, template, ' +
    '[contenteditable=""], [contenteditable="true"], [translate="no"], .notranslate';
  var LETTER = /\p{L}/u;
  var HANGUL = /[가-힣]/g;
  var LETTERS = /\p{L}/gu;

  var originals = new Map(); // 텍스트 노드 → 원문
  var shown = new Map(); // 텍스트 노드 → 우리가 넣은 번역문
  var cache = new Map(); // 원문(앞뒤 공백 뺌) → 번역문
  var waiting = new Map(); // 요청 번호 → 보낸 문장들
  var queued = new Set(); // 아직 안 보낸 텍스트 노드
  var showingTranslation = true;
  var started = false;
  var failed = false;
  var nextId = 1;
  var timer = null;
  var observer = null;

  function bridge() {
    return window.DupeCheckBridge;
  }

  function pageLang() {
    var lang = (document.documentElement.getAttribute("lang") || "").toLowerCase();
    return lang.split("-")[0] || "unknown";
  }

  function isKoreanPage() {
    if (pageLang() === "ko") return true;
    var text = document.body ? document.body.textContent || "" : "";
    var letters = (text.match(LETTERS) || []).length;
    var hangul = (text.match(HANGUL) || []).length;
    return letters > 0 && hangul / letters > 0.5;
  }

  function sensitive() {
    return SENSITIVE_URL.test(location.href);
  }

  function report(status) {
    var b = bridge();
    if (b && b.onTranslateState) b.onTranslateState(JSON.stringify({ status: status, lang: pageLang() }));
  }

  function wanted(node) {
    var text = node.nodeValue.trim();
    if (!text || !LETTER.test(text)) return false; // 빈 글자, 숫자·가격·기호
    var hangul = (text.match(HANGUL) || []).length;
    var letters = (text.match(LETTERS) || []).length;
    if (hangul > 0 && hangul / letters > 0.5) return false; // 이미 한국어
    var parent = node.parentElement;
    return !!parent && !parent.closest(SKIP);
  }

  function textNodes(root) {
    var out = [];
    var walker = document.createTreeWalker(root, 4 /* NodeFilter.SHOW_TEXT */);
    while (walker.nextNode()) out.push(walker.currentNode);
    return out;
  }

  /** 앞뒤 공백은 그대로 두고 가운데 글자만 바꾼다 */
  function withSpacing(original, translated) {
    var lead = original.match(/^\s*/)[0];
    var trail = original.match(/\s*$/)[0];
    return lead + translated + trail;
  }

  function setText(node, value) {
    if (node.nodeValue !== value) node.nodeValue = value;
  }

  function showTranslated(node) {
    var original = originals.get(node);
    var ko = cache.get(original.trim());
    if (ko === undefined) return false;
    var value = withSpacing(original, ko);
    shown.set(node, value);
    setText(node, value);
    return true;
  }

  function enqueue(node) {
    if (!originals.has(node)) {
      if (!wanted(node)) return;
      originals.set(node, node.nodeValue);
    }
    if (!showTranslated(node)) queued.add(node);
  }

  /**
   * 먼저 보낼 순서: 0 화면에 보임 → 1 본문(상품 영역) → 2 그 밖 → 3 숨김.
   * 폰 WebView는 위치를 알 수 있고, 위치를 모르면(jsdom) 본문 여부로 정한다
   */
  function priority(node) {
    var el = node.parentElement;
    if (!el || el.closest(HIDDEN)) return 3;
    var rect = el.getBoundingClientRect();
    if (rect.height > 0 && rect.bottom > 0 && rect.top < window.innerHeight) return 0;
    return el.closest(MAIN_AREA) ? 1 : 2;
  }

  function send() {
    if (!showingTranslation || sensitive() || queued.size === 0) return;
    var b = bridge();
    if (!b || !b.translate) return;
    var ranked = [];
    queued.forEach(function (node) {
      var t = originals.get(node).trim();
      if (!cache.has(t)) ranked.push({ text: t, rank: priority(node), order: ranked.length });
    });
    ranked.sort(function (a, b) {
      return a.rank - b.rank || a.order - b.order;
    });
    var texts = [];
    ranked.forEach(function (r) {
      if (texts.indexOf(r.text) < 0) texts.push(r.text);
    });
    for (var i = 0; i < texts.length; i += MAX_PER_REQUEST) {
      var id = String(nextId++);
      var chunk = texts.slice(i, i + MAX_PER_REQUEST);
      waiting.set(id, chunk);
      report("translating");
      b.translate(id, JSON.stringify(chunk));
    }
  }

  /** 페이지가 바꾼 글자만 새 원문으로 본다. 우리가 넣은 번역문의 변경 기록은 무시한다 */
  function handle(records) {
    records.forEach(function (r) {
      if (r.type === "characterData") {
        var node = r.target;
        if (shown.get(node) === node.nodeValue || originals.get(node) === node.nodeValue) return;
        originals.delete(node);
        shown.delete(node);
        if (showingTranslation) enqueue(node);
        return;
      }
      r.addedNodes.forEach(function (added) {
        var nodes = added.nodeType === 3 ? [added] : textNodes(added);
        nodes.forEach(function (n) {
          if (showingTranslation) enqueue(n);
        });
      });
    });
  }

  function flush() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
    if (observer) handle(observer.takeRecords());
    send();
  }

  function start() {
    if (started) return;
    started = true;
    if (!document.body) return;
    if (isKoreanPage()) {
      report("korean");
      return;
    }
    if (sensitive()) return;
    textNodes(document.body).forEach(enqueue);
    if (typeof MutationObserver !== "undefined") {
      observer = new MutationObserver(function (records) {
        handle(records);
        if (!timer) timer = setTimeout(flush, DEBOUNCE_MS);
      });
      observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    }
    send();
  }

  /** 앱이 서버 번역 결과를 돌려준다 */
  function apply(id, translationsJson) {
    var texts = waiting.get(String(id));
    if (!texts) return;
    waiting.delete(String(id));
    var translations = JSON.parse(translationsJson);
    texts.forEach(function (t, i) {
      if (typeof translations[i] === "string") cache.set(t, translations[i]);
    });
    if (showingTranslation) {
      queued.forEach(function (node) {
        if (showTranslated(node)) queued.delete(node);
      });
    }
    if (observer) observer.takeRecords(); // 방금 우리가 바꾼 기록은 버린다
    if (waiting.size === 0) report(failed ? "error" : "done");
  }

  /** 앱: 번역하지 못했어요. 원문은 그대로 둔다 */
  function fail(id) {
    waiting.delete(String(id));
    failed = true;
    report("error");
  }

  function showOriginal() {
    showingTranslation = false;
    originals.forEach(function (original, node) {
      setText(node, original);
    });
    shown.clear();
    if (observer) observer.takeRecords();
    report("original");
  }

  function showTranslation() {
    showingTranslation = true;
    failed = false;
    originals.forEach(function (original, node) {
      if (!showTranslated(node)) queued.add(node);
    });
    if (observer) observer.takeRecords();
    send();
    if (waiting.size === 0) report("done");
  }

  window.__dupecheck = window.__dupecheck || {};
  window.__dupecheck.translate = {
    start: start,
    apply: apply,
    fail: fail,
    showOriginal: showOriginal,
    showTranslation: showTranslation,
    flush: flush,
    isKoreanPage: isKoreanPage,
  };
})();
