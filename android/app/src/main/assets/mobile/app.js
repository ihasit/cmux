const logElement = document.getElementById("log");
const hostInput = document.getElementById("host");
const portInput = document.getElementById("port");

let nextID = 1;

function log(message, value) {
  const time = new Date().toLocaleTimeString();
  const suffix = value === undefined ? "" : `\n${JSON.stringify(value, null, 2)}`;
  logElement.textContent = `[${time}] ${message}${suffix}\n\n${logElement.textContent}`;
}

function nativeBridge() {
  if (!window.cmuxAndroid) {
    throw new Error("Android bridge is unavailable");
  }
  return window.cmuxAndroid;
}

function connect() {
  const host = hostInput.value.trim();
  const port = Number.parseInt(portInput.value, 10);
  if (!host || !Number.isInteger(port)) {
    log("Enter a valid host and port");
    return;
  }
  nativeBridge().connect(host, port);
  log(`Connecting to ${host}:${port}`);
}

function sendRPC(method, params = {}) {
  const request = {
    id: nextID++,
    method,
    params,
  };
  nativeBridge().send(JSON.stringify(request));
  log(`Sent ${method}`, request);
}

window.cmuxNativeEvent = (event) => {
  if (event.type === "frame") {
    try {
      log("Received frame", JSON.parse(event.payload.payload));
    } catch {
      log("Received frame", event.payload.payload);
    }
    return;
  }
  log(`Native ${event.type}`, event.payload);
};

document.getElementById("connect").addEventListener("click", connect);
document.getElementById("status").addEventListener("click", () => {
  sendRPC("mobile.host.status");
});
document.getElementById("close").addEventListener("click", () => {
  nativeBridge().close();
});
