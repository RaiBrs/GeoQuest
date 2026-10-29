import assert from "node:assert/strict";
import { test } from "node:test";
import { shellView } from "../src/view/shell-view.js";

class Element {
  children = []; handlers = {}; dataset = {}; attributes = {}; hidden = false; disabled = false;
  classList = { contains: () => false };
  addEventListener(name, handler) { this.handlers[name] = handler; }
  setAttribute(name, value) { this.attributes[name] = value; }
  removeAttribute(name) { delete this.attributes[name]; }
  querySelectorAll() { return this.children; }
  contains(node) { return this.children.includes(node); }
  focus() { document.activeElement = this; }
  emit(name, extra = {}) {
    let prevented = false;
    this.handlers[name]?.({ preventDefault() { prevented = true; }, stopPropagation() {}, ...extra });
    return prevented;
  }
  click() { if (!this.disabled) this.emit("click"); }
}

test("drawer traps focus, closes with Escape/outside click, and remains closable while loading", () => {
  const original = { document: globalThis.document, window: globalThis.window };
  const ids = ["home", "retry", "sidebar", "menu-toggle", "menu-close", "sidebar-overlay", "app-shell", "app-header",
    "main-content", "header-account", "account-sign-in", "resume-player", "section-label", "nav-welcome", "nav-hub",
    "nav-ranking", "nav-account", "welcome", "hub", "game", "result", "ranking", "account", "player-name", "app", "loading",
    "hub-title", "leaderboard-title", "account-title", "welcome-title"];
  const ui = Object.fromEntries(ids.map(id => [id, new Element()]));
  const buttons = ["home", "retry", "menu-toggle", "menu-close", "sidebar-overlay", "header-account", "account-sign-in",
    "resume-player", "nav-welcome", "nav-hub", "nav-ranking", "nav-account"].map(id => ui[id]);
  for (const id of ["menu-toggle", "menu-close", "sidebar-overlay"]) ui[id].dataset.shellControl = "";
  ui.sidebar.children = [ui["menu-close"], ui["nav-welcome"], ui["nav-hub"], ui["nav-ranking"], ui["nav-account"]];
  const media = { matches: false, addEventListener(name, handler) { this.change = handler; } };
  globalThis.document = { getElementById: id => ui[id], querySelectorAll: () => buttons };
  globalThis.window = { matchMedia: () => media };
  const navigated = [];
  try {
    shellView.bind({ onHome() {}, onRetry() {}, onNavigate(next) { navigated.push(next); ui["hub-title"].focus(); } });
    assert.equal(ui.sidebar.hidden, true);
    ui["menu-toggle"].click();
    assert.equal(ui.sidebar.hidden, false);
    assert.equal(ui["main-content"].inert, true);
    assert.equal(ui.sidebar.attributes["aria-modal"], "true");
    assert.equal(ui["app-header"].inert, true);
    assert.equal(document.activeElement, ui["menu-close"]);
    ui["nav-account"].focus();
    assert.equal(ui.sidebar.emit("keydown", { key: "Tab" }), true);
    assert.equal(document.activeElement, ui["menu-close"]);
    ui.sidebar.emit("keydown", { key: "Tab", shiftKey: true });
    assert.equal(document.activeElement, ui["nav-account"]);
    ui.sidebar.emit("keydown", { key: "Escape" });
    assert.equal(ui.sidebar.hidden, true);
    assert.equal(ui["main-content"].inert, false);
    assert.equal(document.activeElement, ui["menu-toggle"]);

    ui["menu-toggle"].click();
    shellView.updateControls(true, false);
    assert.equal(ui["nav-hub"].disabled, true);
    assert.equal(ui["menu-close"].disabled, false);
    ui["menu-close"].click();
    assert.equal(ui.sidebar.hidden, true, "Slow requests cannot trap the user in the drawer");
    shellView.updateControls(false, false);
    ui["menu-toggle"].click();
    ui["sidebar-overlay"].click();
    assert.equal(ui.sidebar.hidden, true);

    ui["menu-toggle"].click();
    ui["nav-hub"].click();
    assert.deepEqual(navigated, ["hub"]);
    assert.equal(ui.sidebar.hidden, true);
    assert.equal(document.activeElement, ui["hub-title"], "Navigation preserves the destination's focus");
    media.matches = true;
    media.change();
    assert.equal(ui.sidebar.hidden, false);
    assert.equal(ui["sidebar-overlay"].hidden, true);
    assert.equal(ui["main-content"].inert, false);
    shellView.showScreen("game");
    assert.equal(ui["nav-hub"].attributes["aria-current"], "page");
    media.matches = false;
    media.change();
    assert.equal(ui.sidebar.hidden, true);
  } finally { Object.assign(globalThis, original); }
});
