// translation/page-translation.md 완료 조건 [주입JS]
// 실행: node --test "sandbox/mmminsung02/tests/inject/*.test.js"
const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const script = fs.readFileSync(path.join(__dirname, "../../src/inject/translate.js"), "utf8");

const PAGE = `<!doctype html><html lang="en"><head><title>Wool coat | ASOS</title>
<style>.x{color:red}</style></head>
<body>
  <h1>Wool coat</h1>
  <p>Free delivery <b>and returns</b></p>
  <p>$129.00</p>
  <p>무료 배송</p>
  <input id="q" value="search me" placeholder="Search">
  <input id="pw" type="password" value="secret123">
  <textarea id="note">Leave a note</textarea>
  <script>var hidden = "Do not translate";</script>
  <p translate="no">ASOS DESIGN</p>
</body></html>`;

/** 앱 대신 번역 요청을 받아 '[ko] 원문'으로 돌려주는 가짜 다리 */
function fakeBridge(window, { fail = false } = {}) {
  const bridge = {
    requests: [],
    states: [],
    translate(id, textsJson) {
      const texts = JSON.parse(textsJson);
      bridge.requests.push(texts);
      if (fail) window.__dupecheck.translate.fail(id);
      else window.__dupecheck.translate.apply(id, JSON.stringify(texts.map((t) => "[ko] " + t)));
    },
    onTranslateState(json) {
      bridge.states.push(JSON.parse(json));
    },
  };
  return bridge;
}

function open(html = PAGE, url = "https://www.asos.com/p/1", opts = {}) {
  const dom = new JSDOM(html, { url, runScripts: "outside-only" });
  const bridge = fakeBridge(dom.window, opts);
  dom.window.DupeCheckBridge = bridge;
  dom.window.eval(script);
  dom.window.__dupecheck.translate.start();
  return { w: dom.window, d: dom.window.document, bridge };
}

function sentTexts(bridge) {
  return bridge.requests.flat();
}

test("텍스트 노드가 번역문으로 바뀐다", () => {
  const { d } = open();

  assert.strictEqual(d.querySelector("h1").textContent, "[ko] Wool coat");
  assert.strictEqual(d.querySelector("b").textContent, "[ko] and returns");
  assert.strictEqual(d.querySelector("p").firstChild.nodeValue, "[ko] Free delivery ");
});

test("가격·숫자와 이미 한국어인 글자는 보내지 않고 그대로 둔다", () => {
  const { d, bridge } = open();

  assert.ok(!sentTexts(bridge).includes("$129.00"));
  assert.ok(!sentTexts(bridge).includes("무료 배송"));
  assert.ok(d.body.textContent.includes("$129.00"));
  assert.ok(d.body.textContent.includes("무료 배송"));
});

test("input 값·비밀번호 칸·textarea·script·translate=no는 바뀌지 않고 서버로 보내지 않는다", () => {
  const { d, bridge } = open();
  const sent = sentTexts(bridge).join("\n");

  assert.strictEqual(d.getElementById("q").value, "search me");
  assert.strictEqual(d.getElementById("pw").value, "secret123");
  assert.strictEqual(d.getElementById("note").value, "Leave a note");
  assert.ok(d.querySelector("script").textContent.includes("Do not translate"));
  assert.strictEqual(d.querySelector('[translate="no"]').textContent, "ASOS DESIGN");
  for (const secret of ["search me", "secret123", "Leave a note", "Do not translate", "ASOS DESIGN", ".x{color"]) {
    assert.ok(!sent.includes(secret), secret);
  }
});

test("원문 보기를 누르면 모든 글자가 원래대로 돌아오고, 다시 켜면 번역된다", () => {
  const html = PAGE;
  const before = new JSDOM(html).window.document.body.innerHTML;
  const { w, d } = open(html);

  w.__dupecheck.translate.showOriginal();
  assert.strictEqual(d.body.innerHTML, before);

  w.__dupecheck.translate.showTranslation();
  assert.strictEqual(d.querySelector("h1").textContent, "[ko] Wool coat");
});

test("나중에 추가된 노드도 번역된다", () => {
  const { w, d, bridge } = open();
  const before = bridge.requests.length;

  const p = d.createElement("p");
  p.textContent = "Load more reviews";
  d.body.appendChild(p);
  w.__dupecheck.translate.flush();

  assert.strictEqual(p.textContent, "[ko] Load more reviews");
  assert.strictEqual(bridge.requests.length, before + 1);
});

test("우리가 바꾼 글자는 다시 번역 요청하지 않는다", () => {
  const { w, bridge } = open();
  const before = bridge.requests.length;

  w.__dupecheck.translate.flush();

  assert.strictEqual(bridge.requests.length, before);
});

