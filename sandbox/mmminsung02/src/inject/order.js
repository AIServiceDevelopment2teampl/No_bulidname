// 주문 완료 페이지 감지·주문 상품 수집 (auto-register/in-app.md, 기능 14).
// 감지: 페이지 글자에 '주문 완료' 문구가 있어야 한다 (장바구니·결제 전 화면에는 없다).
// 수집: 상품 영역만 읽는다. 사진(또는 상품 링크)과 가격이 함께 있는 가장 작은 칸 = 상품 한 줄.
//   이름·주소·연락처·결제 수단 글자가 들어 있는 칸은 통째로 건너뛰고, 입력칸(form)은 읽지 않는다.
// 감지되면 앱 다리(window.DupeCheckBridge.onOrder)로 서버 /v1/imports/in-app 입력 모양을 한 번 보낸다.
(function () {
  var DONE = new RegExp(
    [
      "주문(이|가)?\\s*(정상적으로\\s*)?(완료|접수)",
      "주문해\\s*주셔서\\s*감사",
      "구매(가|를)?\\s*완료",
      "thank\\s*you\\s*for\\s*(your\\s*)?(order|purchase)",
      "your\\s*order\\s*(has\\s*been|was)\\s*(placed|received|confirmed)",
      "order\\s*(is\\s*)?(complete|confirmed|placed)",
      "ご注文(ありがとう|が完了|を承りました|を受け付けました)",
      "注文(が)?完了",
    ].join("|"),
    "i"
  );
  var ORDER_NO = /(주문\s*번호|ご?注文番号|order\s*(number|no\.?|#|id))\s*[:：#]?\s*([A-Za-z0-9][A-Za-z0-9-]{3,})/i;
  var PRICE = /([₩¥$€£]\s?\d[\d,.]*|\d[\d,]*\s?(원|円))/;
  // 이 글자가 있는 칸은 개인 정보·결제 정보 칸이라 읽지 않는다
  var PRIVATE = new RegExp(
    [
      "배송지|받는\\s*분|받는\\s*사람|수령인|주문자|연락처|휴대폰|전화|주소|결제\\s*수단|카드|계좌",
      "お届け先|配送先|ご注文者|お名前|住所|電話|お支払|クレジット|カード",
      "ship(ping)?\\s*to|shipping\\s*address|billing|payment|\\bcard\\b|credit|address|phone|recipient",
      "\\d{2,3}-\\d{3,4}-\\d{4}",
      "\\d{4}[-\\s][\\d*]{4}[-\\s][\\d*]{4}",
    ].join("|"),
    "i"
  );
  var COLOR = /^(색상|컬러|カラー|colou?r)\s*[:：]\s*(.+)$/i;
  var SIZE = /^(사이즈|サイズ|size)\s*[:：]\s*(.+)$/i;
  var MAX_ITEMS = 30;

  function text(el) {
    return (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim();
  }

  function detect() {
    var body = document.body;
    if (!body) return false;
    return DONE.test(text(body));
  }

  function orderNumber() {
    var m = ORDER_NO.exec(text(document.body));
    return m ? m[3] : null;
  }

  function looksLikeItem(el, needs) {
    if (el.closest("form, input, textarea, select")) return false;
    var t = text(el);
    if (!PRICE.test(t) || t.length > 600) return false;
    return !!el.querySelector(needs);
  }

  function smallestItems(needs) {
    var all = document.body.querySelectorAll("li, tr, article, div, section");
    var found = [];
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (!looksLikeItem(el, needs)) continue;
      // 가장 작은 칸만: 안에 또 상품 칸이 있으면 바깥 칸은 목록이다
      var inner = el.querySelectorAll("li, tr, article, div, section");
      var hasInner = false;
      for (var j = 0; j < inner.length; j++) {
        if (looksLikeItem(inner[j], needs)) {
          hasInner = true;
          break;
        }
      }
      if (!hasInner) found.push(el);
    }
    return found.filter(function (el) {
      return !PRIVATE.test(text(el));
    });
  }

  // 사진이 있는 상품 줄을 먼저 찾고, 사진 없는 쇼핑몰이면 상품 링크로 찾는다
  function itemElements() {
    var withPhoto = smallestItems("img");
    return withPhoto.length ? withPhoto : smallestItems("a[href]");
  }

  function absolute(url) {
    try {
      return new URL(url, location.href).href;
    } catch (e) {
      return null;
    }
  }

  function nameOf(el) {
    var candidates = el.querySelectorAll("a[href], h1, h2, h3, h4, strong, b, p, span, td, div");
    var best = null;
    for (var i = 0; i < candidates.length; i++) {
      var t = text(candidates[i]);
      if (!t || PRICE.test(t) || /[:：]/.test(t)) continue;
      if (candidates[i].children.length > 2) continue;
      if (!best || t.length > best.length) best = t;
    }
    if (!best) {
      var img = el.querySelector("img[alt]");
      best = img ? img.getAttribute("alt").trim() : null;
    }
    return best ? best.slice(0, 300) : null;
  }

  /** '색상: 화이트 / 사이즈: L' 같은 옵션 글자. 글자 조각을 / | , 로 나눠 칸 이름을 찾는다 */
  function option(el, pattern) {
    var walker = document.createTreeWalker(el, 4 /* NodeFilter.SHOW_TEXT */);
    while (walker.nextNode()) {
      var segments = walker.currentNode.nodeValue.split(/[/|,]/);
      for (var i = 0; i < segments.length; i++) {
        var m = pattern.exec(segments[i].replace(/\s+/g, " ").trim());
        if (m) return m[2].trim();
      }
    }
    return null;
  }

  function collectItem(el) {
    var t = text(el);
    var img = el.querySelector("img");
    var link = el.querySelector("a[href]");
    var src = img ? img.getAttribute("src") || img.getAttribute("data-src") : null;
    return {
      name: nameOf(el),
      color: option(el, COLOR),
      size: option(el, SIZE),
      price: (PRICE.exec(t) || [null])[0],
      image_url: src ? absolute(src) : null,
      product_url: link ? absolute(link.getAttribute("href")) : null,
    };
  }

  function collect() {
    var items = itemElements()
      .map(collectItem)
      .filter(function (item) {
        return item.name;
      })
      .slice(0, MAX_ITEMS);
    return {
      site: location.hostname.replace(/^www\./, ""),
      url: location.origin + location.pathname,
      order_number: orderNumber(),
      items: items,
    };
  }

  var api = { detect: detect, collect: collect };
  window.__dupecheck = window.__dupecheck || {};
  window.__dupecheck.order = api;

  if (detect() && window.DupeCheckBridge && window.DupeCheckBridge.onOrder) {
    var order = collect();
    if (order.items.length) window.DupeCheckBridge.onOrder(JSON.stringify(order));
  }
})();
