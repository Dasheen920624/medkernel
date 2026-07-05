const IDEMPOTENCY_KEY_MAX_LENGTH = 128;

export function buildStableIdempotencyKey(prefix: string, readableRef: string, ...parts: string[]) {
  const safePrefix = normalizeKeyPart(prefix).slice(0, 32) || "idem";
  const safeRef = normalizeKeyPart(readableRef).slice(0, 64);
  const digest = stableDigest([prefix, readableRef, ...parts].join("\u001f"));
  return [safePrefix, safeRef, digest]
    .filter(Boolean)
    .join("-")
    .slice(0, IDEMPOTENCY_KEY_MAX_LENGTH);
}

function normalizeKeyPart(value: string) {
  return value
    .trim()
    .replace(/[^A-Za-z0-9._:-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

function stableDigest(value: string) {
  const first = fnv1a32(value, 0x811c9dc5);
  const second = fnv1a32(value, 0x9e3779b9);
  return `${first.toString(36).padStart(7, "0")}${second.toString(36).padStart(7, "0")}`;
}

function fnv1a32(value: string, seed: number) {
  let hash = seed;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}
