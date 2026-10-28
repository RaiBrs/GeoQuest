import { element, span } from "../dom.js";

const saveMessages = {
  saving: "Saving your result…",
  error: "Your result has not been saved yet. You can try again.",
  saved: "Result saved. Your best score has been kept.",
  discarded: "Result discarded."
};

export const resultView = {
  bind({ onRestart, onChangePlayer }) {
    element("restart").addEventListener("click", onRestart);
    element("change-player").addEventListener("click", onChangePlayer);
  },

  renderSummary(state, playerName) {
    element("result-score").textContent = String(state.score);
    element("result-total").textContent = `/${state.total}`;
    element("summary").textContent = `${playerName}, you answered ${state.score} out of ${state.total} questions correctly.`;
  },

  showSaveStatus(status) {
    element("save-status").dataset.status = status;
    element("save-status").textContent = saveMessages[status];
  },

  showRankingLoading() { element("ranking-status").textContent = "Loading leaderboard…"; },

  showRankingError() {
    element("ranking-status").textContent = "Your result is saved. The leaderboard could not be loaded yet.";
  },

  clearRanking() { element("ranking").replaceChildren(); },

  renderRanking(ranking) {
    this.clearRanking();
    ranking.forEach((entry, index) => {
      const item = document.createElement("li");
      const position = span("rank-index", String(index + 1).padStart(2, "0"));
      position.setAttribute("aria-hidden", "true");
      item.append(position, span("ranking-name", entry.playerName),
        span("ranking-score", `${entry.score}/${entry.totalQuestions}`));
      element("ranking").append(item);
    });
    element("ranking-status").textContent = ranking.length
      ? "" : "The leaderboard is empty. Be the first to leave your mark!";
  }
};
