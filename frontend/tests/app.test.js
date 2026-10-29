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
  removeAttribute(name) { delete this.attributes[name]; }
  setCustomValidity(message) { this.validationMessage = message; }
  reportValidity() {}
  reset() { this.onReset?.(); }
  contains(node) { return this.children.includes(node); }
  querySelectorAll() { return this.children; }
  emit(name, extra = {}) { this.handlers[name]?.({ preventDefault() {}, target: this, ...extra }); }
  click() { if (!this.disabled) this.emit("click"); }
}


test("supports anonymous entry, safe provider names, keyboard flow and answer retries", async () => {
  const original = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch,
    setInterval: globalThis.setInterval, clearInterval: globalThis.clearInterval,
    setTimeout: globalThis.setTimeout, clearTimeout: globalThis.clearTimeout };
  const originalNow = Date.now;
  let now = 100000, tick;
  Date.now = () => now;
  globalThis.setInterval = callback => { tick = callback; return 1; };
  globalThis.clearInterval = () => { tick = undefined; };
  let nextTimeout = 0;
  const timeouts = new Map();
  globalThis.setTimeout = (callback, delay) => { timeouts.set(++nextTimeout, { callback, delay }); return nextTimeout; };
  globalThis.clearTimeout = id => { timeouts.delete(id); };
  function advanceAfterFeedback() {
    assert.equal(timeouts.size, 1, "Only one automatic advance is scheduled");
    const { callback, delay } = timeouts.values().next().value;
    assert.equal(delay, 700, "Feedback remains visible for a short, predictable pause");
    now += delay;
    callback();
    assert.equal(timeouts.size, 0);
  }
  const ids = ["mistake-review", "mistake-list", "review-note","language", "question-type", "time-warning","hub", "hub-title", "mode-capitals", "mode-survival", "mode-timed", "change-player", "mode-label", "timer",
    "app", "home", "welcome", "game", "result", "login", "login-title", "continue-guest", "test-input",
    "error", "error-panel", "retry", "progress", "round-progress", "score", "player-label", "question",
    "feedback", "next", "restart-round", "keyboard-hint", "alternatives", "summary", "result-title", "result-score", "result-total",
    "restart", "back-to-home", "loading", "loading-text", "sidebar", "menu-toggle", "menu-close", "sidebar-overlay",
    "app-shell", "app-header", "main-content", "header-account", "account-sign-in", "resume-player", "section-label",
    "nav-welcome", "nav-hub", "nav-ranking", "nav-account", "ranking", "account", "leaderboard-title", "account-title", "welcome-title", "hub-greeting"];
  const ui = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  ui["test-input"].tagName = "INPUT";
  ui.home.tagName = "BUTTON";
  ui.next.tagName = "BUTTON";
  for (const id of ["game", "result", "error-panel", "loading", "next", "restart-round"]) ui[id].hidden = true;
  const staticButtons = ["home", "retry", "next", "restart-round", "restart", "back-to-home"].map(id => ui[id]);
  const documentHandlers = {}, windowHandlers = {};
  globalThis.document = {
    documentElement: { lang: "en" },
    getElementById: id => ui[id], createElement: name => new Element("", name.toUpperCase()),
    querySelectorAll: () => [...staticButtons, ...ui.alternatives.children],
    addEventListener: (name, fn) => { documentHandlers[name] = fn; }
  };
  globalThis.window = { addEventListener: (name, fn) => { windowHandlers[name] = fn; },
    matchMedia: () => ({ matches: true, addEventListener() {} }) };
  const requests = [];
  let starts = 0, answers = 0;
  let failNextStart = false;
  let failNextAnswer = false;
  let timedAnswered = 0;
  let expectedMode = "SURVIVAL", expectedType = "CAPITAL", expectedLanguage = "EN", activeMode, expiresAt;
  const initial = { gameId: "round", number: 1, total: 2, score: 0, finished: false,
    question: { text: "Capital?", alternatives: ["A", "B", "C", "D"] } };
  globalThis.fetch = async (url, options) => {
    if (url === "/api/csrf") return { ok: true, json: async () => ({ token: "test-token", headerName: "X-XSRF-TOKEN" }) };
    const body = JSON.parse(options.body);
    requests.push({ url, body });
    let payload;
    if (url === "/api/games") {
      assert.deepEqual(body, { mode: expectedMode, questionType: expectedType, language: expectedLanguage }, "Only the selected mode is sent to Java");
      if (failNextStart) {
        failNextStart = false;
        return { ok: false, json: async () => ({ message: "Could not start a new round." }) };
      }
      starts++;
      activeMode = body.mode;
      timedAnswered = 0;
      expiresAt = activeMode === "TIMED" ? now + 60000 : null;
      payload = { ...initial, gameId: "round-" + starts, mode: activeMode, expiresAt, total: activeMode === "TIMED" ? null : initial.total, answered: 0 };
    } else if (url === "/api/answers") {
      answers++;
      if (answers === 1 || failNextAnswer) {
        failNextAnswer = false;
        throw new Error("Network response lost");
      }
      const correct = body.choice === "B";
      const finished = activeMode !== "TIMED" && ((!correct && activeMode === "SURVIVAL") || body.number === 2);
      if (activeMode === "TIMED") timedAnswered++;
      payload = { correct, correctAnswer: "B", state: { ...initial, gameId: body.gameId,
        score: correct ? body.number : body.number - 1,
        number: finished ? body.number : body.number + 1, finished,
        question: finished ? null : initial.question, mode: activeMode, expiresAt,
        total: activeMode === "TIMED" ? null : initial.total, answered: body.number } };
    } else if (url === "/api/games/status") {
      payload = { ...initial, gameId: body.gameId, mode: "TIMED", expiresAt,
        finished: true, timedOut: true, question: null, total: null, answered: timedAnswered, score: timedAnswered };
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
    const { initializeGameController } = await import("../src/controller/game-controller.js");
    const game = initializeGameController();
    assert.equal(ui.login.hidden, false);
    assert.equal(ui.sidebar.hidden, true);
    game.useAccount(name);
    assert.equal(ui.welcome.hidden, false, "A restored provider identity enters Home automatically");
    game.clearAccount();
    assert.equal(ui.login.hidden, false);
    ui["nav-hub"].click();
    assert.equal(ui.login.hidden, false, "A challenge requires entry first");
    ui["continue-guest"].click();
    assert.equal(ui.welcome.hidden, false);
    assert.equal(ui.login.hidden, true);
    ui["resume-player"].click();
    assert.equal(ui["hub-greeting"].textContent, "Hello, Guest!");
    assert.equal(starts, 0, "Guest entry does not start a round or send a name");
    game.useAccount(name);
    assert.equal(ui.welcome.hidden, false);
    ui["resume-player"].click();
    assert.equal(ui.hub.hidden, false);
    assert.equal(ui["hub-greeting"].textContent, `Hello, ${name}!`);
    const beforeNavigation = requests.length;
    ui["nav-ranking"].click();
    assert.equal(ui.ranking.hidden, false);
    assert.equal(document.activeElement, ui["leaderboard-title"]);
    ui["header-account"].click();
    assert.equal(ui.account.hidden, false);
    ui["nav-welcome"].click();
    assert.equal(ui.welcome.hidden, false);
    assert.equal(ui["resume-player"].hidden, false);
    ui["resume-player"].click();
    assert.equal(ui.hub.hidden, false);
    assert.equal(requests.length, beforeNavigation, "Section navigation does not create API requests");
    ui["mode-survival"].click();
    await settle();
    assert.equal(starts, 1);
    assert.equal(ui.game.hidden, false);
    assert.equal(ui["restart-round"].hidden, true);
    assert.equal(press("r"), false, "Cannot restart an unanswered round with R");
    assert.equal(document.activeElement, ui.question);
    assert.equal(ui["player-label"].textContent, `Your turn, ${name}.`);
    assert.equal(press(" "), false);
    assert.equal(press("b", ui["test-input"]), false);
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
    assert.equal(press("r", ui["test-input"]), false);
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
    assert.equal(ui["hub-greeting"].textContent, `Hello, ${name}!`);
    expectedMode = "CAPITALS";
    ui["mode-capitals"].click();
    await settle();
    press("a");
    await settle();
    assert.equal(ui["restart-round"].hidden, true, "Capitals continues after a mistake");
    assert.equal(timeouts.size, 0, "Untimed modes still wait for the player to continue");
    press(" ", ui.next);
    assert.equal(ui.progress.textContent, "QUESTION 2 OF 2");
    assert.equal(ui["round-progress"].value, 1, "Progress counts answers, not correct answers");
    ui["nav-hub"].click();
    assert.equal(ui.hub.hidden, false);
    expectedMode = "TIMED";
    ui["mode-timed"].click();
    await settle();
    assert.equal(ui.timer.hidden, false);
    assert.match(ui.timer.textContent, /60s/);
    assert.match(ui["keyboard-hint"].textContent, /automatically/);
    for (let i = 0; i < 12; i++) {
      press("b");
      await settle();
      assert.equal(ui.next.hidden, true, "Timed mode never requires a Next question click");
      assert.equal(ui.game.dataset.feedback, "correct");
      assert.equal(document.activeElement, ui.feedback);
      assert.equal(ui.progress.textContent, `QUESTION ${i + 1}`, "Show feedback before advancing");
      const answersBefore = answers;
      assert.equal(press("b"), false, "Do not submit another answer during feedback");
      assert.equal(press(" ", ui.next), false, "Space cannot skip feedback in timed mode");
      assert.equal(answers, answersBefore);
      advanceAfterFeedback();
      assert.equal(ui.feedback.textContent, "");
      assert.equal(document.activeElement, ui.question);
    }
    assert.equal(ui.progress.textContent, "QUESTION 13");
    assert.equal(ui["round-progress"].max, 60);
    assert.equal(ui["round-progress"].value, Math.ceil((expiresAt - now) / 1000));
    now += 60000;
    tick();
    await settle();
    assert.equal(ui.result.hidden, false, "The timer ends an idle round");
    assert.equal(ui["result-title"].textContent, "Time is up!");
    assert.equal(ui["result-total"].textContent, "");
    assert.match(ui.summary.textContent, /12 correct answers from 12 questions/);
    assert.equal(tick, undefined);
    ui["back-to-home"].click();
    ui["mode-timed"].click();
    await settle();
    press("a");
    await settle();
    assert.equal(ui.game.dataset.feedback, "wrong");
    assert.match(ui.feedback.textContent, /The correct answer is B/);
    assert.equal(ui.next.hidden, true);
    advanceAfterFeedback();
    assert.equal(ui.progress.textContent, "QUESTION 2", "Mistakes also advance automatically");
    failNextAnswer = true;
    press("b");
    await settle();
    assert.equal(timeouts.size, 0, "A failed request must not advance before its correction arrives");
    assert.equal(ui["error-panel"].hidden, false);
    const timedSubmission = requests.at(-1).body;
    ui.retry.click();
    await settle();
    assert.deepEqual(requests.at(-1).body, timedSubmission, "Retry the same timed submission");
    assert.equal(timeouts.size, 1);
    ui["nav-hub"].click();
    assert.equal(tick, undefined, "Leaving a timed round cancels its timer");
    assert.equal(timeouts.size, 0, "Leaving during feedback cancels the pending advance");
    assert.equal(ui.hub.hidden, false);
    ui["mode-timed"].click();
    await settle();
    now = expiresAt - 100;
    press("b");
    await settle();
    advanceAfterFeedback();
    assert.equal(ui.progress.textContent, "QUESTION 1", "Never display a new question after the deadline");
    await settle();
    assert.equal(ui.result.hidden, false, "Expiry during feedback shows the server-confirmed result");
    assert.equal(timeouts.size, 0);
    assert.equal(tick, undefined);
    ui["back-to-home"].click();
    ui["mode-timed"].click();
    await settle();
    now = expiresAt - 100;
    press("b");
    await settle();
    assert.equal(timeouts.size, 1);
    now = expiresAt;
    tick();
    await settle();
    assert.equal(ui.result.hidden, false);
    assert.equal(timeouts.size, 0, "The countdown cancels automatic advance when time runs out");
    game.clearAccount();
    assert.equal(ui.login.hidden, false);
    assert.equal(document.activeElement, ui["continue-guest"]);
    assert.ok(requests.every(request => !JSON.stringify(request.body).includes(name)));
    ui.language.value = "PT_BR";
    ui.language.emit("change");
    assert.equal(document.documentElement.lang, "pt-BR");
    ui["continue-guest"].click();
    ui["resume-player"].click();
    assert.equal(ui["hub-greeting"].textContent, "Olá, Visitante!");
    ui["question-type"].value = "COUNTRY";
    expectedType = "COUNTRY";
    expectedLanguage = "PT_BR";
    ui["mode-timed"].click();
    await settle();
    assert.equal(ui.language.disabled, true, "Language stays fixed during a round");
    now += 51000;
    tick();
    assert.equal(ui["time-warning"].textContent, "Restam apenas 9 segundos!");
    assert.match(ui.timer.textContent, /09s/, "Keep the countdown width stable in the final seconds");
    assert.equal(ui.timer.dataset.urgent, "true");
    ui["nav-hub"].click();
    assert.equal(tick, undefined);
    assert.equal(ui.language.disabled, false);
    ui["change-player"].click();
    assert.equal(ui.login.hidden, false);
    assert.equal(ui.sidebar.hidden, true);
  } finally { Object.assign(globalThis, original); Date.now = originalNow; }
});
