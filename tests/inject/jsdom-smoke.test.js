const { test } = require("node:test");
const assert = require("node:assert");
const { JSDOM } = require("jsdom");

test("jsdom이 HTML을 읽고 글자를 바꿀 수 있다", () => {
  const dom = new JSDOM("<p id='t'>Regular fit</p>");
  const el = dom.window.document.getElementById("t");

  el.textContent = "레귤러 핏";

  assert.strictEqual(el.textContent, "레귤러 핏");
});
