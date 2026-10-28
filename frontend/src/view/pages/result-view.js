import { element } from "../dom.js";

export const resultView = {
  bind({ onRestart, onBackToHome }) {
    element("restart").addEventListener("click", onRestart);
    element("back-to-home").addEventListener("click", onBackToHome);
  },

  renderSummary(state, playerName) {
    element("result-score").textContent = String(state.score);
    element("result-total").textContent = `/${state.total}`;
    element("summary").textContent = `${playerName}, you answered ${state.score} out of ${state.total} questions correctly.`;
    element("result-title").textContent = state.timedOut ? "Time is up!" : "Round complete";
  }
};
