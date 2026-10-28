import { t } from "../../i18n.js";
import { element, span } from "../dom.js";

export const resultView = {
  bind({ onRestart, onBackToHome }) {
    element("restart").addEventListener("click", onRestart);
    element("back-to-home").addEventListener("click", onBackToHome);
  },

  renderSummary(state, playerName) {
    element("result-score").textContent = String(state.score);
    const timed = state.mode === "TIMED";
    element("result-total").textContent = timed ? "" : `/${state.total}`;
    element("summary").textContent = t(timed ? "{name}, you got {score} correct answers from {answered} questions in 60 seconds."
      : "{name}, you answered {score} out of {total} questions correctly.", { name: playerName, ...state });
    element("result-title").textContent = t(state.timedOut ? "Time is up!" : !timed && state.score === state.total ? "Perfect round!"
      : state.mode === "SURVIVAL" ? "Survival ended" : "Round complete");
    const mistakes = state.mistakes || [];
    element("mistake-list").replaceChildren();
    element("mistake-review").hidden = mistakes.length === 0;
    element("mistake-review").open = false;
    element("review-note").textContent = t(mistakes.length
      ? "Review your mistakes below. Unanswered questions are not counted as mistakes."
      : "No mistakes in the answers you submitted. Unanswered questions are not counted as mistakes.");
    for (const mistake of mistakes) {
      const item = document.createElement("li");
      item.append(span("review-question", t("Question {number}: {text}", mistake)),
        span("review-selected", t("Your answer: {answer}", { answer: mistake.selectedAnswer })),
        span("review-correct", t("Correct answer: {answer}", { answer: mistake.correctAnswer })));
      element("mistake-list").append(item);
    }
  }
};
