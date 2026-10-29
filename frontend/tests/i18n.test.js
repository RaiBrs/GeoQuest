import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { setLanguage, t } from "../src/i18n.js";

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
