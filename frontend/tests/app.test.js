import assert from "node:assert/strict";
import { test } from "node:test";

class Element {
  constructor(id = "", tagName = "DIV") {
    Object.assign(this, { id, tagName, children: [], handlers: {}, dataset: {}, attributes: {},
      hidden: false, disabled: false, value: "", className: "", returnValue: "", open: false });
    this.classList = { contains: name => this.className.split(" ").includes(name) };
  }
  get textContent() { return this._text || this.children.map(child => child.textContent).join(""); }
  set textContent(value) { this._text = String(value); this.children = []; }
  set innerHTML(value) { throw new Error("Do not render player/API data as HTML"); }
  get lastElementChild() { return this.children.at(-1); }
  addEventListener(name, handler) { this.handlers[name] = handler; }
  append(...children) { this.children.push(...children); }
  replaceChildren() { this.children = []; this._text = ""; }
  focus() { document.activeElement = this; }
  setAttribute(name, value) { this.attributes[name] = value; }
  setCustomValidity(message) { this.validationMessage = message; }
  reportValidity() {}
  reset() { this.onReset?.(); }
  emit(name, extra = {}) { this.handlers[name]?.({ preventDefault() {}, target: this, ...extra }); }
  click() { if (!this.disabled) this.emit("click"); }
  showModal() { this.open = true; }
  close(value) { this.returnValue = value; this.open = false; this.emit("close"); }
}

