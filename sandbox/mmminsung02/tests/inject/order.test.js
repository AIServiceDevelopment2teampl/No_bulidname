// auto-register/in-app.md 완료 조건 [주입JS]
// 실행: node --test "sandbox/mmminsung02/tests/inject/*.test.js"
// 고정 HTML: tests/fixtures/order-complete-*.html (주문 완료), cart-zozo.html (장바구니)
const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const script = fs.readFileSync(path.join(__dirname, "../../src/inject/order.js"), "utf8");
const fixture = (name) => path.join(__dirname, "../fixtures", name);
const ZOZO_DONE = fixture("order-complete-zozo.html");
const KO_DONE = fixture("order-complete-ko.html");
const CART = fixture("cart-zozo.html");

// jsdom 안에서 만든 배열은 Node 배열과 프로토타입이 달라 deepStrictEqual이 실패한다. JSON으로 옮겨 비교한다
const plain = (value) => JSON.parse(JSON.stringify(value));

function load(file, url, bridge) {
  const dom = new JSDOM(fs.readFileSync(file, "utf8"), { url, runScripts: "outside-only" });
  if (bridge) dom.window.DupeCheckBridge = bridge;
  dom.window.eval(script);
  return dom.window;
}

const ZOZO_URL = "https://zozo.jp/_cart/order/complete.html?token=abc";

test("주문 완료 페이지에서 상품 2개가 정확히 추출된다 (일본어)", () => {
  const w = load(ZOZO_DONE, ZOZO_URL);
  assert.strictEqual(w.__dupecheck.order.detect(), true);
  const order = plain(w.__dupecheck.order.collect());
  assert.strictEqual(order.site, "zozo.jp");
  assert.strictEqual(order.order_number, "2611-0412-88");
  assert.deepStrictEqual(
    order.items.map((i) => i.name),
    ["ウールブレンド クルーネック カーディガン", "キャンバス トートバッグ"]
  );
  const [cardigan, tote] = order.items;
  assert.strictEqual(cardigan.color, "チャコール");
  assert.strictEqual(cardigan.size, "M");
  assert.strictEqual(cardigan.price, "¥8,800");
  assert.strictEqual(cardigan.image_url, "https://c.imgz.jp/123/cardigan.jpg");
  assert.strictEqual(cardigan.product_url, "https://zozo.jp/shop/beams/goods/123/");
  assert.strictEqual(tote.image_url, "https://c.imgz.jp/456/tote.jpg");
});

test("주문 완료 페이지에서 상품 2개가 정확히 추출된다 (한국어 표)", () => {
  const order = plain(load(KO_DONE, "https://shop.example.kr/order/complete").__dupecheck.order.collect());
  assert.strictEqual(order.order_number, "20261119-0001234");
  assert.deepStrictEqual(
    order.items.map((i) => [i.name, i.color, i.size, i.price]),
    [
      ["오버핏 코튼 셔츠", "화이트", "L", "39,900원"],
      ["와이드 데님 팬츠", "인디고", "30", "59,000원"],
    ]
  );
});

test("이름·주소·전화·카드 번호 영역 글자가 추출 결과에 없다", () => {
  for (const [file, url, secrets] of [
    [ZOZO_DONE, ZOZO_URL, ["山田", "太郎", "渋谷", "神宮前", "090-1234-5678", "4111", "VISA", "12,100", "token"]],
    [KO_DONE, "https://shop.example.kr/order/complete", ["홍길동", "테헤란로", "강남구", "010-1234-5678", "1234-56", "신한", "98,900"]],
  ]) {
    const json = JSON.stringify(load(file, url).__dupecheck.order.collect());
    for (const secret of secrets) assert.ok(!json.includes(secret), `${secret} 이(가) 결과에 있음`);
  }
});

test("장바구니 페이지에서는 감지되지 않는다", () => {
  const sent = [];
  const w = load(CART, "https://zozo.jp/_cart/", { onOrder: (json) => sent.push(json) });
  assert.strictEqual(w.__dupecheck.order.detect(), false);
  assert.strictEqual(sent.length, 0);
});

test("상품 페이지에서도 감지되지 않는다", () => {
  const repo = path.join(__dirname, "../../../..");
  const w = load(path.join(repo, "docs/site_html/zozotown.html"), "https://zozo.jp/shop/beams/goods/7781/");
  assert.strictEqual(w.__dupecheck.order.detect(), false);
});

test("주문 완료면 서버 입력 모양으로 앱 다리에 한 번 보낸다", () => {
  const sent = [];
  load(ZOZO_DONE, ZOZO_URL, { onOrder: (json) => sent.push(JSON.parse(json)) });
  assert.strictEqual(sent.length, 1);
  assert.deepStrictEqual(Object.keys(sent[0]).sort(), ["items", "order_number", "site", "url"]);
  assert.strictEqual(sent[0].url, "https://zozo.jp/_cart/order/complete.html");
});
