(function (send, listen) {
  "use strict";
  const stringify = JSON.stringify;
  const selection = getSelection.bind(window);
  const on = addEventListener.bind(window);
  const off = removeEventListener.bind(window);
  const soon = setTimeout.bind(window);
  const command = document.execCommand.bind(document);
  const can = document.queryCommandEnabled.bind(document);
  let waiting = null;
  let nextMenu = 1;

  function toHost(message) {
    send(stringify(message));
  }

  function pickFrom(select) {
    const menu = nextMenu++;
    waiting = { menu, select };
    toHost({
      type: "menu",
      menu,
      items: Array.from(select.options, (option) => ({
        label: option.label || option.text,
        enabled: !option.disabled,
        chosen: option.selected,
      })),
    });
  }

  function pressed(event) {
    if (!event.isTrusted) return;
    const select = event.target instanceof Element ? event.target.closest("select") : null;
    if (!select || select.multiple || select.size > 1 || select.disabled) return;
    event.preventDefault();
    select.focus();
    pickFrom(select);
  }

  // The words of a copy go to the person who made it, through the host. The browser's own copy
  // would put them on the clipboard of the computer that runs it, so the script cancels that copy.
  // A page's own handler may cancel the copy and give other words, so the words are read after
  // the handlers, as the event leaves the window. A handler that stops the event hides it: then
  // the browser copies, and the words are told after the event.
  function copied(event) {
    if (!event.isTrusted) return;
    const selected = String(selection() ?? "");
    // Outside a field, a cut that no page handler takes does nothing.
    const cuts = event.type === "cut" && can("cut");
    let told = false;
    const words = () => {
      if (event.defaultPrevented) return event.clipboardData?.getData("text/plain") ?? "";
      return event.type === "copy" || cuts ? selected : "";
    };
    const last = (leaving) => {
      if (leaving !== event || told) return;
      told = true;
      off(event.type, last);
      const text = words();
      if (!text) return;
      const own = event.defaultPrevented;
      toHost({ type: "copied", text });
      event.clipboardData?.clearData();
      event.preventDefault();
      // A cancelled cut leaves its words in the field, and a page runs no command inside a command.
      if (cuts && !own) soon(() => command("delete"), 0);
    };
    on(event.type, last);
    soon(() => {
      if (told) return;
      told = true;
      off(event.type, last);
      const text = words();
      if (text) toHost({ type: "copied", text });
    }, 0);
  }

  // A copy button calls the clipboard of the page, which makes no copy event.
  function ownClipboard() {
    if (!navigator.clipboard) return;
    const clipboard = Object.getPrototypeOf(navigator.clipboard);
    const write = clipboard.write;
    const refused = () => Promise.reject(new DOMException("Write permission denied.", "NotAllowedError"));
    const allowed = () =>
      navigator.userActivation?.isActive !== false && document.featurePolicy?.allowsFeature("clipboard-write") !== false;
    const give = (name, value) => Object.defineProperty(clipboard, name, { value, writable: true, configurable: true });
    give("writeText", function (text) {
      if (!allowed()) return refused();
      toHost({ type: "copied", text: String(text) });
      return Promise.resolve();
    });
    give("write", async function (items) {
      const plain = Array.from(items ?? []).find((item) => item?.types?.includes("text/plain"));
      if (!plain) return write.call(this, items);
      if (!allowed()) return refused();
      const text = await (await plain.getType("text/plain")).text();
      toHost({ type: "copied", text });
    });
  }

  on("mousedown", pressed, { capture: true });
  on("copy", copied, { capture: true, passive: true });
  on("cut", copied, { capture: true, passive: true });
  ownClipboard();

  listen((menu, choice) => {
    if (!waiting || waiting.menu !== menu) return;
    const { select } = waiting;
    waiting = null;
    if (choice < 0 || choice === select.selectedIndex) return;
    select.selectedIndex = choice;
    select.dispatchEvent(new Event("input", { bubbles: true }));
    select.dispatchEvent(new Event("change", { bubbles: true }));
  });
});
