import { t } from "../../i18n.js";
import { element } from "../dom.js";

export const hubView = {
  bind({ onSelectMode, onChangePlayer }) {
    for (const mode of ["CAPITALS", "SURVIVAL", "TIMED"]) {
      element(`mode-${mode.toLowerCase()}`).addEventListener("click", () => onSelectMode(mode));
    }
    element("change-player").addEventListener("click", onChangePlayer);
  },

  render(playerName) {
    element("hub-title").textContent = t("Hello, {name}!", { name: playerName });
  }
};
