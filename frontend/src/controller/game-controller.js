import { getLanguage, setLanguage, t } from "../i18n.js";
import { element } from "../view/dom.js";
import { gameService } from "../service/game-service.js";
import { gameView } from "../view/pages/game-view.js";
import { resultView } from "../view/pages/result-view.js";
import { welcomeView } from "../view/pages/welcome-view.js";
import { shellView } from "../view/shell-view.js";
import { hubView } from "../view/pages/hub-view.js";

export function initializeGameController({ onStateChange = () => {} } = {}) {
  let screen = "login";
  let entered = false;
  let accountName;
  let state;
  let answer;
  let pendingAnswer;
  let busy = false;
  let accountBusy = false;
  let retryTask;
  let selectedMode = "SURVIVAL";
  let timer;
  let selectedType = "CAPITAL";

  function playerName() { return accountName || t("Guest"); }

  function stopTimer() { clearInterval(timer); timer = undefined; }

  function leaveRound() {
    stopTimer();
    state = answer = pendingAnswer = undefined;
  }

  function showHub() {
    leaveRound();
    hubView.render(playerName());
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
    resultView.renderSummary(state, playerName());
    showScreen("result");
    focusCurrentStep();
  }

  function showScreen(next) {
    screen = next;
    shellView.showScreen(next);
    element("language").disabled = next === "game" || next === "result";
  }

  function clearError() {
    shellView.clearError();
    retryTask = undefined;
  }

  function updateControls() {
    shellView.updateControls(busy || accountBusy, answer != null || pendingAnswer != null);
    element("language").disabled = busy || accountBusy || screen === "game" || screen === "result";
    element("question-type").disabled = busy || accountBusy;
    element("change-player").hidden = Boolean(accountName);
    element("resume-player").hidden = !entered;
    onStateChange({ screen, busy: busy || accountBusy });
  }

  function focusCurrentStep() { shellView.focusCurrentStep(screen, answer != null); }

  async function run(action, operation) {
    if (busy || accountBusy) return;
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

  async function startGame() {
    const nextState = await gameService.start(selectedMode, selectedType, getLanguage());
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
    gameView.renderQuestion(state, playerName(), chooseAnswer);
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
    gameView.renderAnswer(answer, submission.choice, playerName());
  }

  function navigate(action) {
    if (busy || accountBusy) return;
    clearError();
    action();
  }

  function resetPlayer() {
    leaveRound();
    entered = false;
    showScreen("login");
    updateControls();
    welcomeView.focus();
  }

  function continueRound() {
    if (busy || !answer) return;
    clearError();
    if (!state.finished) { renderQuestion(); focusCurrentStep(); return; }
    showResult();
  }

  function canUseShortcuts() { return screen === "game" && !busy && !accountBusy; }

  function restartRound() {
    if (busy || !state?.finished) return;
    run(startGame, "start");
  }

  function showHome() {
    leaveRound();
    showScreen("welcome");
    updateControls();
    focusCurrentStep();
  }

  welcomeView.bind({ onContinue() {
    if (busy || accountBusy || entered) return;
    entered = true;
    showHome();
  } });
  gameView.bind({
    onNext: continueRound,
    onRestart: restartRound,
    canRestart: () => canUseShortcuts() && state?.finished,
    canContinue: () => canUseShortcuts() && answer != null && !shellView.hasError(),
    canAnswer: () => canUseShortcuts() && !answer && !pendingAnswer
  });
  resultView.bind({
    onRestart: restartRound,
    onBackToHome: () => navigate(() => entered ? showHub() : resetPlayer())
  });
  shellView.bind({
    onHome: () => navigate(() => entered ? showHome() : resetPlayer()),
    onNavigate: next => navigate(() => {
      if (next === "login" || !entered) { resetPlayer(); return; }
      if (next === "hub") { showHub(); return; }
      leaveRound();
      showScreen(next);
      updateControls();
      focusCurrentStep();
    }),
    onRetry: () => { if (retryTask) run(retryTask.action, retryTask.operation); }
  });
  element("language").addEventListener("change", () => {
    if (busy || screen === "game" || screen === "result") return;
    setLanguage(element("language").value);
    shellView.showScreen(screen);
    if (screen === "hub") hubView.render(playerName());
    clearError();
    updateControls();
  });
  setLanguage("EN");
  hubView.bind({
    onSelectMode(mode) {
      if (busy || accountBusy || !entered) return;
      selectedMode = mode;
      selectedType = element("question-type").value || "CAPITAL";
      run(startGame, "start");
    },
    onChangePlayer: () => navigate(resetPlayer)
  });
  return {
    useAccount(name) {
      accountName = name;
      entered = true;
      if (["login", "welcome", "hub"].includes(screen)) showHome();
    },
    clearAccount() { accountName = undefined; resetPlayer(); },
    setAccountBusy(value) {
      accountBusy = value;
      updateControls();
      if (!value) focusCurrentStep();
    }
  };
}
