import { initializeGameController } from "./controller/game-controller.js";
import { initializeAccounts } from "./controller/account-controller.js";

// Controllers share availability, but each keeps its own state.
let accounts;
const game = initializeGameController({ onStateChange: state => accounts?.updateAvailability(state) });
accounts = initializeAccounts(game);
