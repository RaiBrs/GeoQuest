async function request(path, data) {
  let response;
  try {
    response = await fetch(`/api/${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data)
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
  start(mode) { return request("games", mode ? { mode } : {}); },
  status(gameId) { return request("games/status", { gameId }); },
  answer(answer) { return request("answers", answer); }
};
