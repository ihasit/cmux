const state = {
  macs: [],
  connected: false,
  hostStatus: null,
  workspaces: [],
  activeWorkspace: null,
  activeTerminal: null,
};

const messages = {
  en: {
    "app.disconnected": "Disconnected",
    "app.connected": "Connected.",
    "disconnect": "Disconnect",
    "pair.title": "Pair a Mac",
    "pair.subtitle": "Paste a cmux pairing link or enter the route shown on your Mac.",
    "pair.link": "Pairing link",
    "pair.placeholder": "cmux-ios://attach?v=2&r=100.64.0.5:58465",
    "pair.connect": "Pair and connect",
    "pair.error.empty": "Pairing code is empty.",
    "pair.error.scheme": "Pairing code must start with cmux-ios:// or cmux-ios-dev://.",
    "pair.error.host": "Pairing code must be an attach or pair URL.",
    "pair.error.unsupported": "Unsupported pairing code.",
    "pair.error.noRoutes": "Pairing code has no routes.",
    "pair.error.tooManyRoutes": "Pairing code has too many routes.",
    "pair.error.loopback": "Pairing code points at a loopback address.",
    "pair.error.missingPayload": "Pairing payload is missing.",
    "pair.error.invalidRoute": "Pairing code has an invalid host or port.",
    "pair.error.failed": "Pairing failed.",
    "paired.defaultTitle": "Paired Mac",
    "paired.noRoute": "No supported route",
    "paired.notFound": "Paired Mac was not found.",
    "workspaces.title": "Workspaces",
    "workspaces.empty": "No workspaces reported yet.",
    "workspace.defaultTitle": "Workspace",
    "terminal.defaultTitle": "Terminal",
    "terminal.notFound": "Terminal was not found.",
    "terminal.noTerminals": "No terminals",
    "terminal.new": "New",
    "terminal.open": "Open",
    "terminal.loading": "Loading terminal replay...",
    "terminal.empty": "(terminal is empty)",
    "terminal.inputPlaceholder": "Send input to the terminal",
    "host.connected": "Connected",
    "host.defaultName": "Connected Mac",
    "request.failed": "Request failed.",
    "refresh": "Refresh",
    "back": "Back",
    "replay": "Replay",
    "paste": "Paste",
    "send": "Send",
    "forget": "Forget",
    "connect": "Connect",
  },
  ja: {
    "app.disconnected": "未接続",
    "app.connected": "接続しました。",
    "disconnect": "切断",
    "pair.title": "Mac をペアリング",
    "pair.subtitle": "cmux のペアリングリンクを貼り付けるか、Mac に表示された経路を入力します。",
    "pair.link": "ペアリングリンク",
    "pair.placeholder": "cmux-ios://attach?v=2&r=100.64.0.5:58465",
    "pair.connect": "ペアリングして接続",
    "pair.error.empty": "ペアリングコードが空です。",
    "pair.error.scheme": "ペアリングコードは cmux-ios:// または cmux-ios-dev:// で始まる必要があります。",
    "pair.error.host": "ペアリングコードは attach または pair URL である必要があります。",
    "pair.error.unsupported": "対応していないペアリングコードです。",
    "pair.error.noRoutes": "ペアリングコードに経路がありません。",
    "pair.error.tooManyRoutes": "ペアリングコードの経路が多すぎます。",
    "pair.error.loopback": "ペアリングコードがループバックアドレスを指しています。",
    "pair.error.missingPayload": "ペアリングペイロードがありません。",
    "pair.error.invalidRoute": "ペアリングコードのホストまたはポートが無効です。",
    "pair.error.failed": "ペアリングに失敗しました。",
    "paired.defaultTitle": "ペアリング済み Mac",
    "paired.noRoute": "対応する経路がありません",
    "paired.notFound": "ペアリング済み Mac が見つかりません。",
    "workspaces.title": "ワークスペース",
    "workspaces.empty": "ワークスペースはまだ報告されていません。",
    "workspace.defaultTitle": "ワークスペース",
    "terminal.defaultTitle": "ターミナル",
    "terminal.notFound": "ターミナルが見つかりません。",
    "terminal.noTerminals": "ターミナルなし",
    "terminal.new": "新規",
    "terminal.open": "開く",
    "terminal.loading": "ターミナルの再生を読み込み中...",
    "terminal.empty": "（ターミナルは空です）",
    "terminal.inputPlaceholder": "ターミナルへ入力を送信",
    "host.connected": "接続済み",
    "host.defaultName": "接続済み Mac",
    "request.failed": "リクエストに失敗しました。",
    "refresh": "更新",
    "back": "戻る",
    "replay": "再生",
    "paste": "貼り付け",
    "send": "送信",
    "forget": "削除",
    "connect": "接続",
  },
};

