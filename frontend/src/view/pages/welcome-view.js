import { element } from "../dom.js";

export const welcomeView = {
  updateControls(busy) { element("continue-guest").disabled = busy; },
  bind({ onContinue }) { element("continue-guest").addEventListener("click", onContinue); },
  focus() { element("continue-guest").focus(); }
};
