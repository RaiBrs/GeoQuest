async function request(path, data) {
  const response = await fetch(`/api/${path}`, {
    method: data === undefined ? "GET" : "POST",
    headers: data === undefined ? {} : { "Content-Type": "application/json" },
    body: data === undefined ? undefined : JSON.stringify(data)
  });
  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.message || "Não foi possível concluir a operação.");
  }
  return body;
}

export const gameService = {
  start(playerName) { return request("games", { playerName }); },
  answer(answer) { return request("answers", answer); },
  saveResult(gameId) { return request("results", { gameId }); },
  getRanking() { return request("ranking"); }
};
