import { request } from "../service/http-client.js";
import { element } from "../view/dom.js";
import { t } from "../i18n.js";
import { accountView } from "../view/pages/account-view.js";

export function initializeAccounts(game) {
  let account = { enabled: false, user: null, providers: [] };
  let leaders = [], rankingLoaded = false, errorKey = "";
  let pending = false, screen = "welcome", gameBusy = false;

  function render() {
    accountView.render({ account, pending, gameBusy, screen, errorKey });
    accountView.renderRanking(account, leaders, rankingLoaded);
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
  for (const id of ["ranking-mode", "ranking-type"]) {
    element(id).addEventListener("change", () => {
      leaders = [];
      rankingLoaded = false;
      render();
    });
  }
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
