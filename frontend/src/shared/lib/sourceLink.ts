export const SOURCE_LINK_UNAVAILABLE_TEXT = "来源暂不可跳转";
export const SOURCE_LINK_MISSING_TEXT = "来源未提供跳转";
export const SOURCE_TRACE_MISSING_TEXT = "追踪链路未提供";

function hasUnsafeLinkCharacter(value: string) {
  for (const char of value) {
    const code = char.charCodeAt(0);
    if (char === "\\" || char.trim() === "" || code < 32 || code === 127) return true;
  }
  return false;
}

export function resolveSourceDeepLink(deepLink?: string | null): string | null {
  const value = deepLink?.trim();
  if (!value) return null;
  if (!value.startsWith("/") || value.startsWith("//")) return null;
  if (hasUnsafeLinkCharacter(value)) return null;
  return value;
}
