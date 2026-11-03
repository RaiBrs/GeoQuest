export const element = id => document.getElementById(id);

export function span(className, text) {
  const node = document.createElement("span");
  node.className = className;
  // Names and external data are text and must not be interpreted as HTML.
  node.textContent = text;
  return node;
}
