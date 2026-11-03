import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { getLanguage, initializeLanguage, setLanguage, t } from "../src/i18n.js";

test("restores the selected language after reload, OAuth return and a new tab", async () => {
  const originalDocument = globalThis.document;
  const originalStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  const saved = new Map();
  Object.defineProperty(globalThis, "localStorage", { configurable: true, value: {
    getItem: key => saved.get(key) ?? null,
    setItem: (key, value) => saved.set(key, value)
  } });
  const selector = {};
  globalThis.document = { documentElement: {}, getElementById: () => selector, querySelectorAll: () => [] };
  try {
    initializeLanguage();
    assert.equal(getLanguage(), "EN", "First visit retains the existing default");
    setLanguage("PT_BR");
    for (const scenario of ["reload", "OAuth return", "new tab"]) {
      const freshPage = await import(`../src/i18n.js?scenario=${encodeURIComponent(scenario)}`);
      freshPage.initializeLanguage();
      assert.equal(freshPage.getLanguage(), "PT_BR", scenario);
      assert.equal(selector.value, "PT_BR");
      assert.equal(document.documentElement.lang, "pt-BR");
      assert.equal(document.title, "GeoQuest — Explore o mundo");
      assert.equal(freshPage.t("Guest"), "Visitante");
    }
    setLanguage("EN");
    initializeLanguage();
    assert.equal(getLanguage(), "EN", "Switching back to English is also remembered");
    saved.set("geoquest.language", "invalid");
    initializeLanguage();
    assert.equal(getLanguage(), "EN", "Invalid stored values use the default");
  } finally {
    globalThis.document = originalDocument;
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else delete globalThis.localStorage;
  }
});

test("blocked browser storage does not prevent translation", () => {
  const originalDocument = globalThis.document;
  const originalStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  globalThis.document = { documentElement: {}, querySelectorAll: () => [] };
  Object.defineProperty(globalThis, "localStorage", { configurable: true, get() { throw new Error("Storage blocked"); } });
  try {
    assert.doesNotThrow(initializeLanguage);
    assert.doesNotThrow(() => setLanguage("PT_BR"));
    assert.equal(t("Guest"), "Visitante");
  } finally {
    setLanguage("EN");
    globalThis.document = originalDocument;
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else delete globalThis.localStorage;
  }
});

test("all static interface labels have Portuguese translations", async () => {
  const originalDocument = globalThis.document;
  globalThis.document = { documentElement: {}, querySelectorAll: () => [] };
  try {
    const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
    const labels = [...html.matchAll(/data-i18n(?:-placeholder|-aria)?="([^"]+)"/g)].map(match => match[1]);
    setLanguage("PT_BR");
    for (const key of new Set(labels)) {
      assert.notEqual(t(key), t("Unable to complete the operation."), `Missing translation: ${key}`);
    }
  } finally { setLanguage("EN"); globalThis.document = originalDocument; }
});

test("document IDs are unique and every label targets an existing control", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
  assert.equal(new Set(ids).size, ids.length, "Duplicate IDs break screen controls and accessible labels");
  for (const [, id] of html.matchAll(/\bfor="([^"]+)"/g)) assert.ok(ids.includes(id), `Missing label target: ${id}`);
});
