const state = {
  macs: [],
  connected: false,
  hostStatus: null,
  workspaces: [],
  groups: [],
  activeWorkspace: null,
  activeTerminal: null,
  terminalFrames: new Map(),
  effectiveViewport: null,
  lastViewportReport: "",
  viewportReportTimer: 0,
  pendingScrollLines: 0,
  scrollFlushTimer: 0,
  lastTouchY: null,
  touchStart: null,
  suppressClickUntil: 0,
  inferredUnreadNotificationCount: 0,
  authoritativeUnreadNotificationCount: null,
  deliveredNotificationIds: [],
  nativeNotificationsEnabled: false,
  nativeNotificationsCanRequest: false,
  stackAccessTokenConfigured: false,
  stackRefreshTokenConfigured: false,
  workspaceFilter: "all",
  workspaceSearch: "",
  workspaceRefreshPending: false,
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
    "pair.scan": "Scan QR",
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
    "pair.scanCanceled": "QR scan canceled.",
    "pair.scanUnavailable": "QR scanning is unavailable on this device.",
    "auth.title": "Stack Auth token",
    "auth.subtitleConfigured": "Signed in with a Stack session.",
    "auth.subtitleAccessOnly": "Stack access token is configured. Sign in to enable refresh.",
    "auth.subtitleMissing": "Add a Stack access token before opening workspaces or terminals.",
    "auth.token": "Access token",
    "auth.placeholder": "Paste Stack access token",
    "auth.signIn": "Sign in",
    "auth.save": "Save token",
    "auth.clear": "Clear token",
    "auth.saved": "Stack access token saved.",
    "auth.signedIn": "Signed in to Stack Auth.",
    "auth.cleared": "Stack access token cleared.",
    "auth.error.empty": "Stack access token is empty.",
    "auth.error.callback": "Stack Auth callback did not include usable tokens.",
    "auth.error.openSignIn": "Could not open Stack sign-in.",
    "auth.error.unauthorized": "Stack authorization failed. Save a fresh token, then try again.",
    "auth.error.accountMismatch": "This token belongs to a different Stack account.",
    "paired.defaultTitle": "Paired Mac",
    "paired.manage": "Paired Macs",
    "paired.noRoute": "No supported route",
    "paired.notFound": "Paired Mac was not found.",
    "workspaces.title": "Workspaces",
    "workspaces.empty": "No workspaces reported yet.",
    "workspaces.filterEmpty": "No matching workspaces.",
    "workspace.new": "New workspace",
    "workspace.defaultTitle": "Workspace",
    "workspace.searchLabel": "Search workspaces",
    "workspace.searchPlaceholder": "Search workspaces or terminals",
    "workspace.filterLabel": "Workspace filters",
    "workspace.filterAll": "All",
    "workspace.filterUnread": "Unread",
    "workspace.filterPinned": "Pinned",
    "workspace.rename": "Rename",
    "workspace.pin": "Pin",
    "workspace.unpin": "Unpin",
    "workspace.markRead": "Mark read",
    "workspace.markUnread": "Mark unread",
    "workspace.close": "Close",
    "workspace.renamePrompt": "Workspace name",
    "workspace.renameEmpty": "Workspace name is empty.",
    "workspace.actionsUnsupported": "This Mac does not support workspace actions yet.",
    "workspace.closeUnsupported": "This Mac does not support closing workspaces yet.",
    "notification.none": "No unread notifications.",
    "notification.unread": "{count} unread notification",
    "notification.unreadPlural": "{count} unread notifications",
    "notification.enable": "Enable alerts",
    "notification.sync": "Sync notifications",
    "notification.dismissAll": "Dismiss synced",
    "notification.noDelivered": "No synced notifications to dismiss.",
    "notification.synced": "Notifications synced.",
    "notification.dismissed": "Notifications dismissed.",
    "notification.enabled": "Android notifications enabled.",
    "notification.denied": "Android notifications are off.",
    "group.defaultName": "Group",
    "group.expand": "Expand group",
    "group.collapse": "Collapse group",
    "terminal.defaultTitle": "Terminal",
    "terminal.notFound": "Terminal was not found.",
    "terminal.noTerminals": "No terminals",
    "terminal.new": "New",
    "terminal.open": "Open",
    "terminal.loading": "Loading terminal replay...",
    "terminal.empty": "(terminal is empty)",
    "terminal.inputPlaceholder": "Send input to the terminal",
    "terminal.live": "Live terminal update received.",
    "terminal.copyEmpty": "No terminal output to copy.",
    "terminal.copied": "Terminal output copied.",
    "terminal.keys": "Terminal keys",
    "terminal.key.enter": "Enter",
    "terminal.key.tab": "Tab",
    "terminal.key.escape": "Esc",
    "terminal.key.backspace": "Backspace",
    "terminal.key.ctrlC": "Ctrl-C",
    "terminal.key.ctrlD": "Ctrl-D",
    "terminal.key.ctrlL": "Ctrl-L",
    "terminal.key.ctrlZ": "Ctrl-Z",
    "terminal.key.arrowUp": "Up",
    "terminal.key.arrowDown": "Down",
    "terminal.key.arrowLeft": "Left",
    "terminal.key.arrowRight": "Right",
    "terminal.key.home": "Home",
    "terminal.key.end": "End",
    "terminal.key.pageUp": "Page Up",
    "terminal.key.pageDown": "Page Down",
    "image.paste": "Image",
    "image.unsupported": "This browser cannot read the selected image.",
    "image.tooLarge": "Image is too large to paste.",
    "image.invalid": "Selected file is not a supported image.",
    "host.connected": "Connected",
    "host.defaultName": "Connected Mac",
    "request.failed": "Request failed.",
    "refresh": "Refresh",
    "back": "Back",
    "replay": "Replay",
    "copy": "Copy",
    "scroll.up": "Scroll up",
    "scroll.down": "Scroll down",
    "paste": "Paste",
    "clear": "Clear",
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
    "pair.scan": "QR をスキャン",
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
    "pair.scanCanceled": "QR スキャンをキャンセルしました。",
    "pair.scanUnavailable": "このデバイスでは QR スキャンを利用できません。",
    "auth.title": "Stack Auth トークン",
    "auth.subtitleConfigured": "Stack セッションでサインイン済みです。",
    "auth.subtitleAccessOnly": "Stack アクセストークンは設定済みです。更新を有効にするにはサインインしてください。",
    "auth.subtitleMissing": "ワークスペースやターミナルを開く前に Stack アクセストークンを追加してください。",
    "auth.token": "アクセストークン",
    "auth.placeholder": "Stack アクセストークンを貼り付け",
    "auth.signIn": "サインイン",
    "auth.save": "トークンを保存",
    "auth.clear": "トークンを消去",
    "auth.saved": "Stack アクセストークンを保存しました。",
    "auth.signedIn": "Stack Auth にサインインしました。",
    "auth.cleared": "Stack アクセストークンを消去しました。",
    "auth.error.empty": "Stack アクセストークンが空です。",
    "auth.error.callback": "Stack Auth コールバックに利用可能なトークンが含まれていません。",
    "auth.error.openSignIn": "Stack サインインを開けませんでした。",
    "auth.error.unauthorized": "Stack 認証に失敗しました。新しいトークンを保存してから再試行してください。",
    "auth.error.accountMismatch": "このトークンは別の Stack アカウントに属しています。",
    "paired.defaultTitle": "ペアリング済み Mac",
    "paired.manage": "ペアリング済み Mac",
    "paired.noRoute": "対応する経路がありません",
    "paired.notFound": "ペアリング済み Mac が見つかりません。",
    "workspaces.title": "ワークスペース",
    "workspaces.empty": "ワークスペースはまだ報告されていません。",
    "workspaces.filterEmpty": "一致するワークスペースはありません。",
    "workspace.new": "新しいワークスペース",
    "workspace.defaultTitle": "ワークスペース",
    "workspace.searchLabel": "ワークスペースを検索",
    "workspace.searchPlaceholder": "ワークスペースまたはターミナルを検索",
    "workspace.filterLabel": "ワークスペースフィルター",
    "workspace.filterAll": "すべて",
    "workspace.filterUnread": "未読",
    "workspace.filterPinned": "ピン留め",
    "workspace.rename": "名前を変更",
    "workspace.pin": "ピン留め",
    "workspace.unpin": "ピン留め解除",
    "workspace.markRead": "既読にする",
    "workspace.markUnread": "未読にする",
    "workspace.close": "閉じる",
    "workspace.renamePrompt": "ワークスペース名",
    "workspace.renameEmpty": "ワークスペース名が空です。",
    "workspace.actionsUnsupported": "この Mac はまだワークスペース操作に対応していません。",
    "workspace.closeUnsupported": "この Mac はまだワークスペースの終了に対応していません。",
    "notification.none": "未読通知はありません。",
    "notification.unread": "未読通知 {count} 件",
    "notification.unreadPlural": "未読通知 {count} 件",
    "notification.enable": "通知を有効化",
    "notification.sync": "通知を同期",
    "notification.dismissAll": "同期済みを消去",
    "notification.noDelivered": "消去できる同期済み通知はありません。",
    "notification.synced": "通知を同期しました。",
    "notification.dismissed": "通知を消去しました。",
    "notification.enabled": "Android 通知を有効にしました。",
    "notification.denied": "Android 通知はオフです。",
    "group.defaultName": "グループ",
    "group.expand": "グループを展開",
    "group.collapse": "グループを折りたたむ",
    "terminal.defaultTitle": "ターミナル",
    "terminal.notFound": "ターミナルが見つかりません。",
    "terminal.noTerminals": "ターミナルなし",
    "terminal.new": "新規",
    "terminal.open": "開く",
    "terminal.loading": "ターミナルの再生を読み込み中...",
    "terminal.empty": "（ターミナルは空です）",
    "terminal.inputPlaceholder": "ターミナルへ入力を送信",
    "terminal.live": "ターミナルのライブ更新を受信しました。",
    "terminal.copyEmpty": "コピーできるターミナル出力がありません。",
    "terminal.copied": "ターミナル出力をコピーしました。",
    "terminal.keys": "ターミナルキー",
    "terminal.key.enter": "Enter",
    "terminal.key.tab": "Tab",
    "terminal.key.escape": "Esc",
    "terminal.key.backspace": "Backspace",
    "terminal.key.ctrlC": "Ctrl-C",
    "terminal.key.ctrlD": "Ctrl-D",
    "terminal.key.ctrlL": "Ctrl-L",
    "terminal.key.ctrlZ": "Ctrl-Z",
    "terminal.key.arrowUp": "上",
    "terminal.key.arrowDown": "下",
    "terminal.key.arrowLeft": "左",
    "terminal.key.arrowRight": "右",
    "terminal.key.home": "Home",
    "terminal.key.end": "End",
    "terminal.key.pageUp": "Page Up",
    "terminal.key.pageDown": "Page Down",
    "image.paste": "画像",
    "image.unsupported": "選択した画像を読み取れません。",
    "image.tooLarge": "画像が大きすぎて貼り付けできません。",
    "image.invalid": "選択したファイルは対応している画像ではありません。",
    "host.connected": "接続済み",
    "host.defaultName": "接続済み Mac",
    "request.failed": "リクエストに失敗しました。",
    "refresh": "更新",
    "back": "戻る",
    "replay": "再生",
    "copy": "コピー",
    "scroll.up": "上へスクロール",
    "scroll.down": "下へスクロール",
    "paste": "貼り付け",
    "clear": "消去",
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
  notificationText: document.getElementById("notificationText"),
  closeConnection: document.getElementById("closeConnection"),
  pairingView: document.getElementById("pairingView"),
  pairingCode: document.getElementById("pairingCode"),
  backToWorkspacesFromPairing: document.getElementById("backToWorkspacesFromPairing"),
  scanPairingCode: document.getElementById("scanPairingCode"),
  pairButton: document.getElementById("pairButton"),
  authStatusText: document.getElementById("authStatusText"),
  stackAccessToken: document.getElementById("stackAccessToken"),
  startStackSignIn: document.getElementById("startStackSignIn"),
  saveStackAccessToken: document.getElementById("saveStackAccessToken"),
  clearStackAccessToken: document.getElementById("clearStackAccessToken"),
  pairedList: document.getElementById("pairedList"),
  workspaceView: document.getElementById("workspaceView"),
  hostText: document.getElementById("hostText"),
  refreshWorkspaces: document.getElementById("refreshWorkspaces"),
  createWorkspace: document.getElementById("createWorkspace"),
  showPairedMacs: document.getElementById("showPairedMacs"),
  enableNotifications: document.getElementById("enableNotifications"),
  syncNotifications: document.getElementById("syncNotifications"),
  dismissNotifications: document.getElementById("dismissNotifications"),
  workspaceSearch: document.getElementById("workspaceSearch"),
  workspaceFilters: document.getElementById("workspaceFilters"),
  workspaceList: document.getElementById("workspaceList"),
  terminalView: document.getElementById("terminalView"),
  backToWorkspaces: document.getElementById("backToWorkspaces"),
  terminalTitle: document.getElementById("terminalTitle"),
  terminalMeta: document.getElementById("terminalMeta"),
  copyTerminalOutput: document.getElementById("copyTerminalOutput"),
  refreshTerminal: document.getElementById("refreshTerminal"),
  terminalOutput: document.getElementById("terminalOutput"),
  scrollUp: document.getElementById("scrollUp"),
  scrollDown: document.getElementById("scrollDown"),
  terminalKeybar: document.querySelector(".terminal-keybar"),
  terminalInput: document.getElementById("terminalInput"),
  imageInput: document.getElementById("imageInput"),
  pasteInput: document.getElementById("pasteInput"),
  pasteImage: document.getElementById("pasteImage"),
  clearTerminalInput: document.getElementById("clearTerminalInput"),
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
  renderConnectionControls();
  renderNotificationStatus();
  renderAuthStatus();
  elements.hostText.textContent = t("host.connected");
  elements.terminalTitle.textContent = t("terminal.defaultTitle");
}

