import { gameService } from "../service/game-service.js";
import { gameView } from "../view/pages/game-view.js";
import { resultView } from "../view/pages/result-view.js";
import { welcomeView } from "../view/pages/welcome-view.js";
import { shellView } from "../view/shell-view.js";
import { hubView } from "../view/pages/hub-view.js";

export function initializeGameController() {
  let screen = "welcome";
  let playerName = "";
  let state;
  let answer;
  let pendingAnswer;
  let busy = false;
  let retryTask;
  let selectedMode = "SURVIVAL";
  let timer;

  function stopTimer() { clearInterval(timer); timer = undefined; }

  function showHub() {
    stopTimer();
    state = answer = pendingAnswer = undefined;
    hubView.render(playerName);
    showScreen("hub");
    updateControls();
    focusCurrentStep();
  }

  function updateTimer() {
    if (!state?.expiresAt || screen !== "game") return;
    const seconds = Math.max(0, Math.ceil((state.expiresAt - Date.now()) / 1000));
    gameView.renderTimer(seconds);
    if (seconds === 0 && !busy && !shellView.hasError()) {
      stopTimer();
      run(async () => {
        state = await gameService.status(state.gameId);
        if (state.finished) showResult();
        else timer = setInterval(updateTimer, 250);
      }, "answer");
    }
  }

  function showResult() {
    stopTimer();
    resultView.renderSummary(state, playerName);
    showScreen("result");
    focusCurrentStep();
  }

  function showScreen(next) {
    screen = next;
    shellView.showScreen(next);
  }

  function clearError() {
    shellView.clearError();
    retryTask = undefined;
  }

  function updateControls() {
    shellView.updateControls(busy, answer != null || pendingAnswer != null);
  }

  function focusCurrentStep() { shellView.focusCurrentStep(screen, answer != null); }

  async function run(action, operation) {
    if (busy) return;
    clearError();
    busy = true;
    shellView.showLoading(operation);
    updateControls();
    try {
      await action();
    } catch (error) {
      retryTask = { action, operation };
      shellView.showError(error.message);
    } finally {
      busy = false;
      updateControls();
      // Disabled buttons cannot receive focus; enable controls before restoring it.
      focusCurrentStep();
    }
  }

  async function startGame(name) {
    const nextState = await gameService.start(selectedMode);
    playerName = name;
    state = nextState;
    pendingAnswer = undefined;
    showScreen("game");
    renderQuestion();
    stopTimer();
    gameView.renderMode(selectedMode, state.expiresAt != null);
    if (state.expiresAt) { timer = setInterval(updateTimer, 250); updateTimer(); }
  }

  function renderQuestion() {
    answer = undefined;
    gameView.renderQuestion(state, playerName, chooseAnswer);
    updateControls();
  }

  function chooseAnswer(choice) {
    if (busy || pendingAnswer || answer) return;
    const submission = { gameId: state.gameId, number: state.number, choice };
    pendingAnswer = submission;
    // Replay the exact submission after an ambiguous network failure.
    run(() => submitAnswer(submission), "answer");
  }

  async function submitAnswer(submission) {
    answer = await gameService.answer(submission);
    state = answer.state;
    pendingAnswer = undefined;
    if (state.timedOut) { showResult(); return; }
    if (state.finished) stopTimer();
    gameView.renderAnswer(answer, submission.choice, playerName);
  }

  function navigate(action) {
    if (busy) return;
    clearError();
    action();
  }

  function resetPlayer() {
    stopTimer();
    state = answer = pendingAnswer = undefined;
    playerName = "";
    welcomeView.reset();
    showScreen("welcome");
    updateControls();
    welcomeView.focus();
  }

  function continueRound() {
    if (busy || !answer) return;
    clearError();
    if (!state.finished) { renderQuestion(); focusCurrentStep(); return; }
    showResult();
  }

  function canUseShortcuts() { return screen === "game" && !busy; }

  function restartRound() {
    if (busy || !state?.finished) return;
    const name = playerName;
    run(() => startGame(name), "start");
  }

  welcomeView.bind({
    onSubmit() {
      if (busy) return;
      const name = welcomeView.readPlayerName();
      if (name !== undefined) { playerName = name; showHub(); }
    },
    onInput: clearError
  });
  gameView.bind({
    onNext: continueRound,
    onRestart: restartRound,
    canRestart: () => canUseShortcuts() && state?.finished,
    canContinue: () => canUseShortcuts() && answer != null && !shellView.hasError(),
    canAnswer: () => canUseShortcuts() && !answer && !pendingAnswer
  });
  resultView.bind({
    onRestart: restartRound,
    onBackToHome: () => navigate(() => playerName ? showHub() : resetPlayer())
  });
  shellView.bind({
    onHome: () => navigate(() => playerName ? showHub() : resetPlayer()),
    onRetry: () => { if (retryTask) run(retryTask.action, retryTask.operation); }
  });
  hubView.bind({
    onSelectMode(mode) {
      if (busy) return;
      selectedMode = mode;
      run(() => startGame(playerName), "start");
    },
    onChangePlayer: () => navigate(resetPlayer)
  });
}
