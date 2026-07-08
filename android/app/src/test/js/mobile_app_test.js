const assert = require("assert");
const fs = require("fs");
const path = require("path");
const vm = require("vm");

function classList() {
  const values = new Set();
  return {
    add: (name) => values.add(name),
    remove: (name) => values.delete(name),
    contains: (name) => values.has(name),
    toggle: (name, enabled) => {
      if (enabled) values.add(name);
      else values.delete(name);
    },
  };
}

function element(id) {
  return {
    id,
    value: "",
    textContent: "",
    innerHTML: "",
    disabled: false,
    dataset: {},
    files: [],
    style: {},
    classList: classList(),
    setAttribute: () => {},
    removeAttribute: () => {},
    addEventListener: () => {},
    focus: () => {},
    click: () => {},
    querySelectorAll: () => [],
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 400 }),
    clientWidth: 800,
    clientHeight: 400,
  };
}

function loadApp() {
  const elements = new Map();
  const document = {
    getElementById: (id) => {
      if (!elements.has(id)) elements.set(id, element(id));
      return elements.get(id);
    },
    querySelector: () => element("terminalKeybar"),
    querySelectorAll: () => [],
    createElement: () => element("created"),
    body: {
      appendChild: () => {},
      removeChild: () => {},
    },
    execCommand: () => true,
  };
  const bridgeCalls = [];
  const bridge = {
    initialState: () => bridgeCalls.push(["initialState"]),
    replayTerminal: (...args) => bridgeCalls.push(["replayTerminal", ...args]),
    reportViewport: (...args) => bridgeCalls.push(["reportViewport", ...args]),
    refreshWorkspaces: () => bridgeCalls.push(["refreshWorkspaces"]),
    clearViewport: (...args) => bridgeCalls.push(["clearViewport", ...args]),
  };
  const context = {
    console,
    document,
    navigator: { language: "en-US" },
    window: {
      addEventListener: () => {},
      clearTimeout: () => {},
      setTimeout: (callback) => {
        if (typeof callback === "function") callback();
        return 1;
      },
      getComputedStyle: () => ({ fontSize: "12px", lineHeight: "16px" }),
      cmuxAndroid: bridge,
    },
    globalThis: null,
    atob: (value) => Buffer.from(value, "base64").toString("binary"),
    clearTimeout: () => {},
    setTimeout: (callback) => {
      if (typeof callback === "function") callback();
      return 1;
    },
    __cmuxMobileTestHooks: {},
  };
  context.globalThis = context;
  context.window.window = context.window;
  context.window.document = document;
  context.window.navigator = context.navigator;
  context.window.__cmuxMobileTestHooks = context.__cmuxMobileTestHooks;
  context.window.setTimeout = context.setTimeout;
  context.window.clearTimeout = context.clearTimeout;
  context.window.atob = context.atob;

  const appPath = path.resolve(__dirname, "../../main/assets/mobile/app.js");
  vm.runInNewContext(fs.readFileSync(appPath, "utf8"), context, { filename: appPath });
  return { hooks: context.__cmuxMobileTestHooks, bridgeCalls };
}

function testSubscribeAckGapTriggersTerminalReplay() {
  const { hooks, bridgeCalls } = loadApp();
  hooks.state.activeWorkspace = { id: "workspace-1" };
  hooks.state.activeTerminal = { id: "terminal-1" };
  hooks.showScreen("terminal");

  hooks.handleRpcResult("mobile.events.subscribe", { already_subscribed: false });

  assert(
    bridgeCalls.some((call) => (
      call[0] === "replayTerminal" &&
      call[1] === "workspace-1" &&
      call[2] === "terminal-1"
    )),
    `expected replayTerminal call after lost subscription ack, got ${JSON.stringify(bridgeCalls)}`
  );
}

function testSubscribeAckDoesNotReplayWithoutActiveTerminal() {
  const { hooks, bridgeCalls } = loadApp();

  hooks.handleRpcResult("mobile.events.subscribe", { already_subscribed: false });

  assert(
    !bridgeCalls.some((call) => call[0] === "replayTerminal"),
    `expected no replayTerminal call without active terminal, got ${JSON.stringify(bridgeCalls)}`
  );
}

function testRenderGridPushWithoutSurfaceTargetsActiveTerminal() {
  const { hooks } = loadApp();
  hooks.state.activeWorkspace = { id: "workspace-1" };
  hooks.state.activeTerminal = { id: "terminal-1" };
  hooks.showScreen("terminal");

  hooks.handlePushEvent("terminal.render_grid", {
    rows: 1,
    columns: 12,
    row_spans: [
      { row: 0, column: 0, text: "active grid" },
    ],
  });

  assert(
    hooks.elements.terminalOutput.innerHTML.includes("active grid"),
    `expected surface-less render grid push to render active terminal, got ${hooks.elements.terminalOutput.innerHTML}`
  );
}

testSubscribeAckGapTriggersTerminalReplay();
testSubscribeAckDoesNotReplayWithoutActiveTerminal();
testRenderGridPushWithoutSurfaceTargetsActiveTerminal();
console.log("mobile app js tests passed");
