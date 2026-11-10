// translation/product-extraction.md 완료 조건 [주입JS]
// 실행: node --test "sandbox/mmminsung02/tests/inject/*.test.js"
// 고정 HTML: docs/site_html/ (moneyhipo가 브라우저로 저장한 실제 상품 페이지), tests/fixtures/ (목록 페이지)
const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const repo = path.join(__dirname, "../../../..");
const script = fs.readFileSync(path.join(__dirname, "../../src/inject/product.js"), "utf8");
const ASOS = path.join(repo, "docs/site_html/ASOS LUXE plunge maxi dress with ruched chiffon detail & open back in burgundy _ ASOS.html");
const ZOZO = path.join(repo, "docs/site_html/zozotown.html");
const LIST = path.join(__dirname, "../fixtures/asos-search-list.html");

function load(file, url, bridge) {
  // 저장한 페이지의 스크립트는 돌리지 않는다 (네트워크·광고). 우리 스크립트만 넣는다
  const dom = new JSDOM(fs.readFileSync(file, "utf8"), { url, runScripts: "outside-only" });
  if (bridge) dom.window.DupeCheckBridge = bridge;
  dom.window.eval(script);
  return dom.window;
}

test("JSON-LD Product가 있는 ASOS 상품 페이지는 상품 페이지로 감지된다", () => {
  const w = load(ASOS, "https://www.asos.com/asos-luxe/plunge-maxi-dress/prd/1");
  assert.strictEqual(w.__dupecheck.product.detect(), true);
});

test("ZOZOTOWN 상품 페이지도 감지된다", () => {
  const w = load(ZOZO, "https://zozo.jp/shop/beams/goods/7781/");
  assert.strictEqual(w.__dupecheck.product.detect(), true);
});

test("목록(검색 결과) 페이지는 감지되지 않는다", () => {
  const w = load(LIST, "https://www.asos.com/women/coats/cat/");
  assert.strictEqual(w.__dupecheck.product.detect(), false);
});

test("상품 페이지면 서버 입력 모양으로 앱 다리에 한 번 보낸다", () => {
  const sent = [];
  load(ZOZO, "https://zozo.jp/shop/beams/goods/7781/", { onProduct: (json) => sent.push(JSON.parse(json)) });

  assert.strictEqual(sent.length, 1);
  const page = sent[0];
  assert.strictEqual(page.url, "https://zozo.jp/shop/beams/goods/7781/");
  assert.strictEqual(page.lang, "ja");
  // ZOZOTOWN은 색상별 Product 4개를 배열 하나에 넣는다
  const blocks = page.json_ld.flat();
  assert.ok(blocks.some((b) => b["@type"] === "Product" && b.name));
  assert.ok(page.og["og:image"].startsWith("https://"));
  assert.ok(page.text.length > 100);
  assert.ok(page.text.length <= 20000);
  assert.ok(!/<script|function\s*\(/.test(page.text), "스크립트 글자가 섞이면 안 된다");
});

test("목록 페이지에서는 앱에 아무것도 보내지 않는다", () => {
  const sent = [];
  load(LIST, "https://www.asos.com/women/coats/cat/", { onProduct: (json) => sent.push(json) });
  assert.strictEqual(sent.length, 0);
});

test("JSON-LD가 없어도 og:type=product면 감지한다", () => {
  const dom = new JSDOM(
    '<html><head><meta property="og:type" content="product"><meta property="og:title" content="Coat"></head><body>Coat</body></html>',
    { url: "https://shop.example/p/1", runScripts: "outside-only" },
  );
  dom.window.eval(script);
  assert.strictEqual(dom.window.__dupecheck.product.detect(), true);
  assert.strictEqual(dom.window.__dupecheck.product.collect().og["og:title"], "Coat");
});

test("깨진 JSON-LD가 있어도 멈추지 않는다", () => {
  const dom = new JSDOM(
    '<html><head><script type="application/ld+json">{not json</script></head><body>x</body></html>',
    { url: "https://shop.example/", runScripts: "outside-only" },
  );
  dom.window.eval(script);
  assert.strictEqual(dom.window.__dupecheck.product.detect(), false);
});
