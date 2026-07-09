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
  const listeners = new Map();
  const attributes = new Map();
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
    setAttribute: (name, value) => attributes.set(name, String(value)),
    removeAttribute: (name) => attributes.delete(name),
    getAttribute: (name) => attributes.get(name) || null,
    addEventListener: (type, listener) => {
      if (!listeners.has(type)) listeners.set(type, []);
      listeners.get(type).push(listener);
    },
    dispatchEvent: (type, event = {}) => {
      for (const listener of listeners.get(type) || []) {
        listener({ target: event.target || null, ...event });
      }
    },
    focus: () => {},
    click: () => {},
    querySelectorAll: () => [],
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 400 }),
    clientWidth: 800,
    clientHeight: 400,
  };
}

function eventTarget(attributes = {}, closestTarget = null) {
  return {
    getAttribute: (name) => attributes[name] || null,
    closest: (selector) => {
      const match = selector.match(/^\[([^\]]+)\]$/);
      if (!match) return null;
      if (attributes[match[1]]) return eventTarget(attributes, closestTarget);
      return closestTarget;
    },
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
    createWorkspace: () => bridgeCalls.push(["createWorkspace"]),
    createTerminal: (...args) => bridgeCalls.push(["createTerminal", ...args]),
    replayTerminal: (...args) => bridgeCalls.push(["replayTerminal", ...args]),
    reportViewport: (...args) => bridgeCalls.push(["reportViewport", ...args]),
    refreshWorkspaces: () => bridgeCalls.push(["refreshWorkspaces"]),
    clearViewport: (...args) => bridgeCalls.push(["clearViewport", ...args]),
    dismissNotifications: (...args) => bridgeCalls.push(["dismissNotifications", ...args]),
    reconcileNotifications: (...args) => bridgeCalls.push(["reconcileNotifications", ...args]),
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
  return { hooks: context.__cmuxMobileTestHooks, bridgeCalls, nativeEvent: context.window.cmuxNativeEvent };
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

function testRenderGridReplayIgnoresPreviousStateSequence() {
  const { hooks } = loadApp();
  hooks.state.activeWorkspace = { id: "workspace-1" };
  hooks.state.activeTerminal = { id: "terminal-1" };
  hooks.showScreen("terminal");

  hooks.handlePushEvent("terminal.render_grid", {
    surface_id: "terminal-1",
    state_seq: 99,
    rows: 1,
    columns: 8,
    row_spans: [
      { row: 0, column: 0, text: "old" },
    ],
  });
  hooks.handleRpcResult("mobile.terminal.replay", {
    render_grid: {
      surface_id: "terminal-1",
      state_seq: 1,
      rows: 1,
      columns: 8,
      row_spans: [
        { row: 0, column: 0, text: "new" },
      ],
    },
  });

  assert(
    hooks.elements.terminalOutput.innerHTML.includes("new"),
    `expected lower-sequence replay to replace terminal grid, got ${hooks.elements.terminalOutput.innerHTML}`
  );
}

function testRenderGridColumnResizeRebuildsRows() {
  const { hooks } = loadApp();
  hooks.state.activeWorkspace = { id: "workspace-1" };
  hooks.state.activeTerminal = { id: "terminal-1" };
  hooks.showScreen("terminal");

  hooks.handlePushEvent("terminal.render_grid", {
    surface_id: "terminal-1",
    state_seq: 1,
    rows: 5,
    columns: 30,
    row_spans: [
      { row: 0, column: 0, text: "wide" },
    ],
  });
  hooks.handlePushEvent("terminal.render_grid", {
    surface_id: "terminal-1",
    state_seq: 2,
    rows: 5,
    columns: 20,
    full: false,
    row_spans: [],
  });

  assert.strictEqual(
    hooks.elements.terminalOutput.dataset.columns,
    "20",
    `expected terminal columns metadata to shrink, got ${hooks.elements.terminalOutput.dataset.columns}`
  );
  const firstLineText = hooks.elements.terminalOutput.innerHTML.match(/<span class="terminal-cell">([^<]*)<\/span>/)?.[1] || "";
  assert.strictEqual(
    firstLineText.length,
    20,
    `expected rendered row to shrink to 20 cells, got ${firstLineText.length}: ${JSON.stringify(firstLineText)}`
  );
}

function testRenderGridInverseStyleSwapsForegroundAndBackground() {
  const { hooks } = loadApp();
  hooks.state.activeWorkspace = { id: "workspace-1" };
  hooks.state.activeTerminal = { id: "terminal-1" };
  hooks.showScreen("terminal");

  hooks.handlePushEvent("terminal.render_grid", {
    surface_id: "terminal-1",
    rows: 1,
    columns: 4,
    styles: [{
      id: 1,
      foreground: "#112233",
      background: "#445566",
      inverse: true,
    }],
    row_spans: [
      { row: 0, column: 0, text: "X", style_id: 1 },
    ],
  });

  assert(
    hooks.elements.terminalOutput.innerHTML.includes("color:#445566"),
    `expected inverse foreground to use original background, got ${hooks.elements.terminalOutput.innerHTML}`
  );
  assert(
    hooks.elements.terminalOutput.innerHTML.includes("background-color:#112233"),
    `expected inverse background to use original foreground, got ${hooks.elements.terminalOutput.innerHTML}`
  );
}

function testTerminalBytesGapRequestsReplay() {
  const { hooks, bridgeCalls } = loadApp();
  hooks.state.activeWorkspace = { id: "workspace-1" };
  hooks.state.activeTerminal = { id: "terminal-1" };
  hooks.showScreen("terminal");

  hooks.handlePushEvent("terminal.bytes", {
    surface_id: "terminal-1",
    seq: 0,
    data_b64: Buffer.from("abc").toString("base64"),
  });
  hooks.handlePushEvent("terminal.bytes", {
    surface_id: "terminal-1",
    seq: 5,
    data_b64: Buffer.from("fg").toString("base64"),
  });

  assert(
    bridgeCalls.some((call) => (
      call[0] === "replayTerminal" &&
      call[1] === "workspace-1" &&
      call[2] === "terminal-1"
    )),
    `expected replayTerminal call after terminal byte gap, got ${JSON.stringify(bridgeCalls)}`
  );
}

function testTerminalReplayResetsByteDeduplication() {
  const { hooks } = loadApp();
  hooks.state.activeWorkspace = { id: "workspace-1" };
  hooks.state.activeTerminal = { id: "terminal-1" };
  hooks.showScreen("terminal");

  hooks.handlePushEvent("terminal.bytes", {
    surface_id: "terminal-1",
    seq: 0,
    data_b64: Buffer.from("old").toString("base64"),
  });
  hooks.handleRpcResult("mobile.terminal.replay", {
    data_b64: Buffer.from("snapshot\n").toString("base64"),
  });
  hooks.handlePushEvent("terminal.bytes", {
    surface_id: "terminal-1",
    seq: 0,
    data_b64: Buffer.from("new").toString("base64"),
  });

  assert.strictEqual(
    hooks.elements.terminalOutput.textContent,
    "snapshot\nnew",
    `expected bytes after replay to append, got ${JSON.stringify(hooks.elements.terminalOutput.textContent)}`
  );
}

function testNestedTerminalOpenClickUsesClosestButton() {
  const { hooks, bridgeCalls } = loadApp();
  hooks.state.connected = true;
  hooks.state.workspaces = [{
    id: "workspace-1",
    title: "Workspace",
    terminals: [{ id: "terminal-1", title: "Terminal" }],
  }];
  const button = eventTarget({
    "data-open-terminal": "workspace-1",
    "data-terminal-id": "terminal-1",
  });
  const child = eventTarget({}, button);

  hooks.elements.workspaceList.dispatchEvent("click", { target: child });

  assert(
    bridgeCalls.some((call) => (
      call[0] === "replayTerminal" &&
      call[1] === "workspace-1" &&
      call[2] === "terminal-1"
    )),
    `expected nested terminal button click to open and replay terminal, got ${JSON.stringify(bridgeCalls)}`
  );
}

function testWorkspaceFilterIgnoresNonElementClickTarget() {
  const { hooks } = loadApp();
  hooks.state.workspaceFilter = "all";

  assert.doesNotThrow(() => {
    hooks.elements.workspaceFilters.dispatchEvent("click", { target: {} });
  });
  assert.strictEqual(
    hooks.state.workspaceFilter,
    "all",
    `expected non-element filter click target to be ignored, got ${hooks.state.workspaceFilter}`
  );
}

function testWorkspaceGroupsRenderBeforeHostStatusCapabilities() {
  const { hooks } = loadApp();
  hooks.state.connected = true;

  hooks.handleRpcResult("mobile.workspace.list", {
    groups: [{ id: "group-1", name: "Builds", is_collapsed: false }],
    workspaces: [{
      id: "workspace-1",
      title: "Android",
      group_id: "group-1",
      terminals: [],
    }],
  });

  assert(
    hooks.elements.workspaceList.innerHTML.includes("Builds"),
    `expected group title from workspace payload to render before host status, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    hooks.elements.workspaceList.innerHTML.includes("grouped-workspace"),
    `expected grouped workspace styling before host status, got ${hooks.elements.workspaceList.innerHTML}`
  );
}

function testNestedHostServiceCapabilitiesEnableWorkspaceControls() {
  const { hooks, bridgeCalls } = loadApp();
  hooks.state.connected = true;

  hooks.handleRpcResult("mobile.host.status", {
    capabilities: [],
    host_service: {
      capabilities: ["workspace.create.v1"],
    },
  });
  hooks.elements.createWorkspace.dispatchEvent("click", {
    target: hooks.elements.createWorkspace,
  });

  assert(
    bridgeCalls.some((call) => call[0] === "createWorkspace"),
    `expected nested host_service capabilities to enable createWorkspace, got ${JSON.stringify(bridgeCalls)}`
  );
}

function testCreateWorkspaceButtonRequiresHostCapability() {
  const { hooks, nativeEvent } = loadApp();

  hooks.handleRpcResult("mobile.host.status", {
    capabilities: [],
  });
  nativeEvent({
    type: "connection",
    payload: { state: "open" },
  });

  assert.strictEqual(
    hooks.elements.createWorkspace.disabled,
    true,
    "expected create workspace button to stay disabled without workspace.create.v1"
  );

  hooks.handleRpcResult("mobile.host.status", {
    capabilities: ["workspace.create.v1"],
  });
  nativeEvent({
    type: "connection",
    payload: { state: "open" },
  });

  assert.strictEqual(
    hooks.elements.createWorkspace.disabled,
    false,
    "expected create workspace button to enable when workspace.create.v1 is available"
  );
}

function testWorkspaceCardActionsRequireHostCapabilities() {
  const { hooks } = loadApp();
  hooks.state.connected = true;

  hooks.handleRpcResult("mobile.host.status", {
    capabilities: [],
  });
  hooks.handleRpcResult("mobile.workspace.list", {
    workspaces: [{
      id: "workspace-1",
      title: "Android",
      has_unread: true,
      terminals: [],
    }],
    groups: [],
  });

  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-create-terminal="workspace-1" disabled'),
    `expected terminal create button disabled without terminal.create.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-rename-workspace="workspace-1" disabled'),
    `expected rename button disabled without workspace.actions.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-pin-workspace="workspace-1" data-pinned="false" disabled'),
    `expected pin button disabled without workspace.actions.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-read-workspace="workspace-1" data-unread="true" disabled'),
    `expected read-state button disabled without workspace.read_state.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-close-workspace="workspace-1" disabled'),
    `expected close button disabled without workspace.close.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );

  hooks.handleRpcResult("mobile.host.status", {
    capabilities: [
      "terminal.create.v1",
      "workspace.actions.v1",
      "workspace.read_state.v1",
      "workspace.close.v1",
    ],
  });
  hooks.handleRpcResult("mobile.workspace.list", {
    workspaces: [{
      id: "workspace-1",
      title: "Android",
      has_unread: true,
      terminals: [],
    }],
    groups: [],
  });

  assert(
    !hooks.elements.workspaceList.innerHTML.includes('data-create-terminal="workspace-1" disabled'),
    `expected terminal create button enabled with terminal.create.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    !hooks.elements.workspaceList.innerHTML.includes('data-rename-workspace="workspace-1" disabled'),
    `expected rename button enabled with workspace.actions.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    !hooks.elements.workspaceList.innerHTML.includes('data-pin-workspace="workspace-1" data-pinned="false" disabled'),
    `expected pin button enabled with workspace.actions.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    !hooks.elements.workspaceList.innerHTML.includes('data-read-workspace="workspace-1" data-unread="true" disabled'),
    `expected read-state button enabled with workspace.read_state.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    !hooks.elements.workspaceList.innerHTML.includes('data-close-workspace="workspace-1" disabled'),
    `expected close button enabled with workspace.close.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
}

function testWorkspaceGroupToggleRequiresHostCapability() {
  const { hooks } = loadApp();
  hooks.state.connected = true;

  hooks.handleRpcResult("mobile.host.status", {
    capabilities: [],
  });
  hooks.handleRpcResult("mobile.workspace.list", {
    groups: [{ id: "group-1", name: "Builds", is_collapsed: false }],
    workspaces: [{
      id: "workspace-1",
      title: "Android",
      group_id: "group-1",
      terminals: [],
    }],
  });

  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-toggle-group="group-1" data-collapsed="false" disabled'),
    `expected group toggle disabled without workspace.groups.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );

  hooks.handleRpcResult("mobile.host.status", {
    capabilities: ["workspace.groups.v1"],
  });
  hooks.handleRpcResult("mobile.workspace.list", {
    groups: [{ id: "group-1", name: "Builds", is_collapsed: false }],
    workspaces: [{
      id: "workspace-1",
      title: "Android",
      group_id: "group-1",
      terminals: [],
    }],
  });

  assert(
    !hooks.elements.workspaceList.innerHTML.includes('data-toggle-group="group-1" data-collapsed="false" disabled'),
    `expected group toggle enabled with workspace.groups.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
}

function testWorkspaceWithTerminalsStillOffersCreateTerminal() {
  const { hooks } = loadApp();
  hooks.state.connected = true;
  hooks.handleRpcResult("mobile.host.status", {
    capabilities: ["terminal.create.v1"],
  });

  hooks.handleRpcResult("mobile.workspace.list", {
    workspaces: [{
      id: "workspace-1",
      title: "Android",
      terminals: [{ id: "terminal-1", title: "Shell" }],
    }],
    groups: [],
  });

  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-open-terminal="workspace-1" data-terminal-id="terminal-1"'),
    `expected existing terminal open button, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    hooks.elements.workspaceList.innerHTML.includes('data-create-terminal="workspace-1"'),
    `expected create terminal button even when workspace has terminals, got ${hooks.elements.workspaceList.innerHTML}`
  );
  assert(
    !hooks.elements.workspaceList.innerHTML.includes('data-create-terminal="workspace-1" disabled'),
    `expected create terminal button enabled with terminal.create.v1, got ${hooks.elements.workspaceList.innerHTML}`
  );
}

function testCreateTerminalClickRequestsWorkspaceTerminal() {
  const { hooks, bridgeCalls } = loadApp();
  hooks.state.connected = true;
  hooks.handleRpcResult("mobile.host.status", {
    capabilities: ["terminal.create.v1"],
  });
  hooks.handleRpcResult("mobile.workspace.list", {
    workspaces: [{
      id: "workspace-1",
      title: "Android",
      terminals: [{ id: "terminal-1", title: "Shell" }],
    }],
    groups: [],
  });

  const button = eventTarget({ "data-create-terminal": "workspace-1" });
  hooks.elements.workspaceList.dispatchEvent("click", { target: button });

  assert(
    bridgeCalls.some((call) => (
      call[0] === "createTerminal" &&
      call[1] === "workspace-1"
    )),
    `expected createTerminal call for workspace-1, got ${JSON.stringify(bridgeCalls)}`
  );
}

function testNotificationBadgeRecordsDeliveredIdsForDismissal() {
  const { hooks, bridgeCalls } = loadApp();
  hooks.state.connected = true;
  hooks.state.hostStatus = {
    capabilities: ["notification.dismiss.v1"],
  };

  hooks.handlePushEvent("notification.badge", {
    unread_count: 2,
    notification_ids: ["n-1", "n-2", "n-1"],
  });

  assert.deepStrictEqual(
    Array.from(hooks.state.deliveredNotificationIds),
    ["n-1", "n-2"],
    `expected badge notification ids to be tracked, got ${JSON.stringify(hooks.state.deliveredNotificationIds)}`
  );
  assert.strictEqual(
    hooks.elements.dismissNotifications.disabled,
    false,
    "expected dismiss button to become enabled after delivered ids arrive"
  );

  hooks.elements.dismissNotifications.dispatchEvent("click", {
    target: hooks.elements.dismissNotifications,
  });

  assert(
    bridgeCalls.some((call) => (
      call[0] === "dismissNotifications" &&
      call[1] === JSON.stringify(["n-1", "n-2"])
    )),
    `expected dismissNotifications with delivered ids, got ${JSON.stringify(bridgeCalls)}`
  );
}

function testNotificationBadgeZeroClearsDeliveredIds() {
  const { hooks } = loadApp();
  hooks.state.connected = true;
  hooks.state.hostStatus = {
    capabilities: ["notification.dismiss.v1"],
  };

  hooks.handlePushEvent("notification.badge", {
    unread_count: 2,
    notification_ids: ["n-1", "n-2"],
  });
  hooks.handlePushEvent("notification.badge", {
    unread_count: 0,
  });

  assert.deepStrictEqual(
    Array.from(hooks.state.deliveredNotificationIds),
    [],
    `expected zero badge to clear delivered ids, got ${JSON.stringify(hooks.state.deliveredNotificationIds)}`
  );
  assert.strictEqual(
    hooks.elements.dismissNotifications.disabled,
    true,
    "expected dismiss button to be disabled after unread badge returns to zero"
  );
}

function testNotificationDismissedZeroClearsDeliveredIds() {
  const { hooks } = loadApp();
  hooks.state.connected = true;
  hooks.state.hostStatus = {
    capabilities: ["notification.dismiss.v1"],
  };

  hooks.handlePushEvent("notification.badge", {
    unread_count: 3,
    notification_ids: ["n-1", "n-2", "n-3"],
  });
  hooks.handlePushEvent("notification.dismissed", {
    handled_ids: ["n-1"],
    unread_count: 0,
  });

  assert.deepStrictEqual(
    Array.from(hooks.state.deliveredNotificationIds),
    [],
    `expected dismissed zero unread to clear delivered ids, got ${JSON.stringify(hooks.state.deliveredNotificationIds)}`
  );
  assert.strictEqual(
    hooks.elements.dismissNotifications.disabled,
    true,
    "expected dismiss button to be disabled after dismissed push returns unread to zero"
  );
}

function testNotificationReconcileZeroClearsDeliveredIds() {
  const { hooks } = loadApp();
  hooks.state.connected = true;
  hooks.state.hostStatus = {
    capabilities: ["notification.reconcile.v1", "notification.dismiss.v1"],
  };

  hooks.handlePushEvent("notification.badge", {
    unread_count: 3,
    notification_ids: ["n-1", "n-2", "n-3"],
  });
  hooks.handleRpcResult("notification.reconcile", {
    handled_ids: ["n-1"],
    unread_count: 0,
  });

  assert.deepStrictEqual(
    Array.from(hooks.state.deliveredNotificationIds),
    [],
    `expected reconcile zero unread to clear delivered ids, got ${JSON.stringify(hooks.state.deliveredNotificationIds)}`
  );
  assert.strictEqual(
    hooks.elements.dismissNotifications.disabled,
    true,
    "expected dismiss button to be disabled after reconcile returns unread zero"
  );
}

testSubscribeAckGapTriggersTerminalReplay();
testSubscribeAckDoesNotReplayWithoutActiveTerminal();
testRenderGridPushWithoutSurfaceTargetsActiveTerminal();
testRenderGridReplayIgnoresPreviousStateSequence();
testRenderGridColumnResizeRebuildsRows();
testRenderGridInverseStyleSwapsForegroundAndBackground();
testTerminalBytesGapRequestsReplay();
testTerminalReplayResetsByteDeduplication();
testNestedTerminalOpenClickUsesClosestButton();
testWorkspaceFilterIgnoresNonElementClickTarget();
testWorkspaceGroupsRenderBeforeHostStatusCapabilities();
testNestedHostServiceCapabilitiesEnableWorkspaceControls();
testCreateWorkspaceButtonRequiresHostCapability();
testWorkspaceCardActionsRequireHostCapabilities();
testWorkspaceGroupToggleRequiresHostCapability();
testWorkspaceWithTerminalsStillOffersCreateTerminal();
testCreateTerminalClickRequestsWorkspaceTerminal();
testNotificationBadgeRecordsDeliveredIdsForDismissal();
testNotificationBadgeZeroClearsDeliveredIds();
testNotificationDismissedZeroClearsDeliveredIds();
testNotificationReconcileZeroClearsDeliveredIds();
console.log("mobile app js tests passed");
