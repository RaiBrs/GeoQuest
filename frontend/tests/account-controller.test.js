import assert from "node:assert/strict";
import { test } from "node:test";
import { initializeAccounts } from "../src/controller/account-controller.js";
import { setLanguage } from "../src/i18n.js";

class Element {
  children = []; handlers = {}; value = ""; hidden = false; disabled = false; textContent = "";
  addEventListener(event, handler) { this.handlers[event] = handler; }
  append(...items) { this.children.push(...items); }
  replaceChildren() { this.children = []; }
  querySelectorAll() { return []; }
  emit(name) { return this.handlers[name]?.({ preventDefault() {} }); }
  set innerHTML(value) { throw new Error("Identity and ranking data must remain plain text"); }
}
test("account UI restores identity, renders ranking safely and deletes only after confirmation", async () => {
  const original = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch };
  const ids = ["account-access","login-options","login-google","login-github","account-status","profile-panel","profile-name",
    "load-ranking","logout","ranking-mode","ranking-type","delete-confirmation","delete-account-form","ranking-status","ranking-list","account-error"];
  const ui = Object.fromEntries(ids.map(id => [id, new Element()]));
  globalThis.document = { documentElement: {}, getElementById: id => ui[id], querySelectorAll: () => [], createElement: () => new Element() };
  globalThis.window = { location: { search: "" } };
  const calls = [], used = [];
  let busy = false;
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options });
    return { ok: true, json: async () => path === "/api/account" ? { enabled: true, user: { displayName: "Rai<script>" }, providers: ["google"] }
      : path.startsWith("/api/leaderboard") ? [{ displayName: "Other<script>", score: 9, answered: 10 }] : { ok: true } };
  };
  const settle = () => new Promise(resolve => setImmediate(resolve));
  try {
    setLanguage("PT_BR");
    const controller = initializeAccounts({ useAccount: name => used.push(name), clearAccount: () => used.push("cleared"), setAccountBusy: value => { busy = value; } });
    await settle();
    assert.deepEqual(used, ["Rai<script>"]);
    assert.equal(busy, false);
    assert.equal(ui["login-github"].hidden, true);
    assert.equal(ui["profile-name"].textContent, "Conectado como Rai<script>");
    controller.updateAvailability({ screen: "hub", busy: false });
    ui["ranking-mode"].value = "CAPITALS"; ui["ranking-type"].value = "CAPITAL";
    ui["load-ranking"].emit("click"); await settle();
    assert.equal(ui["ranking-list"].children[0].children[0].textContent, "Other<script> — 9 acertos");
    ui["delete-confirmation"].value = "wrong";
    ui["delete-account-form"].emit("submit"); await settle();
    assert.equal(calls.some(c => c.path === "/api/account/delete"), false);
    ui["delete-confirmation"].value = "DELETE";
    ui["delete-account-form"].emit("submit"); await settle();
    assert.deepEqual(JSON.parse(calls.find(c => c.path === "/api/account/delete").options.body), { confirmation: "DELETE" });
    assert.equal(used.at(-1), "cleared");
    assert.equal(ui["profile-panel"].hidden, true);
    assert.equal(ui["ranking-list"].children.length, 0);
  } finally { setLanguage("EN"); Object.assign(globalThis, original); }
});
