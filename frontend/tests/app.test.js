import assert from "node:assert/strict";
import { test } from "node:test";

// A minimal DOM adapter for behavior tests; visual layout still needs a browser.
class Element {
  constructor(id = "") {
    this.id = id;
    this.children = [];
    this.handlers = {};
    this.hidden = false;
    this.disabled = false;
    this.value = "";
    this.className = "";
    this.textContent = "";
    this.classList = { contains: name => this.className.split(" ").includes(name) };
  }
  addEventListener(name, handler) { this.handlers[name] = handler; }
  append(child) { this.children.push(child); }
  replaceChildren() { this.children = []; }
  focus() {}
  setAttribute() {}
  setCustomValidity(message) { this.validationMessage = message; }
  reportValidity() {}
  reset() { this.onReset?.(); }
  emit(name) { this.handlers[name]({ preventDefault() {}, target: this }); }
}

test("UI retries the same answer, recovers a failed save, and resets the player", async () => {
  const originalDocument = globalThis.document;
  const originalFetch = globalThis.fetch;
  const ids = ["welcome", "game", "result", "player-form", "player-name", "save-status",
    "ranking", "error", "retry", "progress", "score", "question", "feedback",
    "next", "alternatives", "summary", "result-title", "restart", "change-player"];
  const elements = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  const buttonIds = ["retry", "next", "restart", "change-player"];
  const submitButton = new Element();
  elements["player-form"].onReset = () => { elements["player-name"].value = ""; };
  globalThis.document = {
    body: new Element(),
    getElementById: id => elements[id],
    createElement: () => new Element(),
    querySelectorAll: () => [submitButton, ...buttonIds.map(id => elements[id]),
      ...elements.alternatives.children]
  };
  const requests = [];
  let answers = 0;
  let saves = 0;
  let starts = 0;
  const initialState = {
    gameId: "round", number: 1, score: 0, total: 10, finished: false,
    question: { text: "Capital?", alternatives: ["A", "B", "C", "D"] }
  };
  globalThis.fetch = async (url, options) => {
    requests.push({ url, data: options.body && JSON.parse(options.body) });
    if (url === "/api/games") {
      starts++;
      return { ok: true, json: async () => structuredClone(initialState) };
    }
    if (url === "/api/answers") {
      answers++;
      if (answers === 1) throw new Error("Resposta de rede perdida");
      return { ok: true, json: async () => ({ correct: false, correctAnswer: "B",
        state: { ...initialState, finished: true, question: null } }) };
    }
    if (url === "/api/results") {
      saves++;
      return saves === 1
        ? { ok: false, json: async () => ({ message: "Banco indisponível" }) }
        : { ok: true, json: async () => ({ saved: true }) };
    }
    return { ok: true, json: async () => [
      { playerName: "<script>text</script>", score: 0, totalQuestions: 10 }
    ] };
  };
  const settle = () => new Promise(resolve => setImmediate(resolve));
  try {
    await import("../src/app.js");
    elements["player-name"].value = "   ";
    elements["player-form"].emit("submit");
    assert.equal(starts, 0);
    assert.ok(elements["player-name"].validationMessage);

    elements["player-name"].value = "Rai";
    elements["player-name"].emit("input");
    elements["player-form"].emit("submit");
    elements["player-form"].emit("submit");
    await settle();
    assert.equal(starts, 1);
    assert.equal(elements.game.hidden, false);
    assert.equal(elements.alternatives.children.length, 4);

    elements.alternatives.children[0].emit("click");
    elements.alternatives.children[0].emit("click");
    await settle();
    assert.equal(answers, 1);
    assert.equal(elements.retry.hidden, false);
    assert.ok(elements.alternatives.children.every(button => button.disabled));
    elements.retry.emit("click");
    await settle();
    const submissions = requests.filter(request => request.url === "/api/answers");
    assert.deepEqual(submissions[0].data, submissions[1].data);
    assert.equal(elements.next.textContent, "Ver resultado");

    elements.next.emit("click");
    await settle();
    assert.equal(elements.result.hidden, false);
    assert.match(elements["save-status"].textContent, /ainda não salvo/);
    assert.equal(elements.retry.hidden, false);
    elements.retry.emit("click");
    await settle();
    assert.equal(saves, 2);
    assert.match(elements["save-status"].textContent, /Resultado registrado/);
    assert.equal(elements.ranking.children[0].textContent, "<script>text</script> — 0/10");

    elements.restart.emit("click");
    await settle();
    assert.equal(starts, 2);
    assert.equal(elements.score.textContent, "Acertos: 0");
    assert.ok(elements.alternatives.children.every(button => !button.disabled));

    // The change-player action is available on the result screen.
    elements["change-player"].emit("click");
    assert.equal(elements.welcome.hidden, false);
    assert.equal(elements["player-name"].value, "");
  } finally {
    globalThis.document = originalDocument;
    globalThis.fetch = originalFetch;
  }
});
