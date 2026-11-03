import assert from "node:assert/strict";
import { Element } from "./dom-fixture.js";
import { initializeGameController } from "../../src/controller/game-controller.js";
import { resetCsrf } from "../../src/service/http-client.js";
import { setLanguage } from "../../src/i18n.js";

// Each scenario resets the DOM, responses and clock; no real network or waiting is needed.
export function setupGame(test, options) {
  const original = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch,
    setInterval: globalThis.setInterval, clearInterval: globalThis.clearInterval,
    setTimeout: globalThis.setTimeout, clearTimeout: globalThis.clearTimeout, localStorage: globalThis.localStorage };
  const preferences = new Map();
  globalThis.localStorage = { getItem: key => preferences.get(key) ?? null,
    setItem: (key, value) => preferences.set(key, value) };
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
      if (failNextAnswer) {
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
  resetCsrf();
  test.after(() => {
    setLanguage("EN");
    resetCsrf();
    Object.assign(globalThis, original);
    Date.now = originalNow;
  });
  const game = initializeGameController(options);
  return {
    ui, game, requests, press, settle, advanceAfterFeedback, timeouts, windowHandlers,
    tick: () => tick?.(),
    get timerActive() { return tick !== undefined; },
    get starts() { return starts; },
    get answers() { return answers; },
    get expiresAt() { return expiresAt; },
    get now() { return now; }, set now(value) { now = value; },
    set failNextStart(value) { failNextStart = value; },
    set failNextAnswer(value) { failNextAnswer = value; },
    set expectedMode(value) { expectedMode = value; },
    set expectedType(value) { expectedType = value; },
    set expectedLanguage(value) { expectedLanguage = value; },
    async start(mode = "SURVIVAL") {
      expectedMode = mode;
      ui["continue-guest"].click();
      ui["resume-player"].click();
      ui[`mode-${mode.toLowerCase()}`].click();
      await settle();
    }
  };
}
