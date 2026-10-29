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
const ids = ["account-access","login-options","login-google","login-github","account-status","profile-panel","profile-name",
  "load-ranking","logout","ranking-mode","ranking-type","delete-confirmation","delete-account-form","ranking-status","ranking-list","account-error",
  "guest-entry","account-guest","user-label","user-initial","ranking-table","entry-card"];
test("account UI restores identity, renders ranking safely and deletes only after confirmation", async () => {
  const original = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch };
  const ui = Object.fromEntries(ids.map(id => [id, new Element()]));
  globalThis.document = { documentElement: {}, getElementById: id => ui[id], querySelectorAll: () => [], createElement: () => new Element() };
  globalThis.window = { location: { search: "" } };
  const calls = [], used = [];
  let busy = false;
  globalThis.fetch = async (path, options) => {
    if (path === "/api/csrf") return { ok: true, json: async () => ({ token: "test-token", headerName: "X-XSRF-TOKEN" }) };
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
    assert.equal(ui["entry-card"].hidden, true, "Authenticated players do not see the entry card");
    controller.updateAvailability({ screen: "hub", busy: false });
    ui["ranking-mode"].value = "CAPITALS"; ui["ranking-type"].value = "CAPITAL";
    ui["load-ranking"].emit("click"); await settle();
    const row = ui["ranking-list"].children[0];
    assert.equal(row.children[0].textContent, "1");
    assert.equal(row.children[1].textContent, "Other<script>");
    assert.equal(row.children[2].children[0].textContent, "9");
    assert.equal(row.children[2].children[1].textContent, "10 respondidas");
    assert.equal(ui["ranking-table"].hidden, false);
    ui["ranking-mode"].value = "TIMED";
    ui["ranking-mode"].emit("change");
    assert.equal(ui["ranking-list"].children.length, 0, "Do not display results belonging to the previous filters");
    assert.equal(ui["ranking-table"].hidden, true);
    assert.equal(ui["user-label"].textContent, "Rai<script>");
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

test("account mutations are blocked during a round and sign-out restores guest presentation", async () => {
  const original = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch };
  const ui = Object.fromEntries(ids.map(id => [id, new Element()]));
  globalThis.document = { documentElement: {}, getElementById: id => ui[id], querySelectorAll: () => [], createElement: () => new Element() };
  globalThis.window = { location: { search: "" } };
  const calls = [];
  let cleared = 0;
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options });
    return { ok: true, json: async () => path === "/api/csrf" ? { token: "test-token", headerName: "X-XSRF-TOKEN" }
      : path === "/api/account" ? { enabled: true, user: { displayName: "Player" }, providers: ["google", "github"] } : { ok: true } };
  };
  const settle = () => new Promise(resolve => setImmediate(resolve));
  try {
    const controller = initializeAccounts({ useAccount() {}, clearAccount() { cleared++; }, setAccountBusy() {} });
    await settle();
    controller.updateAvailability({ screen: "game", busy: false });
    ui["logout"].emit("click");
    ui["delete-confirmation"].value = "DELETE";
    ui["delete-account-form"].emit("submit");
    await settle();
    assert.equal(calls.some(call => call.path === "/api/logout" || call.path === "/api/account/delete"), false);
    controller.updateAvailability({ screen: "account", busy: false });
    ui["logout"].emit("click");
    await settle();
    const logout = calls.find(call => call.path === "/api/logout");
    assert.deepEqual(JSON.parse(logout.options.body), {});
    assert.equal(logout.options.headers["X-XSRF-TOKEN"], "test-token");
    assert.equal(cleared, 1);
    assert.equal(ui["profile-panel"].hidden, true);
    assert.equal(ui["account-guest"].hidden, false);
    assert.equal(ui["entry-card"].hidden, false, "Signing out restores visitor entry");
    assert.equal(ui["user-label"].textContent, "Guest");
  } finally { Object.assign(globalThis, original); }
});
