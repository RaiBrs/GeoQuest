import { t } from "../i18n.js";
import { element } from "./dom.js";

const loadingMessages = {
  start: "Preparing your round…",
  answer: "Checking your answer…"
};

export const shellView = {
  bind({ onHome, onRetry, onNavigate }) {
    element("home").addEventListener("click", onHome);
    element("retry").addEventListener("click", onRetry);
    const sidebar = element("sidebar");
    const toggle = element("menu-toggle");
    const desktop = window.matchMedia("(min-width: 1100px)");
    let open = desktop.matches;
    let currentScreen = "login";

    function setOpen(value, restoreFocus = false) {
      open = value && currentScreen !== "login";
      const modal = open && !desktop.matches;
      element("app-shell").dataset.sidebar = open ? "open" : "closed";
      sidebar.hidden = !open;
      toggle.setAttribute("aria-expanded", String(open));
      element("sidebar-overlay").hidden = !modal;
      element("app-header").inert = modal;
      element("main-content").inert = modal;
      if (modal) {
        sidebar.setAttribute("role", "dialog");
        sidebar.setAttribute("aria-modal", "true");
      } else {
        sidebar.removeAttribute("role");
        sidebar.removeAttribute("aria-modal");
      }
      if (modal) element("menu-close").focus();
      else if (restoreFocus) toggle.focus();
    }

    toggle.addEventListener("click", () => setOpen(!open));
    element("menu-close").addEventListener("click", () => setOpen(false, true));
    element("sidebar-overlay").addEventListener("click", () => setOpen(false, true));
    desktop.addEventListener("change", () => {
      const focusWasInside = sidebar.contains(document.activeElement);
      setOpen(desktop.matches, !desktop.matches && focusWasInside);
    });
    sidebar.addEventListener("keydown", event => {
      if (!open || desktop.matches) return;
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        setOpen(false, true);
      } else if (event.key === "Tab") {
        const buttons = [...sidebar.querySelectorAll("button")].filter(button => !button.disabled && !button.hidden);
        const target = event.shiftKey ? buttons.at(-1) : buttons[0];
        if (document.activeElement === (event.shiftKey ? buttons[0] : buttons.at(-1))) {
          event.preventDefault();
          target.focus();
        }
      }
    });
    function navigate(next) {
      if (!desktop.matches) setOpen(false);
      onNavigate(next);
    }
    for (const next of ["welcome", "hub", "ranking", "account"]) {
      element(`nav-${next}`).addEventListener("click", () => navigate(next));
    }
    element("header-account").addEventListener("click", () => navigate("account"));
    element("account-sign-in").addEventListener("click", () => navigate("login"));
    element("resume-player").addEventListener("click", () => navigate("hub"));
    this.syncNavigation = next => {
      const entering = currentScreen === "login" && next !== "login";
      currentScreen = next;
      if (next === "login") setOpen(false);
      else if (entering) setOpen(desktop.matches);
    };
    this.showScreen("login");
  },

  showScreen(next) {
    for (const id of ["login", "welcome", "hub", "game", "result", "ranking", "account"]) element(id).hidden = id !== next;
    this.syncNavigation(next);
    element("menu-toggle").hidden = next === "login";
    element("header-account").hidden = next === "login";
    element("app-shell").dataset.screen = next;
    const active = next === "game" || next === "result" ? "hub" : next;
    for (const id of ["welcome", "hub", "ranking", "account"]) {
      element(`nav-${id}`).setAttribute("aria-current", id === active ? "page" : "false");
    }
    element("section-label").textContent = t({ login: "Welcome", welcome: "How to play", hub: "Challenges",
      ranking: "Ranking", account: "My account", game: "Challenge in progress", result: "Round complete" }[next]);
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
      // Drawer controls must remain usable while a request is pending.
      if ("shellControl" in button.dataset) continue;
      button.disabled = busy || (button.classList.contains("alternative") && answersLocked);
    }
    element("app").setAttribute("aria-busy", String(busy));
    element("loading").hidden = !busy;
  },

  focusCurrentStep(screen, answered) {
    if (element("app-header").inert) return;
    if (this.hasError()) element("retry").focus();
    else if (screen === "game") element(answered ? "next" : "question").focus();
    else if (screen === "result") element("result-title").focus();
    else if (screen === "hub") element("hub-title").focus();
    else if (screen === "ranking") element("leaderboard-title").focus();
    else if (screen === "account") element("account-title").focus();
    else if (screen === "welcome") element("welcome-title").focus();
    else if (screen === "login") element("login-title").focus();
  }
};
