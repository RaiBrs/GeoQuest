let csrfPromise;

async function readResponse(response) {
  let body;
  try { body = await response.json(); }
  catch { throw new Error("The server sent an unexpected response. Please try again shortly."); }
  if (!response.ok) throw new Error(typeof body?.message === "string"
    ? body.message : "Unable to complete the operation. Please try again.");
  return body;
}

async function send(path, options) {
  let response;
  try { response = await fetch(path, { credentials: "same-origin", ...options }); }
  catch { throw new Error("Unable to connect. Check your connection and try again."); }
  return readResponse(response);
}

export function resetCsrf() { csrfPromise = undefined; }

export async function request(path, data) {
  if (data === undefined) return send(path, {});
  csrfPromise ??= send("/api/csrf", {}).catch(error => { resetCsrf(); throw error; });
  const csrf = await csrfPromise;
  if (typeof csrf?.token !== "string" || csrf.headerName !== "X-XSRF-TOKEN")
    throw new Error("The server sent an unexpected response. Please try again shortly.");
  return send(path, { method: "POST", headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token }, body: JSON.stringify(data) });
}
