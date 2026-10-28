export const element = id => document.getElementById(id);

export function span(className, text) {
  const node = document.createElement("span");
  node.className = className;
  // Names and API content are text, never HTML.
  node.textContent = text;
  return node;
}
