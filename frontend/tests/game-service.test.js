import assert from "node:assert/strict";
import { test } from "node:test";
import { gameService } from "../src/service/game-service.js";

test("uses same-origin routes and sends choices rather than client scores", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return { ok: true, json: async () => ({ saved: true }) };
  };
  try {
    await gameService.start("Rai");
    await gameService.answer({ gameId: "round", number: 1, choice: "Brasília" });
    await gameService.saveResult("round");
    await gameService.getRanking();
    assert.deepEqual(calls.map(call => call.url),
      ["/api/games", "/api/answers", "/api/results", "/api/ranking"]);
    assert.deepEqual(JSON.parse(calls[1].options.body),
      { gameId: "round", number: 1, choice: "Brasília" });
    assert.deepEqual(JSON.parse(calls[2].options.body), { gameId: "round" });
    assert.equal(calls[0].options.headers["Content-Type"], "application/json");
    assert.equal(calls[3].options.method, "GET");
    assert.equal(calls[3].options.body, undefined);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("reports server errors and allows retrying a failed save", async () => {
  const originalFetch = globalThis.fetch;
  let attempts = 0;
  globalThis.fetch = async () => {
    attempts++;
    return attempts === 1
      ? { ok: false, json: async () => ({ message: "Banco indisponível" }) }
      : { ok: true, json: async () => ({ saved: true }) };
  };
  try {
    await assert.rejects(gameService.saveResult("round"), /Banco indisponível/);
    assert.deepEqual(await gameService.saveResult("round"), { saved: true });
  } finally {
    globalThis.fetch = originalFetch;
  }
});
