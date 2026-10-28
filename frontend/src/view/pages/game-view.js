import { element, span } from "../dom.js";

function renderScore(score) {
  element("score").textContent = `Correct answers: ${score}`;
}

export const gameView = {
  bind({ onNext, canContinue, canAnswer }) {
    element("next").addEventListener("click", onNext);
    document.addEventListener("keydown", event => {
      if (event.repeat || event.ctrlKey || event.altKey || event.metaKey) return;
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(event.target.tagName) || event.target.isContentEditable) return;

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
    element("progress").textContent = `QUESTION ${state.number} OF ${state.total}`;
    renderScore(state.score);
    element("round-progress").max = state.total;
    element("round-progress").value = state.score;
    element("player-label").textContent = `Your turn, ${playerName}.`;
    element("question").textContent = state.question.text;
    element("feedback").textContent = "";
    element("feedback").dataset.tone = "";
    element("next").hidden = true;
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

  renderAnswer(answer, choice) {
    const state = answer.state;
    renderScore(state.score);
    element("round-progress").value = state.score + (answer.correct ? 0 : 1);
    for (const button of element("alternatives").children) {
      const correct = button.dataset.choice === answer.correctAnswer;
      const selected = button.dataset.choice === choice;
      if (correct || selected) {
        button.dataset.outcome = correct ? "correct" : "wrong";
        button.lastElementChild.textContent = correct ? "✓ Correct" : "Your answer";
      }
    }
    element("feedback").dataset.tone = answer.correct ? "correct" : "wrong";
    element("feedback").textContent = answer.correct
      ? "Well done! Correct answer." : `The correct answer is ${answer.correctAnswer}.`;
    element("next").textContent = state.finished ? "View result" : "Next question →";
    element("next").hidden = false;
  }
};
