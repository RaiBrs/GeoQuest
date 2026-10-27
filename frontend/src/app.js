import { gameService } from "./service/game-service.js";

const element = id => document.getElementById(id);
const screens = ["welcome", "game", "result"];
let playerName = "";
let state;
let answer;
let pendingAnswer;
let busy = false;
let retryAction;

function showScreen(screen) {
  for (const id of screens) element(id).hidden = id !== screen;
}

function updateButtons() {
  for (const button of document.querySelectorAll("button")) {
    const lockedAlternative = button.classList.contains("alternative") &&
      (answer != null || pendingAnswer != null);
    button.disabled = busy || lockedAlternative;
  }
}

async function run(action) {
  if (busy) return;
  busy = true;
  element("error").hidden = true;
  element("retry").hidden = true;
  document.body.setAttribute("aria-busy", "true");
  updateButtons();
  try {
    await action();
    retryAction = undefined;
  } catch (error) {
    retryAction = action;
    element("error").textContent = error.message || "Não foi possível conectar ao servidor.";
    element("error").hidden = false;
    element("retry").hidden = false;
  } finally {
    busy = false;
    document.body.setAttribute("aria-busy", "false");
    updateButtons();
  }
}

async function startGame() {
  const newState = await gameService.start(playerName);
  state = newState;
  pendingAnswer = undefined;
  element("save-status").textContent = "";
  element("ranking").replaceChildren();
  showScreen("game");
  renderQuestion();
}

function renderQuestion() {
  answer = undefined;
  element("progress").textContent = `Pergunta ${state.number} de ${state.total}`;
  element("score").textContent = `Acertos: ${state.score}`;
  element("question").textContent = state.question.text;
  element("feedback").textContent = "";
  element("next").hidden = true;
  element("alternatives").replaceChildren();
  for (const alternative of state.question.alternatives) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "alternative";
    button.textContent = alternative;
    button.addEventListener("click", () => {
      if (busy || pendingAnswer || answer) return;
      const submission = { gameId: state.gameId, number: state.number, choice: alternative };
      pendingAnswer = submission;
      run(() => submitAnswer(submission));
    });
    element("alternatives").append(button);
  }
  element("question").focus();
}

async function submitAnswer(submission) {
  answer = await gameService.answer(submission);
  state = answer.state;
  pendingAnswer = undefined;
  element("score").textContent = `Acertos: ${state.score}`;
  element("feedback").textContent = answer.correct
    ? "Resposta correta!" : `A resposta correta é ${answer.correctAnswer}.`;
  element("next").textContent = state.finished ? "Ver resultado" : "Próxima pergunta";
  element("next").hidden = false;
}

async function saveAndLoadRanking() {
  try {
    await gameService.saveResult(state.gameId);
  } catch (error) {
    element("save-status").textContent = "Resultado ainda não salvo. Tente novamente.";
    throw error;
  }
  element("save-status").textContent = "Resultado registrado. Seu melhor resultado foi preservado.";
  const ranking = await gameService.getRanking();
  element("ranking").replaceChildren();
  for (const entry of ranking) {
    const item = document.createElement("li");
    item.textContent = `${entry.playerName} — ${entry.score}/${entry.totalQuestions}`;
    element("ranking").append(item);
  }
}

element("player-form").addEventListener("submit", event => {
  event.preventDefault();
  if (busy) return;
  const input = element("player-name");
  const name = input.value.trim();
  if (!name || [...name].length > 80 || /\p{Cc}/u.test(name)) {
    input.setCustomValidity("Informe um nome de até 80 caracteres, sem caracteres de controle.");
    input.reportValidity();
    return;
  }
  playerName = name;
  run(startGame);
});
element("player-name").addEventListener("input", event => event.target.setCustomValidity(""));
element("next").addEventListener("click", () => {
  if (busy) return;
  if (!state.finished) {
    renderQuestion();
    updateButtons();
    return;
  }
  element("summary").textContent = `${playerName}, você acertou ${state.score} de ${state.total} perguntas.`;
  element("save-status").textContent = "Salvando resultado…";
  showScreen("result");
  element("result-title").focus();
  run(saveAndLoadRanking);
});
element("restart").addEventListener("click", () => run(startGame));
element("change-player").addEventListener("click", () => {
  if (busy) return;
  state = undefined;
  answer = undefined;
  pendingAnswer = undefined;
  retryAction = undefined;
  playerName = "";
  element("error").hidden = true;
  element("retry").hidden = true;
  element("player-form").reset();
  showScreen("welcome");
  element("player-name").focus();
});
element("retry").addEventListener("click", () => { if (retryAction) run(retryAction); });
