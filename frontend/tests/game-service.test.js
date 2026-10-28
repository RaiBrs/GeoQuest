import assert from "node:assert/strict";
import { test, beforeEach } from "node:test";
import { gameService } from "../src/service/game-service.js";
import { resetCsrf } from "../src/service/http-client.js";

beforeEach(resetCsrf);
function mockFetch(handler) {
  globalThis.fetch = async (url, options) => url === "/api/csrf"
    ? { ok: true, json: async () => ({ token: "test-token", headerName: "X-XSRF-TOKEN" }) }
    : handler(url, options);
}

test("starts anonymous rounds and sends choices with CSRF, without names or client scores", async () => {
  const originalFetch = globalThis.fetch, calls = [];
  mockFetch(async (url, options) => { calls.push({ url, options }); return { ok: true, json: async () => ({}) }; });
  try {
    await gameService.start();
    await gameService.answer({ gameId: "round", number: 1, choice: "Brasília" });
    assert.deepEqual(calls.map(call => call.url), ["/api/games", "/api/answers"]);
    assert.deepEqual(JSON.parse(calls[0].options.body), {});
    assert.deepEqual(JSON.parse(calls[1].options.body), { gameId: "round", number: 1, choice: "Brasília" });
    assert.ok(calls.every(call => call.options.method === "POST" && call.options.credentials === "same-origin"));
    assert.ok(calls.every(call => call.options.headers["Content-Type"] === "application/json"));
    assert.ok(calls.every(call => call.options.headers["X-XSRF-TOKEN"] === "test-token"));
  } finally { globalThis.fetch = originalFetch; }
});

test("reports server errors and allows retrying an answer", async () => {
  const originalFetch = globalThis.fetch;
  let attempts = 0;
  mockFetch(async () => ++attempts === 1
    ? { ok: false, json: async () => ({ message: "Service unavailable" }) }
    : { ok: true, json: async () => ({ correct: true }) });
  try {
    const answer = { gameId: "round", number: 1, choice: "Capital" };
    await assert.rejects(gameService.answer(answer), /Service unavailable/);
    assert.deepEqual(await gameService.answer(answer), { correct: true });
  } finally { globalThis.fetch = originalFetch; }
});

test("handles a non-JSON response without exposing parser details", async () => {
  const originalFetch = globalThis.fetch;
  mockFetch(async () => ({ ok: false, json: async () => { throw new SyntaxError("private HTML body"); } }));
  try { await assert.rejects(gameService.start(), /unexpected response/); }
  finally { globalThis.fetch = originalFetch; }
});

test("network failure produces an actionable user message", async () => {
  const originalFetch = globalThis.fetch;
  mockFetch(async () => { throw new Error("private transport details"); });
  try { await assert.rejects(gameService.start(), /Unable to connect/); }
  finally { globalThis.fetch = originalFetch; }
});

test("a null error body uses a safe fallback instead of throwing a TypeError", async () => {
  const originalFetch = globalThis.fetch;
  mockFetch(async () => ({ ok: false, json: async () => null }));
  try { await assert.rejects(gameService.start(), /Unable to complete/); }
  finally { globalThis.fetch = originalFetch; }
});
