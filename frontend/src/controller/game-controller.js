import { getLanguage, initializeLanguage, setLanguage, t } from "../i18n.js";
import { element } from "../view/dom.js";
import { gameService } from "../service/game-service.js";
import { gameView } from "../view/pages/game-view.js";
import { resultView } from "../view/pages/result-view.js";
import { welcomeView } from "../view/pages/welcome-view.js";
import { shellView } from "../view/shell-view.js";
import { hubView } from "../view/pages/hub-view.js";

const TIMED_FEEDBACK_MS = 700;

export function initializeGameController({ onStateChange = () => {} } = {}) {
  let screen = "login";
  let entered = false;
  let accountName;
  let state;
  // state may already hold the next question while answer keeps the previous feedback.
  let answer;
  let pendingAnswer;
  let busy = false;
  let accountBusy = false;
  let retryTask;
  let selectedMode = "SURVIVAL";
  let timer;
  let autoAdvance;
  let selectedType = "CAPITAL";

  function playerName() { return accountName || t("Guest"); }
  function isBusy() { return busy || accountBusy; }

  function stopTimer() { clearInterval(timer); timer = undefined; }
  function stopAutoAdvance() { clearTimeout(autoAdvance); autoAdvance = undefined; }

  // Leaving or reaching the deadline cancels both the countdown and automatic advance.
  function stopRoundTimers() { stopTimer(); stopAutoAdvance(); }

  function leaveRound() {
    stopRoundTimers();
    state = answer = pendingAnswer = undefined;
  }

  function showSection(next) {
    // Navigation clears the local round; the server round follows its own expiry policy.
    leaveRound();
    if (next === "hub") hubView.render(playerName());
    showScreen(next);
  }

  function updateTimer() {
    if (!state?.expiresAt || screen !== "game") return;
    const seconds = Math.max(0, Math.ceil((state.expiresAt - Date.now()) / 1000));
    gameView.renderTimer(seconds);
    if (seconds === 0 && !isBusy() && !shellView.hasError()) {
      stopRoundTimers();
      // The browser clock shows the deadline; the server confirms when the round ends.
      run(async () => {
        state = await gameService.status(state.gameId);
        if (state.finished) showResult();
        else timer = setInterval(updateTimer, 250);
      }, "answer");
    }
  }

  function showResult() {
    stopRoundTimers();
    resultView.renderSummary(state, playerName());
    showScreen("result");
  }

  function showScreen(next, focus = true) {
    screen = next;
    shellView.showScreen(next);
    updateControls();
    if (focus) focusCurrentStep();
  }

  function clearError() {
    shellView.clearError();
    retryTask = undefined;
  }

  function updateControls() {
    const locked = isBusy();
    shellView.updateControls(locked);
    gameView.updateControls(locked, answer != null || pendingAnswer != null);
    hubView.updateControls(locked, accountName);
    welcomeView.updateControls(locked);
    resultView.updateControls(locked);
    element("language").disabled = locked || screen === "game" || screen === "result";
    element("resume-player").hidden = !entered;
    onStateChange({ screen, busy: locked });
  }

  function focusCurrentStep() { shellView.focusCurrentStep(screen, answer != null); }

  async function run(action, operation) {
    if (isBusy()) return;
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
      // Enable buttons before restoring focus; disabled buttons cannot receive it.
      focusCurrentStep();
    }
  }

  async function startGame() {
    const nextState = await gameService.start(selectedMode, selectedType, getLanguage());
    state = nextState;
    pendingAnswer = undefined;
    // Render the new question before run() restores focus.
    showScreen("game", false);
    renderQuestion();
    stopTimer();
    gameView.renderMode(selectedMode, state.expiresAt != null);
    if (state.expiresAt) { timer = setInterval(updateTimer, 250); updateTimer(); }
  }

  function renderQuestion() {
    stopAutoAdvance();
    answer = undefined;
    gameView.renderQuestion(state, playerName(), chooseAnswer);
    updateControls();
  }

  function chooseAnswer(choice) {
    if (isBusy() || pendingAnswer || answer) return;
    const submission = { gameId: state.gameId, number: state.number, choice };
    pendingAnswer = submission;
    // The server may save an answer before a network failure; retry sends the same submission.
    run(() => submitAnswer(submission), "answer");
  }

  async function submitAnswer(submission) {
    answer = await gameService.answer(submission);
    state = answer.state;
    pendingAnswer = undefined;
    if (state.timedOut) { showResult(); return; }
    if (state.finished) stopTimer();
    gameView.renderAnswer(answer, submission.choice, playerName());
    if (state.mode === "TIMED") autoAdvance = setTimeout(continueRound, TIMED_FEEDBACK_MS);
  }

  function navigate(action) {
    if (isBusy()) return;
    clearError();
    action();
  }

  function resetPlayer() {
    leaveRound();
    entered = false;
    showScreen("login", false);
    welcomeView.focus();
  }

  function continueRound() {
    if (screen !== "game" || isBusy() || !answer) return;
    stopAutoAdvance();
    clearError();
    if (state.mode === "TIMED" && Date.now() >= state.expiresAt) { updateTimer(); return; }
    if (!state.finished) { renderQuestion(); focusCurrentStep(); return; }
    showResult();
  }

  function canUseShortcuts() { return screen === "game" && !isBusy(); }

  function restartRound() {
    if (isBusy() || !state?.finished) return;
    run(startGame, "start");
  }

  welcomeView.bind({ onContinue() {
    if (isBusy() || entered) return;
    entered = true;
    showSection("welcome");
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
    onBackToHome: () => navigate(() => entered ? showSection("hub") : resetPlayer())
  });
  shellView.bind({
    onHome: () => navigate(() => entered ? showSection("welcome") : resetPlayer()),
    onNavigate: next => navigate(() => {
      if (next === "login" || !entered) { resetPlayer(); return; }
      showSection(next);
    }),
    onRetry: () => { if (retryTask) run(retryTask.action, retryTask.operation); }
  });
  element("language").addEventListener("change", () => {
    if (isBusy() || screen === "game" || screen === "result") return;
    setLanguage(element("language").value);
    shellView.showScreen(screen);
    if (screen === "hub") hubView.render(playerName());
    clearError();
    updateControls();
  });
  initializeLanguage();
  hubView.bind({
    onSelectMode(mode) {
      if (isBusy() || !entered) return;
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
      if (["login", "welcome", "hub"].includes(screen)) showSection("welcome");
    },
    clearAccount() { accountName = undefined; resetPlayer(); },
    setAccountBusy(value) {
      accountBusy = value;
      updateControls();
      if (!value) focusCurrentStep();
    }
  };
}
