const AUTH_SESSION_EVENT_KEY = "medkernel.auth.event";
const ALLOWED_EXACT_KEYS = new Set([
  "medkernel.theme.mode",
  "medkernel.evidence-details.enabled",
  AUTH_SESSION_EVENT_KEY,
]);
const SENSITIVE_KEY_PATTERN =
  /(token|secret|password|passwd|api[-_.]?key|authorization|credential|patient|idcard|identity|身份证|患者)/i;

function getStorage(): Storage | null {
  if (typeof window === "undefined") return null;
  return window.localStorage;
}

function assertUiPreferenceKey(key: string) {
  if (SENSITIVE_KEY_PATTERN.test(key)) {
    throw new Error(`敏感键禁止写入浏览器本地存储：${key}`);
  }

  if (!ALLOWED_EXACT_KEYS.has(key)) {
    throw new Error(`只允许写入已批准的 UI 偏好键：${key}`);
  }
}

export function readUiPreference(key: string): string | null {
  assertUiPreferenceKey(key);
  return getStorage()?.getItem(key) ?? null;
}

export function writeUiPreference(key: string, value: string) {
  assertUiPreferenceKey(key);
  getStorage()?.setItem(key, value);
}

export function writeAuthSessionEvent(value: string) {
  assertUiPreferenceKey(AUTH_SESSION_EVENT_KEY);
  getStorage()?.setItem(AUTH_SESSION_EVENT_KEY, value);
}

export { AUTH_SESSION_EVENT_KEY };
