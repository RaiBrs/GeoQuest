import { gameService } from "../service/game-service.js";
import { gameView } from "../view/pages/game-view.js";
import { resultView } from "../view/pages/result-view.js";
import { welcomeView } from "../view/pages/welcome-view.js";
import { shellView } from "../view/shell-view.js";

export function initializeGameController() {
  let screen = "welcome";
  let playerName = "";
  let state;
  let answer;
  let pendingAnswer;
  let busy = false;
  let retryTask;
  let saveState = "idle";
  let pendingNavigation;

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
    const nextState = await gameService.start(name);
    playerName = name;
    state = nextState;
    saveState = "idle";
    pendingAnswer = undefined;
    resultView.clearRanking();
    showScreen("game");
    renderQuestion();
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
    if (state.finished) saveState = "pending";
    gameView.renderAnswer(answer, submission.choice);
  }

  async function loadResult(gameId) {
    if (saveState !== "saved") {
      resultView.showSaveStatus("saving");
      try {
        await gameService.saveResult(gameId);
        saveState = "saved";
      } catch (error) {
        resultView.showSaveStatus("error");
        throw error;
      }
    }
    resultView.showSaveStatus("saved");
    resultView.showRankingLoading();
    let ranking;
    try {
      ranking = await gameService.getRanking();
    } catch (error) {
      resultView.showRankingError();
      throw error;
    }
    resultView.renderRanking(ranking);
  }

  function hasUnsavedResult() { return state?.finished && saveState === "pending"; }

  function navigate(action) {
    if (busy) return;
    if (!hasUnsavedResult()) { clearError(); action(); return; }
    pendingNavigation = action;
    shellView.requestDiscard();
  }

  function resetPlayer() {
    state = answer = pendingAnswer = undefined;
    saveState = "idle";
    playerName = "";
    welcomeView.reset();
    showScreen("welcome");
    updateControls();
    welcomeView.focus();
  }

  function continueRound() {
    if (busy || !answer) return;
    if (!state.finished) { renderQuestion(); focusCurrentStep(); return; }
    resultView.renderSummary(state, playerName);
    showScreen("result");
    const gameId = state.gameId;
    run(() => loadResult(gameId), "result");
  }

  function canUseShortcuts() { return screen === "game" && !busy && !shellView.isDiscardOpen(); }

  welcomeView.bind({
    onSubmit() {
      if (busy) return;
      const name = welcomeView.readPlayerName();
      if (name !== undefined) run(() => startGame(name), "start");
    },
    onInput: clearError
  });
  gameView.bind({
    onNext: continueRound,
    canContinue: () => canUseShortcuts() && answer != null && !shellView.hasError(),
    canAnswer: () => canUseShortcuts() && !answer && !pendingAnswer
  });
  resultView.bind({
    onRestart: () => navigate(() => {
      const name = playerName;
      run(() => startGame(name), "start");
    }),
    onChangePlayer: () => navigate(resetPlayer)
  });
  shellView.bind({
    onHome: () => navigate(resetPlayer),
    onRetry: () => { if (retryTask) run(retryTask.action, retryTask.operation); },
    onDiscard(discarded) {
      const action = pendingNavigation;
      pendingNavigation = undefined;
      if (!discarded || !action) return;
      clearError();
      saveState = "discarded";
      resultView.showSaveStatus("discarded");
      action();
    },
    hasUnsavedResult
  });
}