test("원문 보기 중에 새로 생긴 글자는 보내지 않는다", () => {
  const { w, d, bridge } = open();
  w.__dupecheck.translate.showOriginal();
  const before = bridge.requests.length;

  const p = d.createElement("p");
  p.textContent = "Recently viewed";
  d.body.appendChild(p);
  w.__dupecheck.translate.flush();

  assert.strictEqual(bridge.requests.length, before);
  assert.strictEqual(p.textContent, "Recently viewed");
});

for (const url of [
  "https://www.asos.com/identity/login?returnUrl=/",
  "https://zozo.jp/_cart/checkout/",
  "https://shop.example.com/payment/confirm",
]) {
  test(`결제·로그인 URL에서는 서버로 보내는 요청이 0건이다: ${url}`, () => {
    const { d, bridge } = open(PAGE, url);

    assert.strictEqual(bridge.requests.length, 0);
    assert.strictEqual(d.querySelector("h1").textContent, "Wool coat");
  });
}

test("페이지가 한 화면 안에서 결제 주소로 바뀌면(SPA) 그때부터 보내지 않는다", () => {
  const { w, d, bridge } = open();
  const before = bridge.requests.length;

  w.history.pushState({}, "", "/checkout/delivery");
  const p = d.createElement("p");
  p.textContent = "Delivery address";
  d.body.appendChild(p);
  w.__dupecheck.translate.flush();

  assert.strictEqual(bridge.requests.length, before);
});

test("한국어 페이지는 번역하지 않고 korean 상태만 알린다", () => {
  const html = '<html lang="ko"><body><h1>울 코트</h1><p>무료 배송</p></body></html>';
  const { bridge } = open(html, "https://www.musinsa.com/p/1");

  assert.strictEqual(bridge.requests.length, 0);
  assert.strictEqual(bridge.states.at(-1).status, "korean");
});

test("번역이 끝나면 페이지 언어와 done 상태를 알린다", () => {
  const { bridge } = open();

  const last = bridge.states.at(-1);
  assert.strictEqual(last.status, "done");
  assert.strictEqual(last.lang, "en");
});

test("서버 오류면 원문을 그대로 두고 error 상태를 알린다", () => {
  const { d, bridge } = open(PAGE, "https://www.asos.com/p/1", { fail: true });

  assert.strictEqual(d.querySelector("h1").textContent, "Wool coat");
  assert.strictEqual(bridge.states.at(-1).status, "error");
});

test("한 번에 보내는 문장 수는 서버 상한(200)을 넘지 않는다", () => {
  const items = Array.from({ length: 450 }, (_, i) => `<li>Item number ${i}</li>`).join("");
  const { bridge } = open(`<html lang="en"><body><ul>${items}</ul></body></html>`);

  assert.ok(bridge.requests.length >= 3);
  assert.ok(bridge.requests.every((r) => r.length <= 200));
  assert.strictEqual(sentTexts(bridge).length, 450);
});

test("같은 문장은 한 요청에 한 번만 보낸다", () => {
  const { d, bridge } = open('<html lang="en"><body><p>Add to bag</p><p>Add to bag</p></body></html>');

  assert.deepStrictEqual(bridge.requests, [["Add to bag"]]);
  assert.ok([...d.querySelectorAll("p")].every((p) => p.textContent === "[ko] Add to bag"));
});

// 실제 Claude로 ASOS 페이지를 재 보다 찾은 문제: 문서 순서대로 200개씩 보내 화면에 안 보이는
// 쿠키 안내문부터 번역했고, 첫 요청이 16.7초 걸렸다. 본문부터, 작게 나눠 보낸다
test("본문 글자를 먼저 보내고 숨겨진 글자는 나중에 보낸다", () => {
  const html = `<html lang="en"><body>
    <div id="cookie" aria-hidden="true"><p>We use cookies to improve your experience</p></div>
    <header><a>Women</a></header>
    <main><h1>Wool coat</h1><p>Regular fit</p></main>
    <footer><p>Help and FAQs</p></footer>
  </body></html>`;
  const { bridge } = open(html);
  const order = sentTexts(bridge);

  assert.ok(order.indexOf("Wool coat") < order.indexOf("Help and FAQs"), order.join(" | "));
  assert.ok(order.indexOf("Regular fit") < order.indexOf("Women"), order.join(" | "));
  assert.strictEqual(order.at(-1), "We use cookies to improve your experience");
});

test("한 요청은 40문장 이하로 작게 나눈다", () => {
  const items = Array.from({ length: 100 }, (_, i) => `<li>Item number ${i}</li>`).join("");
  const { bridge } = open(`<html lang="en"><body><main><ul>${items}</ul></main></body></html>`);

  assert.ok(bridge.requests.every((r) => r.length <= 40));
  assert.strictEqual(bridge.requests.length, 3);
});