const locale = navigator.language?.toLowerCase().startsWith("ja") ? "ja" : "en";

function t(key) {
  return messages[locale][key] || messages.en[key] || key;
}

const elements = {
  connectionText: document.getElementById("connectionText"),
  closeConnection: document.getElementById("closeConnection"),
  pairingView: document.getElementById("pairingView"),
  pairingCode: document.getElementById("pairingCode"),
  pairButton: document.getElementById("pairButton"),
  pairedList: document.getElementById("pairedList"),
  workspaceView: document.getElementById("workspaceView"),
  hostText: document.getElementById("hostText"),
  refreshWorkspaces: document.getElementById("refreshWorkspaces"),
  workspaceList: document.getElementById("workspaceList"),
  terminalView: document.getElementById("terminalView"),
  backToWorkspaces: document.getElementById("backToWorkspaces"),
  terminalTitle: document.getElementById("terminalTitle"),
  terminalMeta: document.getElementById("terminalMeta"),
  refreshTerminal: document.getElementById("refreshTerminal"),
  terminalOutput: document.getElementById("terminalOutput"),
  terminalInput: document.getElementById("terminalInput"),
  pasteInput: document.getElementById("pasteInput"),
  sendInput: document.getElementById("sendInput"),
  toast: document.getElementById("toast"),
};

