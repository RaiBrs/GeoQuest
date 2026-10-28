import { element, span } from "../view/dom.js";
import { t } from "../i18n.js";

async function request(path, data) {
  let response;
  try {
    response = await fetch(path, data === undefined ? { credentials: "same-origin" } : {
      method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data)
    });
  } catch { throw new Error("Unable to connect. Check your connection and try again."); }
  let body;
  try { body = await response.json(); }
  catch { throw new Error("The server sent an unexpected response. Please try again shortly."); }
  if (!response.ok) throw new Error(body.message || "Unable to complete the operation.");
  return body;
}

export function initializeAccounts(game) {
  let account = { enabled: false, user: null, providers: [] };
  let leaders = [], rankingLoaded = false, errorKey = "";
  let pending = false, screen = "welcome", gameBusy = false;

  function render() {
    const unavailable = pending || gameBusy || screen === "game";
    element("account-access").hidden = screen !== "welcome";
    element("login-options").hidden = !account.enabled || Boolean(account.user);
    element("login-google").hidden = !account.providers.includes("google");
    element("login-github").hidden = !account.providers.includes("github");
    element("account-status").textContent = t(account.enabled
      ? "Sign in to save results, or play as a guest." : "Online accounts are unavailable. You can still play as a guest.");
    element("profile-panel").hidden = !account.user;
    element("profile-name").textContent = account.user ? t("Signed in as {name}", { name: account.user.displayName }) : "";
    element("load-ranking").disabled = unavailable || !account.enabled;
    element("logout").disabled = unavailable;
    element("ranking-mode").disabled = unavailable;
    element("ranking-type").disabled = unavailable;
    element("delete-confirmation").disabled = unavailable;
    for (const button of element("delete-account-form").querySelectorAll("button")) button.disabled = unavailable;
    element("account-error").textContent = errorKey ? t(errorKey) : "";
    element("ranking-list").replaceChildren();
    element("ranking-status").textContent = !account.enabled ? t("Online ranking is unavailable.")
      : rankingLoaded ? t(leaders.length ? "Top 20 players" : "No completed ranked rounds yet.") : "";
    for (const leader of leaders) {
      const row = document.createElement("li");
      row.append(span("ranking-name", t("{name} — {score} correct answers", { name: leader.displayName, score: leader.score })));
      element("ranking-list").append(row);
    }
  }
  async function perform(action) {
    if (pending || gameBusy || screen === "game") return;
    pending = true;
    game.setAccountBusy(true);
    errorKey = "";
    render();
    try { await action(); }
    catch (error) { errorKey = error.message; }
    finally { pending = false; game.setAccountBusy(false); render(); }
  }
  element("load-ranking").addEventListener("click", () => perform(async () => {
    const mode = element("ranking-mode").value, type = element("ranking-type").value;
    leaders = await request(`/api/leaderboard?mode=${encodeURIComponent(mode)}&questionType=${encodeURIComponent(type)}`);
    rankingLoaded = true;
  }));
  element("logout").addEventListener("click", () => perform(async () => {
    await request("/api/logout", {});
    account.user = null;
    game.clearAccount();
  }));
  element("delete-account-form").addEventListener("submit", event => {
    event.preventDefault();
    perform(async () => {
      if (element("delete-confirmation").value !== "DELETE") throw new Error("Type DELETE to confirm account deletion.");
      await request("/api/account/delete", { confirmation: "DELETE" });
      account.user = null;
      element("delete-confirmation").value = "";
      leaders = [];
      rankingLoaded = false;
      game.clearAccount();
    });
  });
  render();
  game.setAccountBusy(true);
  request("/api/account").then(data => {
    account = data;
    if (account.user) game.useAccount(account.user.displayName);
    const query = new URLSearchParams(window.location.search);
    if (query.get("login") === "failed") errorKey = "Sign-in could not be completed. Please try again.";
    render();
  }).catch(() => {
    element("account-status").textContent = t("Online accounts are unavailable. You can still play as a guest.");
  }).finally(() => game.setAccountBusy(false));
  return { updateAvailability(state) { screen = state.screen; gameBusy = state.busy; render(); } };
}
