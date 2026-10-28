import { t } from "../../i18n.js";
import { element } from "../dom.js";

function clearValidation() {
  element("player-name").setCustomValidity("");
  element("player-name").setAttribute("aria-invalid", "false");
}

export const welcomeView = {
  bind({ onSubmit, onInput }) {
    element("player-form").addEventListener("submit", event => {
      event.preventDefault();
      onSubmit();
    });
    element("player-name").addEventListener("input", () => {
      clearValidation();
      onInput();
    });
  },

  readPlayerName() {
    const input = element("player-name");
    const name = input.value.trim();
    if (!name || [...name].length > 80 || /\p{Cc}/u.test(input.value)) {
      input.setCustomValidity(t("Enter a name of up to 80 characters, without control characters."));
      input.setAttribute("aria-invalid", "true");
      input.reportValidity();
      return undefined;
    }
    return name;
  },

  reset() {
    element("player-form").reset();
    clearValidation();
  },

  focus() { element("player-name").focus(); }
};
