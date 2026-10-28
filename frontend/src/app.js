import { initializeGameController } from "./controller/game-controller.js";
import { initializeAccounts } from "./controller/account-controller.js";

let accounts;
const game = initializeGameController({ onStateChange: state => accounts?.updateAvailability(state) });
accounts = initializeAccounts(game);