function localizeStaticText() {
  document.querySelectorAll("[data-i18n]").forEach((node) => {
    node.textContent = t(node.getAttribute("data-i18n"));
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((node) => {
    node.setAttribute("placeholder", t(node.getAttribute("data-i18n-placeholder")));
  });
  document.querySelectorAll("[data-i18n-aria]").forEach((node) => {
    node.setAttribute("aria-label", t(node.getAttribute("data-i18n-aria")));
  });
  elements.connectionText.textContent = t("app.disconnected");
  elements.hostText.textContent = t("host.connected");
  elements.terminalTitle.textContent = t("terminal.defaultTitle");
}

function bridge() {
  if (!window.cmuxAndroid) {
    throw new Error("Android bridge is unavailable");
  }
  return window.cmuxAndroid;
}

function showToast(message) {
  elements.toast.textContent = message || "";
}

function showScreen(name) {
  elements.pairingView.classList.toggle("hidden", name !== "pairing");
  elements.workspaceView.classList.toggle("hidden", name !== "workspaces");
  elements.terminalView.classList.toggle("hidden", name !== "terminal");
}

function renderPairedMacs() {
  if (state.macs.length === 0) {
    elements.pairedList.innerHTML = "";
    return;
  }
  elements.pairedList.innerHTML = state.macs.map((mac) => {
    const route = (mac.routes || [])[0];
    const title = mac.display_name || route?.host || t("paired.defaultTitle");
    const subtitle = route ? `${route.host}:${route.port}` : t("paired.noRoute");
    return `
      <article class="card">
        <div>
          <div class="card-title">${escapeHtml(title)}</div>
          <div class="card-subtitle">${escapeHtml(subtitle)}</div>
        </div>
        <div class="card-actions">
          <button class="primary" data-connect="${escapeHtml(mac.id)}">${escapeHtml(t("connect"))}</button>
          <button data-forget="${escapeHtml(mac.id)}">${escapeHtml(t("forget"))}</button>
        </div>
      </article>
    `;
  }).join("");
}

function renderWorkspaces() {
  if (state.workspaces.length === 0) {
    elements.workspaceList.innerHTML = `<article class="card"><div class="card-subtitle">${escapeHtml(t("workspaces.empty"))}</div></article>`;
    return;
  }
  elements.workspaceList.innerHTML = state.workspaces.map((workspace) => {
    const terminals = workspace.terminals || [];
    const terminalRows = terminals.length === 0
      ? `<div class="terminal-row"><span class="card-subtitle">${escapeHtml(t("terminal.noTerminals"))}</span><button data-create-terminal="${escapeHtml(workspace.id)}">${escapeHtml(t("terminal.new"))}</button></div>`
      : terminals.map((terminal) => `
          <div class="terminal-row">
            <div>
              <div class="card-title">${escapeHtml(terminal.title || t("terminal.defaultTitle"))}</div>
              <div class="card-subtitle">${escapeHtml(terminal.current_directory || "")}</div>
            </div>
            <button class="primary" data-open-terminal="${escapeHtml(workspace.id)}" data-terminal-id="${escapeHtml(terminal.id)}">${escapeHtml(t("terminal.open"))}</button>
          </div>
        `).join("");
    return `
      <article class="card workspace-card">
        <div>
          <div class="card-title">${escapeHtml(workspace.title || t("workspace.defaultTitle"))}</div>
          <div class="card-subtitle">${escapeHtml(workspace.preview || workspace.current_directory || "")}</div>
        </div>
        ${terminalRows}
      </article>
    `;
  }).join("");
}

function openTerminal(workspaceId, terminalId) {
  const workspace = state.workspaces.find((item) => item.id === workspaceId);
  const terminal = workspace?.terminals?.find((item) => item.id === terminalId);
  if (!workspace || !terminal) {
    showToast(t("terminal.notFound"));
    return;
  }
  state.activeWorkspace = workspace;
  state.activeTerminal = terminal;
  elements.terminalTitle.textContent = terminal.title || t("terminal.defaultTitle");
  elements.terminalMeta.textContent = workspace.title || "";
  elements.terminalOutput.textContent = t("terminal.loading");
  showScreen("terminal");
  replayActiveTerminal();
}

function replayActiveTerminal() {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  bridge().replayTerminal(
    state.activeWorkspace.id,
    state.activeTerminal.id,
    terminalColumns(),
    terminalRows(),
  );
}

function sendTerminalInput(mode) {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const text = elements.terminalInput.value;
  if (!text) return;
  if (mode === "paste") {
    bridge().pasteText(
      state.activeWorkspace.id,
      state.activeTerminal.id,
      text,
      "return",
      terminalColumns(),
      terminalRows(),
    );
  } else {
    bridge().sendInput(
      state.activeWorkspace.id,
      state.activeTerminal.id,
      text,
      terminalColumns(),
      terminalRows(),
    );
  }
  elements.terminalInput.value = "";
  window.setTimeout(replayActiveTerminal, 250);
}

function terminalColumns() {
  return Math.max(20, Math.min(160, Math.floor(elements.terminalOutput.clientWidth / 7)));
}

function terminalRows() {
  return Math.max(8, Math.min(80, Math.floor(elements.terminalOutput.clientHeight / 16)));
}

function decodeReplay(result) {
  if (result.render_grid) {
    return renderGridToText(result.render_grid);
  }
  if (result.snapshot_data_b64) {
    return atob(result.snapshot_data_b64);
  }
  if (result.data_b64) {
    return atob(result.data_b64);
  }
  return "";
}

function renderGridToText(renderGrid) {
  if (Array.isArray(renderGrid.row_spans)) {
    const rowCount = Number.isInteger(renderGrid.rows) ? renderGrid.rows : 0;
    const rows = Array.from({ length: rowCount }, () => "");
    const spans = [...renderGrid.row_spans].sort((left, right) => {
      const rowDelta = (left.row || 0) - (right.row || 0);
      return rowDelta === 0 ? (left.column || 0) - (right.column || 0) : rowDelta;
    });
    for (const span of spans) {
      const rowIndex = span.row || 0;
      if (!rows[rowIndex]) rows[rowIndex] = "";
      const column = span.column || 0;
      if (rows[rowIndex].length < column) {
        rows[rowIndex] += " ".repeat(column - rows[rowIndex].length);
      }
      rows[rowIndex] += span.text || "";
    }
    return rows.join("\n").replace(/\s+$/u, "");
  }

  const rows = renderGrid.rows || renderGrid.lines || [];
  if (!Array.isArray(rows)) return JSON.stringify(renderGrid, null, 2);

  return rows.map((row) => {
    if (typeof row === "string") return row;
    if (Array.isArray(row)) {
      return row.map((cell) => {
        if (typeof cell === "string") return cell;
        return cell.text || cell.ch || cell.c || " ";
      }).join("");
    }
    if (Array.isArray(row.cells)) {
      return row.cells.map((cell) => cell.text || cell.ch || cell.c || " ").join("");
    }
    return row.text || "";
  }).join("\n");
}

function handleRpcResult(method, result) {
  if (method === "mobile.host.status") {
    state.hostStatus = result;
    const name = result.mac_display_name || result.host_service?.display_name || t("host.defaultName");
    elements.hostText.textContent = name;
    return;
  }
  if (method === "mobile.workspace.list" || method === "mobile.terminal.create") {
    state.workspaces = result.workspaces || [];
    renderWorkspaces();
    showScreen("workspaces");
    if (result.created_terminal_id) {
      const workspaceId = result.created_workspace_id || result.workspaces?.[0]?.id;
      if (workspaceId) openTerminal(workspaceId, result.created_terminal_id);
    }
    return;
  }
  if (method === "mobile.terminal.replay") {
    elements.terminalOutput.textContent = decodeReplay(result) || t("terminal.empty");
    return;
  }
  if (method === "mobile.terminal.input" || method === "mobile.terminal.paste") {
    window.setTimeout(replayActiveTerminal, 180);
  }
}

window.cmuxNativeEvent = (event) => {
  if (event.type === "pairedMacs") {
    state.macs = event.payload.macs || [];
    renderPairedMacs();
    return;
  }
  if (event.type === "connection") {
    const { state: nextState, detail } = event.payload;
    state.connected = nextState === "open";
    elements.connectionText.textContent = detail ? `${nextState}: ${detail}` : nextState;
    showToast(nextState === "open" ? t("app.connected") : detail || nextState);
    return;
  }
  if (event.type === "rpcResult") {
    handleRpcResult(event.payload.method, event.payload.result || {});
    return;
  }
  if (event.type === "rpcError" || event.type === "error") {
    showToast(event.payload.message || t(event.payload.message_key) || t("request.failed"));
  }
};

elements.pairButton.addEventListener("click", () => {
  bridge().pair(elements.pairingCode.value);
});

elements.pairedList.addEventListener("click", (event) => {
  const connectId = event.target.getAttribute("data-connect");
  const forgetId = event.target.getAttribute("data-forget");
  if (connectId) bridge().connect(connectId);
  if (forgetId) bridge().forget(forgetId);
});

elements.workspaceList.addEventListener("click", (event) => {
  const terminalId = event.target.getAttribute("data-terminal-id");
  const workspaceId = event.target.getAttribute("data-open-terminal");
  const createWorkspaceId = event.target.getAttribute("data-create-terminal");
  if (workspaceId && terminalId) openTerminal(workspaceId, terminalId);
  if (createWorkspaceId) bridge().createTerminal(createWorkspaceId);
});

elements.refreshWorkspaces.addEventListener("click", () => bridge().refreshWorkspaces());
elements.closeConnection.addEventListener("click", () => bridge().closeConnection());
elements.backToWorkspaces.addEventListener("click", () => showScreen("workspaces"));
elements.refreshTerminal.addEventListener("click", replayActiveTerminal);
elements.sendInput.addEventListener("click", () => sendTerminalInput("input"));
elements.pasteInput.addEventListener("click", () => sendTerminalInput("paste"));

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

localizeStaticText();
bridge().initialState();