test("preserves retries, English feedback, keyboard navigation, unsaved results, and player reset", async () => {
  const original = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch };
  const ids = ["app", "home", "welcome", "game", "result", "player-form", "player-name", "save-status",
    "ranking", "ranking-status", "error", "error-panel", "retry", "progress", "round-progress", "score",
    "player-label", "question", "feedback", "next", "alternatives", "summary", "result-title", "result-score",
    "result-total", "restart", "change-player", "loading", "loading-text", "discard-dialog"];
  const ui = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  ui["player-name"].tagName = "INPUT";
  for (const id of ["game", "result", "error-panel", "loading", "next"]) ui[id].hidden = true;
  const staticButtons = ["home", "retry", "next", "restart", "change-player"].map(id => ui[id]);
  const documentHandlers = {};
  const windowHandlers = {};
  globalThis.document = {
    getElementById: id => ui[id], createElement: name => new Element("", name.toUpperCase()),
    querySelectorAll: () => [...staticButtons, ...ui.alternatives.children],
    addEventListener: (name, fn) => { documentHandlers[name] = fn; }
  };
  globalThis.window = { addEventListener: (name, fn) => { windowHandlers[name] = fn; } };
  ui["player-form"].onReset = () => { ui["player-name"].value = ""; };
  const requests = [];
  let starts = 0, answers = 0, saves = 0, rankings = 0;
  const initial = { gameId: "round", number: 1, total: 2, score: 0, finished: false,
    question: { text: "Capital?", alternatives: ["A", "B", "C", "D"] } };
  globalThis.fetch = async (url, options) => {
    const body = options.body && JSON.parse(options.body);
    requests.push({ url, body });
    let payload;
    if (url === "/api/games") { starts++; payload = structuredClone(initial); }
    else if (url === "/api/answers") {
      answers++;
      if (answers === 1) throw new Error("Network response lost");
      const correct = body.choice === "B";
      const score = starts === 1 ? 0 : 1;
      payload = { correct, correctAnswer: "B", state: { ...initial, score,
        number: correct ? 2 : body.number, finished: !correct,
        question: correct ? initial.question : null } };
    } else if (url === "/api/results") {
      saves++;
      if (saves === 1) return { ok: false, json: async () => ({ message: "Leaderboard unavailable" }) };
      payload = { saved: true };
    } else {
      rankings++;
      if (rankings === 1) throw new Error("Ranking network failure");
      payload = [{ playerName: "<script>text</script>", score: 0, totalQuestions: 2 }];
    }
    return { ok: true, json: async () => payload };
  };
  const settle = () => new Promise(resolve => setImmediate(resolve));
  function press(key, target = ui.question, extra = {}) {
    let prevented = false;
    documentHandlers.keydown({ key, target, preventDefault() { prevented = true; }, ...extra });
    return prevented;
  }
  try {
    await import("../src/app.js");
    ui["player-name"].value = "   ";
    ui["player-form"].emit("submit");
    assert.equal(starts, 0);
    assert.match(ui["player-name"].validationMessage, /^Enter a name/);
    ui["player-name"].value = "Rai";
    ui["player-name"].emit("input");
    ui["player-form"].emit("submit");
    ui["player-form"].emit("submit");
    await settle();
    assert.equal(starts, 1);
    assert.equal(ui.game.hidden, false);
    assert.equal(document.activeElement, ui.question);
    assert.equal(ui["player-label"].textContent, "Your turn, Rai.");
    assert.equal(press(" "), false, "Space must wait for a confirmed answer");
    assert.equal(ui.next.hidden, true);
    ui.alternatives.children[0].click();
    ui.alternatives.children[0].click();
    await settle();
    assert.equal(answers, 1);
    assert.equal(press(" "), false, "Space must not bypass a failed answer retry");
    assert.ok(ui.alternatives.children.every(button => button.disabled));
    assert.equal(document.activeElement, ui.retry);
    ui.retry.click();
    await settle();
    const submissions = requests.filter(r => r.url === "/api/answers");
    assert.deepEqual(submissions[0].body, submissions[1].body);
    assert.equal(document.activeElement, ui.next);
    assert.equal(ui.alternatives.children[1].dataset.outcome, "correct");
    assert.equal(ui.alternatives.children[0].dataset.outcome, "wrong");
    assert.equal(press(" ", ui.next), true, "Space opens the result after a wrong answer");
    await settle();
    assert.equal(ui.result.hidden, false);
    assert.match(ui["save-status"].textContent, /has not been saved yet/);
    ui.restart.click();
    assert.equal(ui["discard-dialog"].open, true);
    ui["discard-dialog"].close("cancel");
    assert.equal(starts, 1);
    assert.equal(ui["error-panel"].hidden, false);
    ui.retry.click();
    await settle();
    assert.equal(saves, 2);
    assert.match(ui["save-status"].textContent, /Result saved/);
    assert.match(ui["ranking-status"].textContent, /result is saved/);
    ui.retry.click();
    await settle();
    assert.equal(saves, 2, "ranking retry must not resubmit a known saved result");
    assert.match(ui.ranking.children[0].textContent, /<script>text<\/script>/);
    ui.restart.click();
    await settle();
    assert.equal(starts, 2);
    const blockedAnswers = answers;
    assert.equal(press("b", ui.question, { repeat: true }), false);
    assert.equal(press("b", ui["player-name"]), false);
    assert.equal(press("b", ui.question, { ctrlKey: true }), false);
    assert.equal(answers, blockedAnswers);
    assert.equal(press("b"), true);
    assert.equal(press(" "), false, "Space cannot skip while an answer is in flight");
    await settle();
    assert.equal(document.activeElement, ui.next);
    assert.equal(ui.score.textContent, "Correct answers: 1");
    assert.equal(press(" ", ui["player-name"]), false);
    assert.equal(press(" ", ui.question, { repeat: true }), false);
    assert.equal(press(" ", ui.question, { ctrlKey: true }), false);
    assert.equal(press(" ", ui.question, { altKey: true }), false);
    assert.equal(press(" ", ui.question, { metaKey: true }), false);
    assert.equal(press(" ", ui.question, { shiftKey: true }), false);
    ui.home.tagName = "BUTTON";
    assert.equal(press(" ", ui.home), false, "Other buttons keep native Space behavior");
    assert.equal(ui.progress.textContent, "QUESTION 1 OF 2");
    assert.equal(press(" ", ui.next), true);
    assert.equal(ui.progress.textContent, "QUESTION 2 OF 2");
    assert.equal(press(" "), false, "A second Space cannot skip an unanswered question");
    assert.equal(press(" ", ui.question, { repeat: true }), false);
    assert.equal(answers, blockedAnswers + 1);
    ui.alternatives.children[0].click();
    await settle();
    let prevented = false;
    windowHandlers.beforeunload({ preventDefault() { prevented = true; } });
    assert.equal(prevented, true);
    ui.home.click();
    assert.equal(ui["discard-dialog"].open, true);
    assert.equal(press(" "), false, "Space must not advance behind a modal dialog");
    assert.equal(press("b"), false, "Answer shortcuts must not act behind a modal dialog");
    ui["discard-dialog"].close("discard");
    assert.equal(ui.welcome.hidden, false);
    assert.equal(ui["player-name"].value, "");
    assert.equal(saves, 2);
  } finally {
    Object.assign(globalThis, original);
  }
});