function renderAuthStatus() {
  if (state.stackAccessTokenConfigured && state.stackRefreshTokenConfigured) {
    elements.authStatusText.textContent = t("auth.subtitleConfigured");
  } else if (state.stackAccessTokenConfigured) {
    elements.authStatusText.textContent = t("auth.subtitleAccessOnly");
  } else {
    elements.authStatusText.textContent = t("auth.subtitleMissing");
  }
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
  elements.backToWorkspacesFromPairing.classList.toggle("hidden", name !== "pairing" || !state.connected);
}

function renderPairedMacs() {
  if (state.macs.length === 0) {
    elements.pairedList.innerHTML = "";
    return;
  }
  elements.pairedList.innerHTML = state.macs.map((mac) => {
    const route = displayRouteForMac(mac);
    const title = mac.display_name || route?.label || t("paired.defaultTitle");
    const subtitle = route?.label || t("paired.noRoute");
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

function displayRouteForMac(mac) {
  const routes = Array.isArray(mac.routes) ? mac.routes : [];
  const route = routes
    .filter(isSupportedMobileRoute)
    .sort((left, right) => {
      const priorityDelta = routePriority(left) - routePriority(right);
      return priorityDelta === 0
        ? String(left.id || "").localeCompare(String(right.id || ""))
        : priorityDelta;
    })[0];
  if (!route) return null;
  const url = String(route.url || "").trim();
  if (url) return { route, label: url };
  const host = String(route.host || "").trim();
  const port = Number(route.port);
  if (host && Number.isInteger(port) && port > 0) {
    return { route, label: `${host}:${port}` };
  }
  return { route, label: route.kind || route.id || t("paired.defaultTitle") };
}

function isSupportedMobileRoute(route) {
  return route?.kind === "tailscale" || route?.kind === "debug_loopback" || route?.kind === "websocket";
}

function routePriority(route) {
  return Number.isInteger(route?.priority) ? route.priority : 0;
}

function setDisabled(element, disabled) {
  if (element) element.disabled = disabled;
}

function renderConnectionControls() {
  const disconnected = !state.connected;
  setDisabled(elements.closeConnection, disconnected);
  setDisabled(elements.refreshWorkspaces, disconnected);
  setDisabled(elements.createWorkspace, disconnected);
  setDisabled(elements.syncNotifications, disconnected);
  setDisabled(elements.dismissNotifications, disconnected || state.deliveredNotificationIds.length === 0);
  setDisabled(elements.refreshTerminal, disconnected);
  setDisabled(elements.copyTerminalOutput, disconnected);
  setDisabled(elements.scrollUp, disconnected);
  setDisabled(elements.scrollDown, disconnected);
  if (elements.terminalKeybar) {
    elements.terminalKeybar.querySelectorAll("button").forEach((button) => {
      button.disabled = disconnected;
    });
  }
  setDisabled(elements.terminalInput, disconnected);
  setDisabled(elements.imageInput, disconnected);
  setDisabled(elements.pasteInput, disconnected);
  setDisabled(elements.pasteImage, disconnected);
  setDisabled(elements.clearTerminalInput, disconnected);
  setDisabled(elements.sendInput, disconnected);
}

function renderWorkspaces() {
  updateUnreadCountFromWorkspaces();
  renderNotificationStatus();
  renderWorkspaceFilters();
  if (state.workspaces.length === 0) {
    elements.workspaceList.innerHTML = `<article class="card"><div class="card-subtitle">${escapeHtml(t("workspaces.empty"))}</div></article>`;
    return;
  }
  const filteredWorkspaces = filteredWorkspaceList();
  if (filteredWorkspaces.length === 0) {
    elements.workspaceList.innerHTML = `<article class="card"><div class="card-subtitle">${escapeHtml(t("workspaces.filterEmpty"))}</div></article>`;
    return;
  }
  elements.workspaceList.innerHTML = workspaceListItems(filteredWorkspaces).join("");
  renderConnectionControls();
}

function renderWorkspaceFilters() {
  elements.workspaceFilters.querySelectorAll("[data-workspace-filter]").forEach((button) => {
    const active = button.getAttribute("data-workspace-filter") === state.workspaceFilter;
    button.classList.toggle("filter-active", active);
    button.setAttribute("aria-pressed", active ? "true" : "false");
  });
}

function filteredWorkspaceList() {
  const query = state.workspaceSearch.trim().toLowerCase();
  if (state.workspaceFilter === "unread") {
    return state.workspaces.filter((workspace) => workspace.has_unread && workspaceMatchesQuery(workspace, query));
  }
  if (state.workspaceFilter === "pinned") {
    return state.workspaces.filter((workspace) => workspace.is_pinned && workspaceMatchesQuery(workspace, query));
  }
  return state.workspaces.filter((workspace) => workspaceMatchesQuery(workspace, query));
}

function workspaceMatchesQuery(workspace, query) {
  if (!query) return true;
  const fields = [
    workspace.title,
    workspace.preview,
    workspace.current_directory,
    ...(workspace.terminals || []).flatMap((terminal) => [
      terminal.title,
      terminal.current_directory,
    ]),
  ];
  return fields.some((field) => String(field || "").toLowerCase().includes(query));
}

function updateUnreadCountFromWorkspaces() {
  const count = state.workspaces.filter((workspace) => workspace.has_unread).length;
  state.inferredUnreadNotificationCount = count;
}

function renderNotificationStatus() {
  if (!elements.notificationText) return;
  const count = Number.isInteger(state.authoritativeUnreadNotificationCount)
    ? state.authoritativeUnreadNotificationCount
    : state.inferredUnreadNotificationCount;
  const messageKey = count === 0
    ? "notification.none"
    : count === 1
      ? "notification.unread"
      : "notification.unreadPlural";
  elements.notificationText.textContent = t(messageKey).replace("{count}", String(count));
  if (elements.dismissNotifications) {
    elements.dismissNotifications.disabled = !state.connected || state.deliveredNotificationIds.length === 0;
  }
  if (elements.enableNotifications) {
    elements.enableNotifications.classList.toggle("hidden", state.nativeNotificationsEnabled || !state.nativeNotificationsCanRequest);
  }
}

function refreshActiveTerminalFromWorkspaces() {
  if (!state.activeWorkspace || !state.activeTerminal) return false;
  const workspace = state.workspaces.find((item) => item.id === state.activeWorkspace.id);
  const terminal = workspace?.terminals?.find((item) => item.id === state.activeTerminal.id);
  if (!workspace || !terminal) {
    state.activeWorkspace = null;
    state.activeTerminal = null;
    state.effectiveViewport = null;
    state.lastViewportReport = "";
    return false;
  }
  state.activeWorkspace = workspace;
  state.activeTerminal = terminal;
  elements.terminalTitle.textContent = terminal.title || t("terminal.defaultTitle");
  elements.terminalMeta.textContent = workspace.title || "";
  return true;
}

function workspaceListItems(workspaces) {
  const groupsById = new Map((state.groups || []).map((group) => [group.id, group]));
  const emittedGroups = new Set();
  const items = [];
  for (const workspace of workspaces) {
    const groupId = workspace.group_id;
    const group = groupId ? groupsById.get(groupId) : null;
    if (group && !emittedGroups.has(group.id)) {
      emittedGroups.add(group.id);
      items.push(renderWorkspaceGroup(group));
    }
    if (!group?.is_collapsed) {
      items.push(renderWorkspaceCard(workspace, group));
    }
  }
  return items;
}

function renderWorkspaceGroup(group) {
  const label = group.name || t("group.defaultName");
  const actionKey = group.is_collapsed ? "group.expand" : "group.collapse";
  const disabled = state.connected ? "" : " disabled";
  return `
    <div class="workspace-group">
      <div>
        <div class="workspace-group-title">${escapeHtml(label)}</div>
      </div>
      <button data-toggle-group="${escapeHtml(group.id)}" data-collapsed="${group.is_collapsed ? "true" : "false"}"${disabled}>${escapeHtml(t(actionKey))}</button>
    </div>
  `;
}

function renderWorkspaceCard(workspace, group) {
  const terminals = workspace.terminals || [];
  const workspaceActions = renderWorkspaceActions(workspace);
  const disabled = state.connected ? "" : " disabled";
  const terminalRows = terminals.length === 0
    ? `<div class="terminal-row"><span class="card-subtitle">${escapeHtml(t("terminal.noTerminals"))}</span><button data-create-terminal="${escapeHtml(workspace.id)}"${disabled}>${escapeHtml(t("terminal.new"))}</button></div>`
    : terminals.map((terminal) => `
        <div class="terminal-row">
          <div>
            <div class="card-title">${escapeHtml(terminal.title || t("terminal.defaultTitle"))}</div>
            <div class="card-subtitle">${escapeHtml(terminal.current_directory || "")}</div>
          </div>
          <button class="primary" data-open-terminal="${escapeHtml(workspace.id)}" data-terminal-id="${escapeHtml(terminal.id)}"${disabled}>${escapeHtml(t("terminal.open"))}</button>
        </div>
      `).join("");
  return `
    <article class="card workspace-card${group ? " grouped-workspace" : ""}">
      <div>
        <div class="card-title">${escapeHtml(workspace.title || t("workspace.defaultTitle"))}</div>
        <div class="card-subtitle">${escapeHtml(workspace.preview || workspace.current_directory || "")}</div>
      </div>
      ${workspaceActions}
      ${terminalRows}
    </article>
  `;
}

function renderWorkspaceActions(workspace) {
  const disabled = state.connected ? "" : " disabled";
  return `
    <div class="workspace-actions">
      <button data-rename-workspace="${escapeHtml(workspace.id)}"${disabled}>${escapeHtml(t("workspace.rename"))}</button>
      <button data-pin-workspace="${escapeHtml(workspace.id)}" data-pinned="${workspace.is_pinned ? "true" : "false"}"${disabled}>${escapeHtml(workspace.is_pinned ? t("workspace.unpin") : t("workspace.pin"))}</button>
      <button data-read-workspace="${escapeHtml(workspace.id)}" data-unread="${workspace.has_unread ? "true" : "false"}"${disabled}>${escapeHtml(workspace.has_unread ? t("workspace.markRead") : t("workspace.markUnread"))}</button>
      <button data-close-workspace="${escapeHtml(workspace.id)}"${disabled}>${escapeHtml(t("workspace.close"))}</button>
    </div>
  `;
}

function hostCapabilities() {
  return state.hostStatus?.capabilities || state.hostStatus?.host_service?.capabilities || [];
}

function hasCapability(capability) {
  return hostCapabilities().includes(capability);
}

function renameWorkspace(workspaceId) {
  if (!hasCapability("workspace.actions.v1")) {
    showToast(t("workspace.actionsUnsupported"));
    return;
  }
  const workspace = state.workspaces.find((item) => item.id === workspaceId);
  const currentTitle = workspace?.title || "";
  const nextTitle = window.prompt(t("workspace.renamePrompt"), currentTitle)?.trim();
  if (nextTitle == null) return;
  if (!nextTitle) {
    showToast(t("workspace.renameEmpty"));
    return;
  }
  bridge().renameWorkspace(workspaceId, nextTitle);
}

function toggleWorkspacePinned(workspaceId, isPinned) {
  if (!hasCapability("workspace.actions.v1")) {
    showToast(t("workspace.actionsUnsupported"));
    return;
  }
  bridge().setWorkspacePinned(workspaceId, !isPinned);
}

function toggleWorkspaceUnread(workspaceId, hasUnread) {
  if (!hasCapability("workspace.read_state.v1")) {
    showToast(t("workspace.actionsUnsupported"));
    return;
  }
  bridge().setWorkspaceUnread(workspaceId, !hasUnread);
}

function closeWorkspace(workspaceId) {
  if (!hasCapability("workspace.close.v1")) {
    showToast(t("workspace.closeUnsupported"));
    return;
  }
  bridge().closeWorkspace(workspaceId);
}

function syncNotifications() {
  bridge().reconcileNotifications(JSON.stringify(state.deliveredNotificationIds));
}

function dismissSyncedNotifications() {
  if (state.deliveredNotificationIds.length === 0) {
    showToast(t("notification.noDelivered"));
    return;
  }
  bridge().dismissNotifications(JSON.stringify(state.deliveredNotificationIds));
}

function toggleWorkspaceGroup(groupId, isCollapsed) {
  bridge().setWorkspaceGroupCollapsed(groupId, !isCollapsed);
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
  state.effectiveViewport = null;
  state.lastViewportReport = "";
  elements.terminalTitle.textContent = terminal.title || t("terminal.defaultTitle");
  elements.terminalMeta.textContent = workspace.title || "";
  elements.terminalOutput.textContent = t("terminal.loading");
  showScreen("terminal");
  reportActiveViewport();
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

function reportActiveViewport() {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const columns = terminalColumns();
  const rows = terminalRows();
  const signature = `${state.activeWorkspace.id}:${state.activeTerminal.id}:${columns}x${rows}`;
  if (signature === state.lastViewportReport) return;
  state.lastViewportReport = signature;
  bridge().reportViewport(state.activeWorkspace.id, state.activeTerminal.id, columns, rows);
}

function scheduleViewportReport() {
  if (!state.activeWorkspace || !state.activeTerminal || elements.terminalView.classList.contains("hidden")) return;
  window.clearTimeout(state.viewportReportTimer);
  state.viewportReportTimer = window.setTimeout(() => {
    reportActiveViewport();
    replayActiveTerminal();
  }, 180);
}

function handleTerminalSetFont(payload) {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const surfaceId = payload.surface_id || payload.surfaceID || payload.surfaceId;
  const workspaceId = payload.workspace_id || payload.workspaceID || payload.workspaceId;
  if (surfaceId && surfaceId !== state.activeTerminal.id) return;
  if (!surfaceId && workspaceId && workspaceId !== state.activeWorkspace.id) return;
  const fontSize = Number(payload.font_size ?? payload.fontSize);
  if (!Number.isFinite(fontSize) || fontSize <= 0) return;
  const clamped = Math.max(8, Math.min(36, fontSize));
  elements.terminalOutput.style.fontSize = `${clamped}px`;
  state.effectiveViewport = null;
  state.lastViewportReport = "";
  reportActiveViewport();
  replayActiveTerminal();
}

function closeActiveTerminal() {
  if (state.activeWorkspace && state.activeTerminal) {
    bridge().clearViewport(state.activeWorkspace.id, state.activeTerminal.id);
  }
  clearActiveTerminalState();
  showScreen("workspaces");
}

function clearActiveTerminalState() {
  state.activeWorkspace = null;
  state.activeTerminal = null;
  state.effectiveViewport = null;
  state.lastViewportReport = "";
  state.pendingScrollLines = 0;
  state.terminalFrames.clear();
  window.clearTimeout(state.viewportReportTimer);
  window.clearTimeout(state.scrollFlushTimer);
  elements.terminalOutput.textContent = "";
  elements.terminalOutput.removeAttribute("data-columns");
}

function queueTerminalScroll(deltaLines, options = {}) {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  if (!Number.isFinite(deltaLines) || deltaLines === 0) return;
  state.pendingScrollLines += deltaLines;
  window.clearTimeout(state.scrollFlushTimer);
  state.scrollFlushTimer = window.setTimeout(() => flushTerminalScroll(options), 60);
}

function flushTerminalScroll(options = {}) {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const deltaLines = state.pendingScrollLines;
  state.pendingScrollLines = 0;
  if (!Number.isFinite(deltaLines) || Math.abs(deltaLines) < 0.05) return;
  const pointer = terminalPointerCell(options.clientX, options.clientY);
  bridge().scrollTerminal(
    state.activeWorkspace.id,
    state.activeTerminal.id,
    deltaLines,
    pointer.column,
    pointer.row,
    maxScrollbackRowsFor(deltaLines),
    terminalColumns(),
    terminalRows(),
  );
}

function terminalPointerCell(clientX, clientY) {
  const bounds = elements.terminalOutput.getBoundingClientRect();
  const x = Number.isFinite(clientX) ? clientX - bounds.left : bounds.width / 2;
  const y = Number.isFinite(clientY) ? clientY - bounds.top : bounds.height / 2;
  const columnWidth = Math.max(1, bounds.width / terminalColumns());
  const rowHeight = Math.max(1, bounds.height / terminalRows());
  return {
    column: Math.max(0, Math.floor(x / columnWidth)),
    row: Math.max(0, Math.floor(y / rowHeight)),
  };
}

function maxScrollbackRowsFor(deltaLines) {
  const magnitude = Math.abs(deltaLines);
  if (magnitude < terminalRows()) return 0;
  return Math.min(20000, Math.max(terminalRows() * 2, Math.ceil(magnitude * 2)));
}

function handleTerminalWheel(event) {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  event.preventDefault();
  const lineHeight = 16;
  const pageHeight = Math.max(lineHeight, elements.terminalOutput.clientHeight);
  const divisor = event.deltaMode === 1
    ? 1
    : event.deltaMode === 2
      ? pageHeight / lineHeight
      : lineHeight;
  queueTerminalScroll(event.deltaY / divisor, { clientX: event.clientX, clientY: event.clientY });
}

function handleTerminalTouchStart(event) {
  const touch = event.touches?.[0];
  state.lastTouchY = touch?.clientY ?? null;
  state.touchStart = touch
    ? { clientX: touch.clientX, clientY: touch.clientY, moved: false }
    : null;
}

function handleTerminalTouchMove(event) {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const touch = event.touches?.[0];
  if (!touch || state.lastTouchY == null) return;
  event.preventDefault();
  const deltaPixels = state.lastTouchY - touch.clientY;
  state.lastTouchY = touch.clientY;
  if (state.touchStart) {
    const distance = Math.hypot(touch.clientX - state.touchStart.clientX, touch.clientY - state.touchStart.clientY);
    if (distance > 8) state.touchStart.moved = true;
  }
  queueTerminalScroll(deltaPixels / 16, { clientX: touch.clientX, clientY: touch.clientY });
}

function handleTerminalTouchEnd() {
  if (state.touchStart && !state.touchStart.moved) {
    clickTerminalAt(state.touchStart.clientX, state.touchStart.clientY);
    state.suppressClickUntil = Date.now() + 500;
  }
  state.lastTouchY = null;
  state.touchStart = null;
}

function handleTerminalClick(event) {
  if (Date.now() < state.suppressClickUntil) return;
  clickTerminalAt(event.clientX, event.clientY);
}

function clickTerminalAt(clientX, clientY) {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const pointer = terminalPointerCell(clientX, clientY);
  bridge().clickTerminal(
    state.activeWorkspace.id,
    state.activeTerminal.id,
    pointer.column,
    pointer.row,
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

function handleTerminalInputKeydown(event) {
  if (event.key !== "Enter" || !event.ctrlKey) return;
  event.preventDefault();
  sendTerminalInput(event.shiftKey ? "paste" : "input");
}

function clearTerminalInput() {
  elements.terminalInput.value = "";
  elements.terminalInput.focus();
}

function sendTerminalKey(key) {
  const text = {
    enter: "\r",
    tab: "\t",
    escape: "\u001b",
    backspace: "\u007f",
    "ctrl-c": "\u0003",
    "ctrl-d": "\u0004",
    "ctrl-l": "\u000c",
    "ctrl-z": "\u001a",
    "arrow-up": "\u001b[A",
    "arrow-down": "\u001b[B",
    "arrow-right": "\u001b[C",
    "arrow-left": "\u001b[D",
    home: "\u001b[H",
    end: "\u001b[F",
    "page-up": "\u001b[5~",
    "page-down": "\u001b[6~",
  }[key];
  if (!text || !state.activeWorkspace || !state.activeTerminal) return;
  bridge().sendInput(
    state.activeWorkspace.id,
    state.activeTerminal.id,
    text,
    terminalColumns(),
    terminalRows(),
  );
  window.setTimeout(replayActiveTerminal, 250);
}

async function copyTerminalOutput() {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const text = elements.terminalOutput.textContent.trimEnd();
  if (!text) {
    showToast(t("terminal.copyEmpty"));
    return;
  }
  try {
    await writeClipboardText(text);
    showToast(t("terminal.copied"));
  } catch (_) {
    showToast(t("request.failed"));
  }
}

async function writeClipboardText(text) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.setAttribute("readonly", "");
  textarea.style.position = "fixed";
  textarea.style.left = "-9999px";
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand("copy");
  document.body.removeChild(textarea);
  if (!copied) {
    throw new Error("copy failed");
  }
}

function chooseImageForPaste() {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  elements.imageInput.value = "";
  elements.imageInput.click();
}

function pasteSelectedImage() {
  if (!state.activeWorkspace || !state.activeTerminal) return;
  const file = elements.imageInput.files?.[0];
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    showToast(t("image.invalid"));
    return;
  }
  if (file.size > MAX_IMAGE_BYTES) {
    showToast(t("image.tooLarge"));
    return;
  }
  if (typeof FileReader === "undefined") {
    showToast(t("image.unsupported"));
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    const dataUrl = String(reader.result || "");
    const commaIndex = dataUrl.indexOf(",");
    if (commaIndex < 0) {
      showToast(t("image.invalid"));
      return;
    }
    bridge().pasteImage(
      state.activeWorkspace.id,
      state.activeTerminal.id,
      dataUrl.slice(commaIndex + 1),
      imageFormatForFile(file),
      terminalColumns(),
      terminalRows(),
    );
  };
  reader.onerror = () => showToast(t("image.unsupported"));
  reader.readAsDataURL(file);
}

function imageFormatForFile(file) {
  const type = file.type.toLowerCase();
  if (type === "image/jpeg") return "jpg";
  if (type === "image/png") return "png";
  if (type === "image/gif") return "gif";
  if (type === "image/webp") return "webp";
  const extension = file.name.split(".").pop()?.toLowerCase() || "";
  return extension.replace(/[^a-z0-9]/gu, "") || "png";
}

function terminalColumns() {
  return Math.max(20, Math.min(160, Math.floor(elements.terminalOutput.clientWidth / terminalCellWidth())));
}

function terminalRows() {
  return Math.max(8, Math.min(80, Math.floor(elements.terminalOutput.clientHeight / terminalLineHeight())));
}

function terminalFontSize() {
  const size = Number.parseFloat(window.getComputedStyle(elements.terminalOutput).fontSize);
  return Number.isFinite(size) && size > 0 ? size : 12;
}

function terminalLineHeight() {
  const styles = window.getComputedStyle(elements.terminalOutput);
  const lineHeight = Number.parseFloat(styles.lineHeight);
  if (Number.isFinite(lineHeight) && lineHeight > 0) return lineHeight;
  return terminalFontSize() * 1.35;
}

function terminalCellWidth() {
  return Math.max(4, terminalFontSize() * 0.58);
}

function decodeReplayText(result) {
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

function renderTerminalReplay(result) {
  if (result.render_grid) {
    const frame = normalizeRenderGrid(result.render_grid);
    if (frame?.surfaceId && state.activeTerminal && frame.surfaceId !== state.activeTerminal.id) {
      return;
    }
    renderTerminalFrame(result.render_grid, { reset: true });
    return;
  }
  elements.terminalOutput.textContent = decodeReplayText(result) || t("terminal.empty");
}

function renderTerminalFrame(rawFrame, options = {}) {
  const frame = normalizeRenderGrid(rawFrame);
  if (!frame) {
    elements.terminalOutput.textContent = renderGridToText(rawFrame) || t("terminal.empty");
    return;
  }

  const surfaceId = frame.surfaceId || state.activeTerminal?.id || "active";
  const existing = state.terminalFrames.get(surfaceId);
  if (existing && frame.stateSeq > 0 && existing.stateSeq > frame.stateSeq) {
    return;
  }
  const previous = options.reset || frame.full ? null : state.terminalFrames.get(surfaceId);
  const next = applyRenderGridFrame(previous, frame);
  state.terminalFrames.set(surfaceId, next);
  elements.terminalOutput.dataset.columns = String(next.columns);
  elements.terminalOutput.innerHTML = terminalFrameToHtml(next) || escapeHtml(t("terminal.empty"));
}

function normalizeRenderGrid(rawFrame) {
  if (!rawFrame || typeof rawFrame !== "object") return null;
  const rowSpans = rawFrame.row_spans || rawFrame.rowSpans;
  if (!Array.isArray(rowSpans)) return null;
  return {
    surfaceId: rawFrame.surface_id || rawFrame.surfaceID || rawFrame.surfaceId || "",
    stateSeq: rawFrame.state_seq ?? rawFrame.stateSeq ?? 0,
    columns: clampInteger(rawFrame.columns, 20, 300, terminalColumns()),
    rows: clampInteger(rawFrame.rows, 5, 120, terminalRows()),
    cursor: rawFrame.cursor || null,
    full: rawFrame.full !== false,
    clearedRows: rawFrame.cleared_rows || rawFrame.clearedRows || [],
    styles: stylesById(rawFrame.styles || []),
    rowSpans,
    activeScreen: rawFrame.active_screen || rawFrame.activeScreen || "primary",
    terminalForeground: rawFrame.terminal_foreground || rawFrame.terminalForeground || "",
    terminalBackground: rawFrame.terminal_background || rawFrame.terminalBackground || "",
    terminalCursorColor: rawFrame.terminal_cursor_color || rawFrame.terminalCursorColor || "",
    scrollbackRows: clampInteger(rawFrame.scrollback_rows ?? rawFrame.scrollbackRows, 0, 20000, 0),
    scrollbackSpans: rawFrame.scrollback_spans || rawFrame.scrollbackSpans || [],
  };
}

function applyRenderGridFrame(previous, frame) {
  const rows = previous?.rows?.length === frame.rows
    ? previous.rows.map((row) => row.map((cell) => ({ ...cell })))
    : emptyTerminalRows(frame.rows, frame.columns);
  const touchedRows = new Set(frame.clearedRows.filter((row) => row >= 0 && row < frame.rows));
  for (const span of frame.rowSpans) {
    const row = Number(span.row);
    if (Number.isInteger(row) && row >= 0 && row < frame.rows) touchedRows.add(row);
  }
  if (previous == null || frame.full) {
    for (let row = 0; row < frame.rows; row += 1) touchedRows.add(row);
  }

  const spansByRow = groupSpansByRow(frame.rowSpans, frame.rows);
  for (const row of touchedRows) {
    rows[row] = buildRowCells(spansByRow.get(row) || [], frame.columns, frame.styles);
  }

  const scrollback = frame.full
    ? buildRowsFromSpans(frame.scrollbackSpans, frame.scrollbackRows, frame.columns, frame.styles)
    : previous?.scrollback || [];

  return {
    surfaceId: frame.surfaceId,
    stateSeq: frame.stateSeq,
    columns: frame.columns,
    rows,
    scrollback,
    cursor: frame.cursor,
    activeScreen: frame.activeScreen,
    terminalForeground: frame.terminalForeground,
    terminalBackground: frame.terminalBackground,
    terminalCursorColor: frame.terminalCursorColor,
  };
}

function buildRowsFromSpans(spans, rowCount, columns, styles) {
  const rows = emptyTerminalRows(rowCount, columns);
  const spansByRow = groupSpansByRow(spans, rowCount);
  for (const [row, rowSpans] of spansByRow) {
    rows[row] = buildRowCells(rowSpans, columns, styles);
  }
  return rows;
}

function emptyTerminalRows(rowCount, columns) {
  return Array.from({ length: rowCount }, () => blankRow(columns));
}

function blankRow(columns) {
  return Array.from({ length: columns }, () => ({ text: " ", style: null }));
}

function groupSpansByRow(spans, rowCount) {
  const grouped = new Map();
  for (const span of spans || []) {
    const row = Number(span.row);
    if (!Number.isInteger(row) || row < 0 || row >= rowCount) continue;
    if (!grouped.has(row)) grouped.set(row, []);
    grouped.get(row).push(span);
  }
  for (const rowSpans of grouped.values()) {
    rowSpans.sort((left, right) => (left.column || 0) - (right.column || 0));
  }
  return grouped;
}

function buildRowCells(spans, columns, styles) {
  const cells = blankRow(columns);
  for (const span of spans) {
    const start = clampInteger(span.column, 0, columns - 1, 0);
    const style = styles.get(Number(span.style_id ?? span.styleID ?? 0)) || null;
    const chars = Array.from(String(span.text || ""));
    const width = clampInteger(span.cell_width ?? span.cellWidth, 0, columns - start, chars.length);
    const writeCount = Math.min(columns - start, Math.max(chars.length, width));
    for (let offset = 0; offset < writeCount; offset += 1) {
      cells[start + offset] = {
        text: chars[offset] || " ",
        style,
      };
    }
  }
  return cells;
}

function stylesById(styles) {
  const map = new Map();
  for (const style of styles) {
    if (!style || !Number.isInteger(Number(style.id))) continue;
    map.set(Number(style.id), style);
  }
  return map;
}

function terminalFrameToHtml(frame) {
  const rows = [];
  for (const row of frame.scrollback) {
    rows.push(rowToHtml(row, null, frame, " terminal-scrollback"));
  }
  frame.rows.forEach((row, index) => {
    const cursor = frame.cursor?.visible === false ? null : frame.cursor;
    const cursorColumn = cursor && cursor.row === index ? cursor.column : null;
    rows.push(rowToHtml(row, cursorColumn, frame, ""));
  });
  return rows.join("");
}

function rowToHtml(row, cursorColumn, frame, extraClass) {
  let html = "";
  let index = 0;
  while (index < row.length) {
    const cell = row[index];
    const isCursor = cursorColumn === index;
    const key = `${styleKey(cell.style)}:${isCursor}`;
    let end = index + 1;
    while (end < row.length) {
      const next = row[end];
      const nextCursor = cursorColumn === end;
      if (`${styleKey(next.style)}:${nextCursor}` !== key) break;
      end += 1;
    }
    const text = row.slice(index, end).map((item) => item.text || " ").join("");
    const classes = ["terminal-cell"];
    if (isCursor) classes.push("terminal-cursor");
    const style = styleToCss(cell.style, frame, isCursor);
    html += `<span class="${classes.join(" ")}"${style ? ` style="${escapeHtml(style)}"` : ""}>${escapeHtml(text)}</span>`;
    index = end;
  }
  return `<span class="terminal-line${extraClass}">${html}</span>`;
}

function styleKey(style) {
  if (!style) return "default";
  return [
    style.foreground || "",
    style.background || "",
    style.bold ? "b" : "",
    style.faint ? "f" : "",
    style.italic ? "i" : "",
    style.underline ? "u" : "",
    style.blink ? "blink" : "",
    style.inverse ? "inv" : "",
    style.invisible ? "hidden" : "",
    style.strikethrough ? "s" : "",
    style.overline ? "o" : "",
  ].join("|");
}

function styleToCss(style, frame, isCursor) {
  const rules = [];
  const foreground = safeCssColor(style?.foreground || (style?.inverse ? frame.terminalBackground : ""));
  const background = safeCssColor(style?.background || (style?.inverse ? frame.terminalForeground : ""));
  if (foreground) rules.push(`color:${foreground}`);
  if (background) rules.push(`background-color:${background}`);
  if (style?.bold) rules.push("font-weight:700");
  if (style?.faint) rules.push("opacity:.65");
  if (style?.italic) rules.push("font-style:italic");
  const decorations = [];
  if (style?.underline) decorations.push("underline");
  if (style?.strikethrough) decorations.push("line-through");
  if (style?.overline) decorations.push("overline");
  if (decorations.length > 0) rules.push(`text-decoration:${decorations.join(" ")}`);
  if (style?.invisible) rules.push("color:transparent");
  if (isCursor) {
    const cursorColor = safeCssColor(frame.terminalCursorColor);
    if (cursorColor) rules.push(`--terminal-cursor-color:${cursorColor}`);
  }
  return rules.join(";");
}

function safeCssColor(value) {
  const color = String(value || "").trim();
  if (/^#[0-9a-fA-F]{3,8}$/u.test(color)) return color;
  if (/^rgba?\(\s*[\d.]+%?\s*,\s*[\d.]+%?\s*,\s*[\d.]+%?(?:\s*,\s*(?:[\d.]+|0?\.\d+))?\s*\)$/u.test(color)) return color;
  return "";
}

function clampInteger(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isInteger(number)) return fallback;
  return Math.max(min, Math.min(max, number));
}

function renderGridToText(renderGrid) {
  const rowSpans = renderGrid.row_spans || renderGrid.rowSpans;
  if (Array.isArray(rowSpans)) {
    const rowCount = Number.isInteger(renderGrid.rows) ? renderGrid.rows : 0;
    const rows = Array.from({ length: rowCount }, () => "");
    const spans = [...rowSpans].sort((left, right) => {
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
  if (method === "mobile.workspace.list" || method === "mobile.terminal.create" || method === "workspace.create") {
    state.workspaceRefreshPending = false;
    const wasTerminalVisible = !elements.terminalView.classList.contains("hidden");
    state.workspaces = result.workspaces || [];
    state.groups = result.groups || [];
    renderWorkspaces();
    const activeTerminalStillPresent = refreshActiveTerminalFromWorkspaces();
    if (wasTerminalVisible && activeTerminalStillPresent && !result.created_workspace_id && !result.created_terminal_id) {
      showScreen("terminal");
    } else {
      showScreen("workspaces");
    }
    if (result.created_workspace_id && !result.created_terminal_id) {
      const workspace = state.workspaces.find((item) => item.id === result.created_workspace_id);
      const terminal = workspace?.terminals?.[0];
      if (workspace && terminal) openTerminal(workspace.id, terminal.id);
    } else if (result.created_terminal_id) {
      const workspaceId = result.created_workspace_id || workspaceIdForTerminal(result.created_terminal_id);
      if (workspaceId) openTerminal(workspaceId, result.created_terminal_id);
    }
    return;
  }
  if (method === "workspace.action" || method === "workspace.close") {
    bridge().refreshWorkspaces();
    return;
  }
  if (method === "workspace.group.collapse" || method === "workspace.group.expand") {
    bridge().refreshWorkspaces();
    return;
  }
  if (method === "notification.reconcile") {
    if (Number.isInteger(result.unread_count)) {
      state.authoritativeUnreadNotificationCount = result.unread_count;
    }
    const handledIds = Array.isArray(result.handled_ids) ? result.handled_ids : [];
    if (handledIds.length > 0) {
      const handled = new Set(handledIds);
      state.deliveredNotificationIds = state.deliveredNotificationIds.filter((id) => !handled.has(id));
    }
    renderNotificationStatus();
    showToast(t("notification.synced"));
    bridge().refreshWorkspaces();
    return;
  }
  if (method === "notification.dismiss") {
    state.deliveredNotificationIds = [];
    renderNotificationStatus();
    showToast(t("notification.dismissed"));
    syncNotifications();
    bridge().refreshWorkspaces();
    return;
  }
  if (method === "mobile.terminal.replay") {
    renderTerminalReplay(result);
    return;
  }
  if (method === "mobile.terminal.input" || method === "mobile.terminal.paste") {
    window.setTimeout(replayActiveTerminal, 180);
    return;
  }
  if (method === "mobile.terminal.paste_image") {
    window.setTimeout(replayActiveTerminal, 180);
    return;
  }
  if (method === "mobile.terminal.viewport") {
    if (Number.isInteger(result.columns) && Number.isInteger(result.rows)) {
      state.effectiveViewport = { columns: result.columns, rows: result.rows };
    }
    return;
  }
  if (method === "mobile.terminal.scroll") {
    if (result.render_grid) {
      renderTerminalFrame(result.render_grid);
    }
    return;
  }
  if (method === "mobile.terminal.mouse") {
    return;
  }
  if (method === "mobile.events.subscribe") {
    return;
  }
}

function workspaceIdForTerminal(terminalId) {
  return state.workspaces.find((workspace) => {
    return (workspace.terminals || []).some((terminal) => terminal.id === terminalId);
  })?.id || "";
}

function handlePushEvent(type, payload) {
  if (type === "workspace.updated") {
    refreshWorkspacesOnce();
    return;
  }
  if (type === "terminal.render_grid") {
    const renderGrid = payload.render_grid || payload;
    const surfaceId = renderGrid.surface_id || renderGrid.surfaceID || renderGrid.surfaceId || payload.surface_id || payload.surfaceID;
    if (state.activeTerminal && surfaceId === state.activeTerminal.id) {
      renderTerminalFrame(renderGrid);
      showToast(t("terminal.live"));
    }
    return;
  }
  if (type === "terminal.set_font") {
    handleTerminalSetFont(payload);
    return;
  }
  if (type === "notification.badge") {
    if (Number.isInteger(payload.unread_count)) {
      state.authoritativeUnreadNotificationCount = payload.unread_count;
      renderNotificationStatus();
    }
    return;
  }
  if (type === "notification.dismissed") {
    const ids = Array.isArray(payload.ids) ? payload.ids : [];
    if (ids.length > 0) {
      const dismissed = new Set(ids);
      state.deliveredNotificationIds = state.deliveredNotificationIds.filter((id) => !dismissed.has(id));
    }
    if (Number.isInteger(payload.unread_count)) {
      state.authoritativeUnreadNotificationCount = payload.unread_count;
    }
    renderNotificationStatus();
    bridge().refreshWorkspaces();
  }
}

function refreshWorkspacesOnce() {
  if (state.workspaceRefreshPending) return;
  state.workspaceRefreshPending = true;
  bridge().refreshWorkspaces();
}

window.cmuxNativeEvent = (event) => {
  if (event.type === "pairedMacs") {
    state.macs = event.payload.macs || [];
    renderPairedMacs();
    return;
  }
  if (event.type === "auth") {
    state.stackAccessTokenConfigured = event.payload.stack_access_token_configured === true;
    state.stackRefreshTokenConfigured = event.payload.stack_refresh_token_configured === true;
    renderAuthStatus();
    return;
  }
  if (event.type === "notificationPermission") {
    state.nativeNotificationsEnabled = event.payload.enabled === true;
    state.nativeNotificationsCanRequest = event.payload.can_request === true;
    renderNotificationStatus();
    if (event.payload.requested === true) {
      showToast(t(state.nativeNotificationsEnabled ? "notification.enabled" : "notification.denied"));
    }
    return;
  }
  if (event.type === "connection") {
    const { state: nextState, detail } = event.payload;
    state.connected = nextState === "open";
    if (!state.connected) {
      state.workspaceRefreshPending = false;
      state.inferredUnreadNotificationCount = 0;
      state.authoritativeUnreadNotificationCount = null;
      state.deliveredNotificationIds = [];
      if (nextState === "closed") {
        clearActiveTerminalState();
        showScreen("workspaces");
      }
      renderNotificationStatus();
    }
    renderConnectionControls();
    renderWorkspaces();
    elements.connectionText.textContent = detail ? `${nextState}: ${detail}` : nextState;
    showToast(nextState === "open" ? t("app.connected") : detail || nextState);
    return;
  }
  if (event.type === "rpcResult") {
    handleRpcResult(event.payload.method, event.payload.result || {});
    return;
  }
  if (event.type === "push") {
    handlePushEvent(event.payload.type, event.payload.payload || {});
    return;
  }
  if (event.type === "toast") {
    showToast(t(event.payload.message_key) || event.payload.message || "");
    return;
  }
  if (event.type === "rpcError" || event.type === "error") {
    if (event.payload.method === "mobile.workspace.list") {
      state.workspaceRefreshPending = false;
    }
    if (event.payload.code === "unauthorized") {
      showToast(t("auth.error.unauthorized"));
    } else if (event.payload.code === "account_mismatch") {
      showToast(t("auth.error.accountMismatch"));
    } else {
      showToast(event.payload.message || t(event.payload.message_key) || t("request.failed"));
    }
  }
};

elements.pairButton.addEventListener("click", () => {
  bridge().pair(elements.pairingCode.value);
});
elements.scanPairingCode.addEventListener("click", () => {
  bridge().scanPairingCode();
});
elements.startStackSignIn.addEventListener("click", () => {
  bridge().startStackSignIn();
});
elements.saveStackAccessToken.addEventListener("click", () => {
  bridge().saveStackAccessToken(elements.stackAccessToken.value);
  elements.stackAccessToken.value = "";
});
elements.clearStackAccessToken.addEventListener("click", () => {
  bridge().clearStackAccessToken();
  elements.stackAccessToken.value = "";
});

elements.pairedList.addEventListener("click", (event) => {
  const connectId = event.target.getAttribute("data-connect");
  const forgetId = event.target.getAttribute("data-forget");
  if (connectId) bridge().connect(connectId);
  if (forgetId) bridge().forget(forgetId);
});

elements.workspaceList.addEventListener("click", (event) => {
  if (!state.connected) return;
  const terminalId = event.target.getAttribute("data-terminal-id");
  const workspaceId = event.target.getAttribute("data-open-terminal");
  const createWorkspaceId = event.target.getAttribute("data-create-terminal");
  const renameWorkspaceId = event.target.getAttribute("data-rename-workspace");
  const pinWorkspaceId = event.target.getAttribute("data-pin-workspace");
  const readWorkspaceId = event.target.getAttribute("data-read-workspace");
  const closeWorkspaceId = event.target.getAttribute("data-close-workspace");
  const toggleGroupId = event.target.getAttribute("data-toggle-group");
  if (workspaceId && terminalId) openTerminal(workspaceId, terminalId);
  if (createWorkspaceId) bridge().createTerminal(createWorkspaceId);
  if (renameWorkspaceId) renameWorkspace(renameWorkspaceId);
  if (pinWorkspaceId) toggleWorkspacePinned(pinWorkspaceId, event.target.getAttribute("data-pinned") === "true");
  if (readWorkspaceId) toggleWorkspaceUnread(readWorkspaceId, event.target.getAttribute("data-unread") === "true");
  if (closeWorkspaceId) closeWorkspace(closeWorkspaceId);
  if (toggleGroupId) toggleWorkspaceGroup(toggleGroupId, event.target.getAttribute("data-collapsed") === "true");
});

elements.refreshWorkspaces.addEventListener("click", () => bridge().refreshWorkspaces());
elements.createWorkspace.addEventListener("click", () => bridge().createWorkspace());
elements.showPairedMacs.addEventListener("click", () => showScreen("pairing"));
elements.backToWorkspacesFromPairing.addEventListener("click", () => showScreen("workspaces"));
elements.workspaceFilters.addEventListener("click", (event) => {
  const button = event.target.closest("[data-workspace-filter]");
  const nextFilter = button?.getAttribute("data-workspace-filter");
  if (!nextFilter || nextFilter === state.workspaceFilter) return;
  state.workspaceFilter = nextFilter;
  renderWorkspaces();
});
elements.workspaceSearch.addEventListener("input", () => {
  state.workspaceSearch = elements.workspaceSearch.value;
  renderWorkspaces();
});
elements.enableNotifications.addEventListener("click", () => bridge().requestNotificationPermission());
elements.syncNotifications.addEventListener("click", syncNotifications);
elements.dismissNotifications.addEventListener("click", dismissSyncedNotifications);
elements.closeConnection.addEventListener("click", () => {
  if (state.activeWorkspace && state.activeTerminal) {
    bridge().clearViewport(state.activeWorkspace.id, state.activeTerminal.id);
  }
  bridge().closeConnection();
});
elements.backToWorkspaces.addEventListener("click", closeActiveTerminal);
elements.copyTerminalOutput.addEventListener("click", copyTerminalOutput);
elements.refreshTerminal.addEventListener("click", replayActiveTerminal);
elements.scrollUp.addEventListener("click", () => queueTerminalScroll(-terminalRows()));
elements.scrollDown.addEventListener("click", () => queueTerminalScroll(terminalRows()));
elements.terminalKeybar.addEventListener("click", (event) => {
  const key = event.target.getAttribute("data-terminal-key");
  if (key) sendTerminalKey(key);
});
elements.sendInput.addEventListener("click", () => sendTerminalInput("input"));
elements.pasteInput.addEventListener("click", () => sendTerminalInput("paste"));
elements.terminalInput.addEventListener("keydown", handleTerminalInputKeydown);
elements.clearTerminalInput.addEventListener("click", clearTerminalInput);
elements.pasteImage.addEventListener("click", chooseImageForPaste);
elements.imageInput.addEventListener("change", pasteSelectedImage);
window.addEventListener("resize", scheduleViewportReport);
elements.terminalOutput.addEventListener("wheel", handleTerminalWheel, { passive: false });
elements.terminalOutput.addEventListener("click", handleTerminalClick);
elements.terminalOutput.addEventListener("touchstart", handleTerminalTouchStart, { passive: true });
elements.terminalOutput.addEventListener("touchmove", handleTerminalTouchMove, { passive: false });
elements.terminalOutput.addEventListener("touchend", handleTerminalTouchEnd);
elements.terminalOutput.addEventListener("touchcancel", handleTerminalTouchEnd);

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

const MAX_IMAGE_BYTES = 8 * 1024 * 1024;

localizeStaticText();
bridge().initialState();
