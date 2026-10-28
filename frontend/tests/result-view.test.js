import assert from "node:assert/strict";
import { test } from "node:test";
import { resultView } from "../src/view/pages/result-view.js";
import { setLanguage } from "../src/i18n.js";

class Element {
  children = [];
  textContent = "";
  replaceChildren() { this.children = []; }
  append(...children) { this.children.push(...children); }
  set innerHTML(value) { throw new Error("Player and API data must remain plain text"); }
}

test("renders a localized mistake review safely and resets it for a perfect or unanswered round", () => {
  const originalDocument = globalThis.document;
  const ids = ["summary", "result-score", "result-total", "result-title", "mistake-review", "mistake-list", "review-note"];
  const ui = Object.fromEntries(ids.map(id => [id, new Element()]));
  globalThis.document = { documentElement: {}, querySelectorAll: () => [],
    getElementById: id => ui[id], createElement: () => new Element() };
  try {
    setLanguage("PT_BR");
    resultView.renderSummary({ score: 8, total: 10, mode: "CAPITALS", mistakes: [
      { number: 2, text: "Qual é a capital da Alemanha? <script>", selectedAnswer: "Paris", correctAnswer: "Berlim" }
    ] }, "Rai");
    assert.equal(ui["mistake-review"].hidden, false);
    assert.equal(ui["mistake-review"].open, false);
    assert.equal(ui["mistake-list"].children.length, 1);
    const item = ui["mistake-list"].children[0];
    assert.equal(item.children[0].textContent, "Pergunta 2: Qual é a capital da Alemanha? <script>");
    assert.equal(item.children[1].textContent, "Sua resposta: Paris");
    assert.equal(item.children[2].textContent, "Resposta correta: Berlim");
    resultView.renderSummary({ score: 10, total: 10, mistakes: [] }, "Rai");
    assert.equal(ui["mistake-review"].hidden, true);
    assert.equal(ui["mistake-list"].children.length, 0);
    resultView.renderSummary({ score: 0, total: 10, timedOut: true, mistakes: [] }, "Rai");
    assert.match(ui["review-note"].textContent, /Perguntas sem resposta não contam/);
    assert.equal(ui["result-title"].textContent, "O tempo acabou!");
  } finally { setLanguage("EN"); globalThis.document = originalDocument; }
});
