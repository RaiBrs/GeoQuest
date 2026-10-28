import assert from "node:assert/strict";
import { test } from "node:test";

class Element {
  constructor(id = "", tagName = "DIV") {
    Object.assign(this, { id, tagName, children: [], handlers: {}, dataset: {}, attributes: {},
      hidden: false, disabled: false, value: "", className: "" });
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
}


test("uses names only for safe greetings and preserves keyboard flow and answer retries", async () => {
  const original = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch,
    setInterval: globalThis.setInterval, clearInterval: globalThis.clearInterval };
  const originalNow = Date.now;
  let now = 100000, tick;
  Date.now = () => now;
  globalThis.setInterval = callback => { tick = callback; return 1; };
  globalThis.clearInterval = () => { tick = undefined; };
  const ids = ["hub", "hub-title", "mode-capitals", "mode-survival", "mode-timed", "change-player", "mode-label", "timer",
    "app", "home", "welcome", "game", "result", "player-form", "player-name",
    "error", "error-panel", "retry", "progress", "round-progress", "score", "player-label", "question",
    "feedback", "next", "restart-round", "keyboard-hint", "alternatives", "summary", "result-title", "result-score", "result-total",
    "restart", "back-to-home", "loading", "loading-text"];
  const ui = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  ui["player-name"].tagName = "INPUT";
  ui.home.tagName = "BUTTON";
  ui.next.tagName = "BUTTON";
  for (const id of ["game", "result", "error-panel", "loading", "next", "restart-round"]) ui[id].hidden = true;
  const staticButtons = ["home", "retry", "next", "restart-round", "restart", "back-to-home"].map(id => ui[id]);
  const documentHandlers = {}, windowHandlers = {};
  globalThis.document = {
    getElementById: id => ui[id], createElement: name => new Element("", name.toUpperCase()),
    querySelectorAll: () => [...staticButtons, ...ui.alternatives.children],
    addEventListener: (name, fn) => { documentHandlers[name] = fn; }
  };
  globalThis.window = { addEventListener: (name, fn) => { windowHandlers[name] = fn; } };
  ui["player-form"].onReset = () => { ui["player-name"].value = ""; };
  const requests = [];
  let starts = 0, answers = 0;
  let failNextStart = false;
  let expectedMode = "SURVIVAL", activeMode, expiresAt;
  const initial = { gameId: "round", number: 1, total: 2, score: 0, finished: false,
    question: { text: "Capital?", alternatives: ["A", "B", "C", "D"] } };
  globalThis.fetch = async (url, options) => {
    const body = JSON.parse(options.body);
    requests.push({ url, body });
    let payload;
    if (url === "/api/games") {
      assert.deepEqual(body, { mode: expectedMode }, "Only the selected mode is sent to Java");
      if (failNextStart) {
        failNextStart = false;
        return { ok: false, json: async () => ({ message: "Could not start a new round." }) };
      }
      starts++;
      activeMode = body.mode;
      expiresAt = activeMode === "TIMED" ? now + 60000 : null;
      payload = { ...initial, gameId: "round-" + starts, mode: activeMode, expiresAt };
    } else if (url === "/api/answers") {
      answers++;
      if (answers === 1) throw new Error("Network response lost");
      const correct = body.choice === "B";
      const finished = (!correct && activeMode === "SURVIVAL") || body.number === 2;
      payload = { correct, correctAnswer: "B", state: { ...initial, gameId: body.gameId,
        score: correct ? body.number : body.number - 1,
        number: finished ? body.number : body.number + 1, finished,
        question: finished ? null : initial.question, mode: activeMode, expiresAt } };
    } else if (url === "/api/games/status") {
      payload = { ...initial, gameId: body.gameId, mode: "TIMED", expiresAt,
        finished: true, timedOut: true, question: null };
    } else {
      throw new Error("Unexpected API route: " + url);
    }
    return { ok: true, json: async () => payload };
  };
  const settle = () => new Promise(resolve => setImmediate(resolve));
  function press(key, target = ui.question, extra = {}) {
    let prevented = false;
    documentHandlers.keydown({ key, target, preventDefault() { prevented = true; }, ...extra });
    return prevented;
  }
  const name = "Rai <script>text</script>";
  try {
    await import("../src/app.js");
    for (const invalid of ["   ", "😀".repeat(81), "Rai\n"]) {
      ui["player-name"].value = invalid;
      ui["player-name"].emit("input");
      ui["player-form"].emit("submit");
      assert.equal(starts, 0);
      assert.match(ui["player-name"].validationMessage, /^Enter a name/);
    }
    ui["player-name"].value = name;
    ui["player-name"].emit("input");
    ui["player-form"].emit("submit");
    ui["player-form"].emit("submit");
    assert.equal(starts, 0, "Entering the hub must not start a round");
    assert.equal(ui.hub.hidden, false);
    assert.equal(ui["hub-title"].textContent, `Hello, ${name}!`);
    ui["mode-survival"].click();
    await settle();
    assert.equal(starts, 1);
    assert.equal(ui.game.hidden, false);
    assert.equal(ui["restart-round"].hidden, true);
    assert.equal(press("r"), false, "Cannot restart an unanswered round with R");
    assert.equal(document.activeElement, ui.question);
    assert.equal(ui["player-label"].textContent, `Your turn, ${name}.`);
    assert.equal(press(" "), false);
    assert.equal(press("b", ui["player-name"]), false);
    assert.equal(press("b", ui.question, { repeat: true }), false);
    assert.equal(press("b", ui.question, { ctrlKey: true }), false);
    assert.equal(press("b"), true);
    assert.equal(press(" "), false, "Wait for the answer response");
    await settle();
    assert.equal(answers, 1);
    assert.ok(ui.alternatives.children.every(button => button.disabled));
    assert.equal(document.activeElement, ui.retry);
    ui.retry.click();
    await settle();
    const submissions = requests.filter(request => request.url === "/api/answers");
    assert.deepEqual(submissions[0].body, submissions[1].body);
    assert.equal(document.activeElement, ui.next);
    assert.equal(ui.feedback.textContent, `Well done, ${name}! Correct answer.`);
    assert.equal(ui.score.textContent, "Correct answers: 1");
    assert.equal(ui["restart-round"].hidden, true, "Continue normally after a correct non-final answer");
    assert.equal(press(" ", ui.home), false);
    assert.equal(press(" ", ui.question, { repeat: true }), false);
    for (const modifier of ["ctrlKey", "altKey", "metaKey", "shiftKey"]) {
      assert.equal(press(" ", ui.question, { [modifier]: true }), false);
    }
    assert.equal(press(" ", ui.next), true);
    assert.equal(ui.progress.textContent, "QUESTION 2 OF 2");
    assert.equal(press(" "), false, "A second Space must not skip a question");
    press("a");
    await settle();
    assert.equal(ui.feedback.textContent, `Not this time, ${name}. The correct answer is B.`);
    assert.equal(ui["restart-round"].hidden, false);
    assert.match(ui["keyboard-hint"].textContent, /Press R to play again/);
    assert.equal(press("r", ui.question, { repeat: true }), false);
    assert.equal(press("r", ui.question, { ctrlKey: true }), false);
    assert.equal(press("r", ui["player-name"]), false);
    failNextStart = true;
    assert.equal(press("r"), true, "Restart directly after a wrong answer without opening the result");
    assert.equal(press("r"), false, "Do not create a second round while restarting");
    await settle();
    assert.equal(starts, 1, "A failed restart must preserve the completed round");
    assert.equal(ui.game.hidden, false);
    assert.equal(ui.score.textContent, "Correct answers: 1");
    assert.equal(ui["error-panel"].hidden, false);
    ui.next.click();
    assert.equal(ui.result.hidden, false, "The old result remains available after a failed restart");
    assert.equal(ui["error-panel"].hidden, true);
    assert.equal(ui.summary.textContent, `${name}, you answered 1 out of 2 questions correctly.`);
    ui.restart.click();
    await settle();
    assert.equal(starts, 2);
    assert.equal(ui.result.hidden, true);
    assert.equal(ui.progress.textContent, "QUESTION 1 OF 2");
    assert.equal(ui.score.textContent, "Correct answers: 0");
    assert.equal(ui["player-label"].textContent, `Your turn, ${name}.`);
    assert.equal(ui["restart-round"].hidden, true);
    assert.match(ui["keyboard-hint"].textContent, /Press A–D to answer/);
    press("a");
    await settle();
    assert.equal(ui["restart-round"].hidden, false, "Offer restart even after the first wrong answer");
    ui["restart-round"].click();
    ui["restart-round"].click();
    await settle();
    assert.equal(starts, 3, "Double clicking Play again must start only one round");
    assert.equal(ui.result.hidden, true);
    assert.equal(document.activeElement, ui.question);
    press("b");
    await settle();
    press(" ", ui.next);
    press("b");
    await settle();
    assert.equal(ui["restart-round"].hidden, false, "A completed perfect round offers the same actions");
    const callsBeforeResult = requests.length;
    assert.equal(press(" ", ui.next), true);
    assert.equal(ui.result.hidden, false);
    assert.equal(document.activeElement, ui["result-title"]);
    assert.equal(requests.length, callsBeforeResult, "Showing the result must not save or fetch a ranking");
    assert.equal(ui.summary.textContent, `${name}, you answered 2 out of 2 questions correctly.`);
    assert.equal(windowHandlers.beforeunload, undefined, "There is no unsaved-result warning");
    ui.restart.click();
    await settle();
    assert.equal(starts, 4);
    assert.equal(ui["player-label"].textContent, `Your turn, ${name}.`);
    press("a");
    await settle();
    press(" ", ui.next);
    ui["back-to-home"].click();
    assert.equal(ui.hub.hidden, false, "Returning home preserves the player");
    assert.equal(ui["hub-title"].textContent, `Hello, ${name}!`);
    expectedMode = "CAPITALS";
    ui["mode-capitals"].click();
    await settle();
    press("a");
    await settle();
    assert.equal(ui["restart-round"].hidden, true, "Capitals continues after a mistake");
    press(" ", ui.next);
    assert.equal(ui.progress.textContent, "QUESTION 2 OF 2");
    assert.equal(ui["round-progress"].value, 1, "Progress counts answers, not correct answers");
    ui.home.click();
    assert.equal(ui.hub.hidden, false);
    expectedMode = "TIMED";
    ui["mode-timed"].click();
    await settle();
    assert.equal(ui.timer.hidden, false);
    assert.match(ui.timer.textContent, /60s/);
    now += 60000;
    tick();
    await settle();
    assert.equal(ui.result.hidden, false, "The timer ends an idle round");
    assert.equal(ui["result-title"].textContent, "Time is up!");
    assert.equal(tick, undefined);
    ui["back-to-home"].click();
    ui["mode-timed"].click();
    await settle();
    ui.home.click();
    assert.equal(tick, undefined, "Leaving a timed round cancels its timer");
    ui["change-player"].click();
    assert.equal(ui.welcome.hidden, false);
    assert.equal(ui["player-name"].value, "");
    assert.equal(document.activeElement, ui["player-name"]);
    assert.ok(requests.every(request => !JSON.stringify(request.body).includes(name)));
  } finally { Object.assign(globalThis, original); Date.now = originalNow; }
});
