import { request } from "../service/http-client.js";
import { element } from "../view/dom.js";
import { getLanguage } from "../i18n.js";
import { accountView } from "../view/pages/account-view.js";

export function initializeAccounts(game) {
  let account = { enabled: false, user: null, providers: [] };
  let leaders = [], rankingLoaded = false, errorKey = "";
  let pending = false, screen = "login", gameBusy = false;
  let rankingLanguage;

  function render() {
    accountView.render({ account, pending, gameBusy, screen, errorKey });
  }

  // Control locks keep ranking rows intact; data and language changes update the table.
  function renderRanking() {
    accountView.renderRanking(account, leaders, rankingLoaded);
    rankingLanguage = getLanguage();
  }

  function resetRanking() {
    // New filters must not show results from the previous query.
    leaders = [];
    rankingLoaded = false;
    renderRanking();
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
    const type = element("ranking-type").value;
    leaders = await request(`/api/leaderboard?mode=TIMED&questionType=${encodeURIComponent(type)}`);
    rankingLoaded = true;
    renderRanking();
  }));
  element("ranking-type").addEventListener("change", resetRanking);
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
      resetRanking();
      game.clearAccount();
    });
  });
  render();
  renderRanking();
  // Restore the server identity before allowing entry or a new round.
  game.setAccountBusy(true);
  request("/api/account").then(data => {
    account = data;
    if (account.user) game.useAccount(account.user.displayName);
    const query = new URLSearchParams(window.location.search);
    if (query.get("login") === "failed") errorKey = "Sign-in could not be completed. Please try again.";
    render();
    renderRanking();
  }).catch(() => {
    render();
  }).finally(() => game.setAccountBusy(false));
  return { updateAvailability(state) {
    screen = state.screen;
    gameBusy = state.busy;
    render();
    if (rankingLanguage !== getLanguage()) renderRanking();
  } };
}
