import { element } from "../dom.js";

export const resultView = {
  bind({ onRestart, onChangePlayer }) {
    element("restart").addEventListener("click", onRestart);
    element("change-player").addEventListener("click", onChangePlayer);
  },

  renderSummary(state, playerName) {
    element("result-score").textContent = String(state.score);
    element("result-total").textContent = `/${state.total}`;
    element("summary").textContent = `${playerName}, you answered ${state.score} out of ${state.total} questions correctly.`;
  }
};
