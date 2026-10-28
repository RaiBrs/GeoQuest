import { request } from "./http-client.js";

export const gameService = {
  start(mode, questionType, language) { return request("/api/games", mode ? { mode, questionType, language } : {}); },
  status(gameId) { return request("/api/games/status", { gameId }); },
  answer(answer) { return request("/api/answers", answer); }
};
