import { t } from "../i18n.js";
import { element } from "./dom.js";

const loadingMessages = {
  start: "Preparing your round…",
  answer: "Checking your answer…"
};

export const shellView = {
  bind({ onHome, onRetry }) {
    element("home").addEventListener("click", onHome);
    element("retry").addEventListener("click", onRetry);
  },

  showScreen(next) {
    for (const id of ["welcome", "hub", "game", "result"]) element(id).hidden = id !== next;
  },

  clearError() {
    element("error-panel").hidden = true;
    element("error").textContent = "";
  },

  showError(message) {
    element("error").textContent = t(message || "Unable to complete the operation.");
    element("error-panel").hidden = false;
  },

  hasError() { return !element("error-panel").hidden; },

  showLoading(operation) { element("loading-text").textContent = t(loadingMessages[operation]); },

  updateControls(busy, answersLocked) {
    for (const button of document.querySelectorAll("button")) {
      button.disabled = busy || (button.classList.contains("alternative") && answersLocked);
    }
    element("player-name").disabled = busy;
    element("app").setAttribute("aria-busy", String(busy));
    element("loading").hidden = !busy;
  },

  focusCurrentStep(screen, answered) {
    if (this.hasError()) element("retry").focus();
    else if (screen === "game") element(answered ? "next" : "question").focus();
    else if (screen === "result") element("result-title").focus();
    else if (screen === "hub") element("hub-title").focus();
  }
};
