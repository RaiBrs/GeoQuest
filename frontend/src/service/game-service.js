async function request(path, data) {
  let response;
  try {
    response = await fetch(`/api/${path}`, {
      method: data === undefined ? "GET" : "POST",
      headers: data === undefined ? {} : { "Content-Type": "application/json" },
      body: data === undefined ? undefined : JSON.stringify(data)
    });
  } catch {
    throw new Error("Unable to connect. Check your connection and try again.");
  }
  let body;
  try {
    body = await response.json();
  } catch {
    throw new Error("The server sent an unexpected response. Please try again shortly.");
  }
  if (!response.ok) {
    throw new Error(typeof body?.message === "string"
      ? body.message : "Unable to complete the operation. Please try again.");
  }
  return body;
}

export const gameService = {
  start(playerName) { return request("games", { playerName }); },
  answer(answer) { return request("answers", answer); },
  saveResult(gameId) { return request("results", { gameId }); },
  getRanking() { return request("ranking"); }
};
