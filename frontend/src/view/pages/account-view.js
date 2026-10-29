import { element, span } from "../dom.js";
import { t } from "../../i18n.js";

export const accountView = {
  render({ account, pending, gameBusy, screen, errorKey }) {
    const unavailable = pending || gameBusy || screen === "game";
    const user = account.user;
    element("account-access").hidden = screen !== "welcome";
    element("login-options").hidden = !account.enabled || Boolean(user);
    element("login-google").hidden = !account.providers.includes("google");
    element("login-github").hidden = !account.providers.includes("github");
    element("account-status").textContent = user ? t("Signed in as {name}", { name: user.displayName })
      : t(account.enabled ? "Sign in to save results, or play as a guest."
        : "Online accounts are unavailable. You can still play as a guest.");
    element("guest-entry").hidden = Boolean(user);
    element("account-guest").hidden = Boolean(user);
    element("profile-panel").hidden = !user;
    element("profile-name").textContent = user ? t("Signed in as {name}", { name: user.displayName }) : "";
    element("user-label").textContent = user ? user.displayName : t("Guest");
    element("user-initial").textContent = [...(user?.displayName || t("Guest"))][0].toLocaleUpperCase();
    for (const id of ["logout", "ranking-mode", "ranking-type", "delete-confirmation"]) element(id).disabled = unavailable;
    element("load-ranking").disabled = unavailable || !account.enabled;
    for (const button of element("delete-account-form").querySelectorAll("button")) button.disabled = unavailable;
    element("account-error").textContent = errorKey ? t(errorKey) : "";
  },

  renderRanking(account, leaders, loaded) {
    element("ranking-list").replaceChildren();
    element("ranking-table").hidden = leaders.length === 0;
    element("ranking-status").textContent = !account.enabled ? t("Online ranking is unavailable.")
      : loaded ? t(leaders.length ? "Top 20 players" : "No completed ranked rounds yet.")
        : t("Choose a mode and question type to view the ranking.");
    for (const [index, leader] of leaders.entries()) {
      const row = document.createElement("tr");
      const position = document.createElement("td");
      position.className = "ranking-position";
      position.textContent = String(index + 1);
      const name = document.createElement("td");
      name.textContent = leader.displayName;
      const score = document.createElement("td");
      score.className = "ranking-score";
      const answered = document.createElement("small");
      answered.textContent = t("{answered} answered", leader);
      score.append(span("score-value", String(leader.score)), answered);
      row.append(position, name, score);
      element("ranking-list").append(row);
    }
  }
};
