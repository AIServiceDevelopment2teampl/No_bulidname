// 실행: node --test sandbox/mmminsung02/tests/inject/ (jsdom은 저장소 루트 node_modules)
const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const script = fs.readFileSync(path.join(__dirname, "../../src/inject/bootstrap.js"), "utf8");

function page() {
  return new JSDOM("<html><body><p>Wool coat</p></body></html>", { runScripts: "outside-only" });
}

test("처음 넣으면 표식을 남기고 페이지 글자는 건드리지 않는다", () => {
  const dom = page();

  dom.window.eval(script);

  assert.strictEqual(dom.window.__dupecheck.version, 1);
  assert.strictEqual(dom.window.__dupecheck.repeated, 0);
  assert.strictEqual(dom.window.document.body.textContent, "Wool coat");
});

test("같은 문서에 두 번 들어와도 다시 시작하지 않는다", () => {
  const dom = page();
  dom.window.eval(script);
  const first = dom.window.__dupecheck.startedAt;

  dom.window.eval(script);

  assert.strictEqual(dom.window.__dupecheck.repeated, 1);
  assert.strictEqual(dom.window.__dupecheck.startedAt, first);
});
