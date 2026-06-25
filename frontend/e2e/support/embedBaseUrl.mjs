const DEFAULT_EMBED_BASE_URL = "http://localhost:5173";

export function resolveEmbedAppBase(baseUrl = DEFAULT_EMBED_BASE_URL) {
  const normalized = normalizeBaseUrl(baseUrl).replace(/\/+$/, "");
  const parsed = new URL(normalized);
  if (parsed.pathname.replace(/\/+$/, "") === "/medkernel") {
    return parsed.origin;
  }
  return normalized;
}

export function resolveEmbedOrigin(baseUrl = DEFAULT_EMBED_BASE_URL) {
  return new URL(resolveEmbedAppBase(baseUrl)).origin;
}

function normalizeBaseUrl(baseUrl) {
  const value = String(baseUrl || DEFAULT_EMBED_BASE_URL).trim();
  return value || DEFAULT_EMBED_BASE_URL;
}
