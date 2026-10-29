import { element } from "../dom.js";

export const welcomeView = {
  bind({ onContinue }) { element("continue-guest").addEventListener("click", onContinue); },
  focus() { element("continue-guest").focus(); }
};
