import { AUTH_SESSION_EVENT_KEY, writeAuthSessionEvent } from "@/shared/lib/browserStorage";

export const AUTH_SESSION_EVENT_STORAGE_KEY = AUTH_SESSION_EVENT_KEY;

export type AuthSessionEventReason = "logout" | "expired";

type AuthSessionEventPayload = {
  reason: AuthSessionEventReason;
  at: number;
  nonce: string;
};

let fallbackNonce = 0;

function createNonce() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  fallbackNonce += 1;
  return `${Date.now()}-${fallbackNonce}`;
}

export function broadcastAuthSessionEvent(reason: AuthSessionEventReason) {
  const payload: AuthSessionEventPayload = {
    reason,
    at: Date.now(),
    nonce: createNonce(),
  };
  writeAuthSessionEvent(JSON.stringify(payload));
}

export function subscribeAuthSessionEvent(handler: (reason: AuthSessionEventReason) => void) {
  if (typeof window === "undefined") {
    return () => {};
  }
  const onStorage = (event: StorageEvent) => {
    if (event.key !== AUTH_SESSION_EVENT_STORAGE_KEY || !event.newValue) {
      return;
    }
    try {
      const payload = JSON.parse(event.newValue) as Partial<AuthSessionEventPayload>;
      if (payload.reason === "logout" || payload.reason === "expired") {
        handler(payload.reason);
      }
    } catch {
      handler("expired");
    }
  };
  window.addEventListener("storage", onStorage);
  return () => window.removeEventListener("storage", onStorage);
}
