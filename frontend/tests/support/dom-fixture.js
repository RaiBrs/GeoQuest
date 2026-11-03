// A minimal DOM for logic tests; real layout and focus need a browser.
export class Element {
  constructor(id = "", tagName = "DIV") {
    Object.assign(this, { id, tagName, children: [], handlers: {}, dataset: {}, attributes: {},
      hidden: false, disabled: false, value: "", className: "" });
    this.classList = { contains: name => this.className.split(" ").includes(name) };
  }
  get textContent() { return this._text || this.children.map(child => child.textContent).join(""); }
  set textContent(value) { this._text = String(value); this.children = []; }
  set innerHTML(value) { throw new Error("Render player and API content as text, never HTML"); }
  get lastElementChild() { return this.children.at(-1); }
  addEventListener(name, handler) { this.handlers[name] = handler; }
  append(...children) { this.children.push(...children); }
  replaceChildren() { this.children = []; this._text = ""; }
  focus() { document.activeElement = this; }
  setAttribute(name, value) { this.attributes[name] = value; }
  removeAttribute(name) { delete this.attributes[name]; }
  contains(node) { return this.children.includes(node); }
  querySelectorAll() { return this.children; }
  emit(name, extra = {}) {
    let prevented = false;
    this.handlers[name]?.({ preventDefault() { prevented = true; }, stopPropagation() {}, target: this, ...extra });
    return prevented;
  }
  click() { if (!this.disabled) this.emit("click"); }
}
