// 상품 페이지 감지·데이터 수집 (translation/product-extraction.md).
// 감지 순서: JSON-LD @type Product → OpenGraph og:type=product. 사이트별 규칙은 아직 없다.
// 상품 페이지면 앱 다리(window.DupeCheckBridge.onProduct)로 서버 /v1/products/extract 입력 모양을 보낸다.
// 로그인·결제 페이지에는 이 스크립트가 들어오지 않는다 (앱 PageInjector).
(function () {
  var PRODUCT_TYPES = ["Product", "ProductGroup", "IndividualProduct"];
  var MAX_TEXT = 20000;

  function jsonLdBlocks() {
    var out = [];
    var scripts = document.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < scripts.length; i++) {
      try {
        out.push(JSON.parse(scripts[i].textContent));
      } catch (e) {
        // 깨진 JSON-LD는 건너뛴다
      }
    }
    return out;
  }

  function hasProductType(node) {
    if (Array.isArray(node)) return node.some(hasProductType);
    if (!node || typeof node !== "object") return false;
    var types = [].concat(node["@type"] || []);
    if (types.some(function (t) { return PRODUCT_TYPES.indexOf(t) >= 0; })) return true;
    return hasProductType(node["@graph"]);
  }

  function meta(name) {
    var el = document.querySelector('meta[property="' + name + '"], meta[name="' + name + '"]');
    return el ? el.getAttribute("content") : null;
  }

  function openGraph() {
    var out = {};
    var metas = document.querySelectorAll('meta[property^="og:"], meta[property^="product:"]');
    for (var i = 0; i < metas.length; i++) {
      var key = metas[i].getAttribute("property");
      var value = metas[i].getAttribute("content");
      if (value && !(key in out)) out[key] = value;
    }
    return out;
  }

  function detect() {
    if (jsonLdBlocks().some(hasProductType)) return true;
    var type = (meta("og:type") || "").toLowerCase();
    return type === "product" || type === "og:product" || type === "product.item";
  }

  /** 본문 글자. 메뉴·머리말·꼬리말·스크립트는 뺀다 */
  function pageText() {
    var root = document.querySelector("main") || document.body;
    if (!root) return "";
    var copy = root.cloneNode(true);
    var drop = copy.querySelectorAll("script, style, noscript, nav, header, footer, svg, iframe");
    for (var i = 0; i < drop.length; i++) drop[i].remove();
    return (copy.textContent || "").replace(/\s+/g, " ").trim().slice(0, MAX_TEXT);
  }

  function collect() {
    return {
      url: location.href,
      title: document.title || null,
      lang: (document.documentElement.getAttribute("lang") || "").split("-")[0] || null,
      json_ld: jsonLdBlocks(),
      og: openGraph(),
      text: pageText(),
      image_url: meta("og:image"),
    };
  }

  var api = { detect: detect, collect: collect };
  window.__dupecheck = window.__dupecheck || {};
  window.__dupecheck.product = api;

  if (detect() && window.DupeCheckBridge && window.DupeCheckBridge.onProduct) {
    window.DupeCheckBridge.onProduct(JSON.stringify(collect()));
  }
})();
