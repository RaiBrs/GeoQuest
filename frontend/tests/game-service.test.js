import assert from "node:assert/strict";
import { test } from "node:test";
import { gameService } from "../src/service/game-service.js";

test("starts anonymous rounds and sends answer choices without names or client scores", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return { ok: true, json: async () => ({}) };
  };
  try {
    await gameService.start();
    await gameService.answer({ gameId: "round", number: 1, choice: "Brasília" });
    assert.deepEqual(calls.map(call => call.url), ["/api/games", "/api/answers"]);
    assert.deepEqual(JSON.parse(calls[0].options.body), {});
    assert.deepEqual(JSON.parse(calls[1].options.body),
      { gameId: "round", number: 1, choice: "Brasília" });
    assert.ok(calls.every(call => call.options.method === "POST"));
    assert.ok(calls.every(call => call.options.headers["Content-Type"] === "application/json"));
  } finally { globalThis.fetch = originalFetch; }
});

test("reports server errors and allows retrying an answer", async () => {
  const originalFetch = globalThis.fetch;
  let attempts = 0;
  globalThis.fetch = async () => ++attempts === 1
    ? { ok: false, json: async () => ({ message: "Service unavailable" }) }
    : { ok: true, json: async () => ({ correct: true }) };
  try {
    const answer = { gameId: "round", number: 1, choice: "Capital" };
    await assert.rejects(gameService.answer(answer), /Service unavailable/);
    assert.deepEqual(await gameService.answer(answer), { correct: true });
  } finally { globalThis.fetch = originalFetch; }
});

test("handles a non-JSON response without exposing parser details", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({ ok: false, json: async () => { throw new SyntaxError("private HTML body"); } });
  try { await assert.rejects(gameService.start(), /unexpected response/); }
  finally { globalThis.fetch = originalFetch; }
});

test("network failure produces an actionable user message", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("private transport details"); };
  try { await assert.rejects(gameService.start(), /Unable to connect/); }
  finally { globalThis.fetch = originalFetch; }
});
