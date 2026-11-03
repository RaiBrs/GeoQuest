import { t } from "../../i18n.js";
import { element } from "../dom.js";

export const hubView = {
  updateControls(busy, accountName) {
    for (const id of ["mode-capitals", "mode-survival", "mode-timed", "change-player", "question-type"])
      element(id).disabled = busy;
    element("change-player").hidden = Boolean(accountName);
  },

  bind({ onSelectMode, onChangePlayer }) {
    for (const mode of ["CAPITALS", "SURVIVAL", "TIMED"]) {
      element(`mode-${mode.toLowerCase()}`).addEventListener("click", () => onSelectMode(mode));
    }
    element("change-player").addEventListener("click", onChangePlayer);
  },

  render(playerName) {
    element("hub-greeting").textContent = t("Hello, {name}!", { name: playerName });
  }
};
