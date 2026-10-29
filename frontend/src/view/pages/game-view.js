import { t } from "../../i18n.js";
import { element, span } from "../dom.js";

function renderScore(score) {
  element("score").textContent = t("Correct answers: {score}", { score });
}

export const gameView = {
  renderMode(mode, timed) {
    element("game").dataset.mode = mode;
    element("mode-label").textContent = {
      CAPITALS: "CAPITALS · 10 QUESTIONS", SURVIVAL: "SURVIVAL · ONE MISTAKE ENDS THE ROUND",
      TIMED: "AGAINST THE CLOCK · 60 SECONDS"
    }[mode];
    element("mode-label").textContent = t(element("mode-label").textContent);
    element("timer").hidden = !timed;
    element("time-warning").textContent = "";
  },

  renderTimer(seconds) {
    element("timer").textContent = t("Time remaining: {seconds}s", { seconds: String(seconds).padStart(2, "0") });
    element("round-progress").value = seconds;
    element("timer").dataset.urgent = String(seconds <= 10);
    element("time-warning").textContent = seconds > 0 && seconds <= 10 ? t("Only {seconds} seconds left!", { seconds }) : "";
  },

  bind({ onNext, onRestart, canRestart, canContinue, canAnswer }) {
    element("next").addEventListener("click", onNext);
    element("restart-round").addEventListener("click", onRestart);
    document.addEventListener("keydown", event => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey) return;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName) || event.target.isContentEditable) return;

      if (event.key.toLowerCase() === "r" && canRestart()) {
        const restart = element("restart-round");
        if (!restart.hidden && !restart.disabled) {
          event.preventDefault();
          restart.click();
        }
        return;
      }

      if (event.key === " ") {
        const next = element("next");
        if (event.shiftKey || !canContinue() || next.hidden || next.disabled) return;
        // Other focused buttons retain their native keyboard behavior.
        if (event.target.tagName === "BUTTON" && event.target !== next) return;
        event.preventDefault();
        next.click();
        return;
      }

      const index = "abcd".indexOf(event.key.toLowerCase());
      if (index < 0 || !canAnswer()) return;
      const button = element("alternatives").children[index];
      if (button && !button.disabled) { event.preventDefault(); button.click(); }
    });
  },

  renderQuestion(state, playerName, onAnswer) {
    element("game").dataset.feedback = "";
    const timed = state.mode === "TIMED";
    element("progress").textContent = t(timed ? "QUESTION {number}" : "QUESTION {number} OF {total}", state);
    renderScore(state.score);
    element("round-progress").max = timed ? 60 : state.total;
    element("round-progress").value = timed ? Math.max(0, Math.ceil((state.expiresAt - Date.now()) / 1000)) : state.number - 1;
    element("round-progress").setAttribute("aria-label", t(timed ? "Time remaining" : "Questions answered"));
    element("player-label").textContent = t("Your turn, {name}.", { name: playerName });
    element("question").textContent = state.question.text;
    element("feedback").textContent = "";
    element("feedback").dataset.tone = "";
    element("next").hidden = true;
    element("restart-round").hidden = true;
    element("keyboard-hint").textContent = t("Press A–D to answer. After answering, press Space to continue.");
    element("alternatives").replaceChildren();
    state.question.alternatives.forEach((choice, index) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "alternative";
      button.dataset.choice = choice;
      button.append(span("option-key", String.fromCharCode(65 + index)),
        span("option-label", choice), span("option-state", ""));
      button.addEventListener("click", () => onAnswer(choice));
      element("alternatives").append(button);
    });
  },

  renderAnswer(answer, choice, playerName) {
    element("game").dataset.feedback = answer.correct ? "correct" : "wrong";
    const state = answer.state;
    renderScore(state.score);
    if (state.mode !== "TIMED") element("round-progress").value = Math.min(state.total,
      Number(element("round-progress").value) + 1);
    for (const button of element("alternatives").children) {
      const correct = button.dataset.choice === answer.correctAnswer;
      const selected = button.dataset.choice === choice;
      if (correct || selected) {
        button.dataset.outcome = correct ? "correct" : "wrong";
        button.lastElementChild.textContent = t(correct ? "✓ Correct" : "Your answer");
      }
    }
    element("feedback").dataset.tone = answer.correct ? "correct" : "wrong";
    element("feedback").textContent = answer.correct
      ? t("Well done, {name}! Correct answer.", { name: playerName })
      : t("Not this time, {name}. The correct answer is {answer}.", { name: playerName, answer: answer.correctAnswer });
    element("next").textContent = t(state.finished ? "View result" : "Next question →");
    element("next").hidden = false;
    element("restart-round").hidden = !state.finished;
    if (state.finished) {
      element("keyboard-hint").textContent = t("Press R to play again or Space to view your result.");
    }
  }
};
