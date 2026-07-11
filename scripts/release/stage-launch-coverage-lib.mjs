export function launchCoverageClaims(entries, observedAt) {
  if (!hasText(observedAt)) throw new Error("覆盖证据 observedAt 不能为空");
  const claims = {};
  for (const entry of entries) {
    if (!Array.isArray(entry) || entry.length !== 2) {
      throw new Error("覆盖证据项必须为 [coverageKey, code]");
    }
    const [key, code] = entry;
    claims[key] ??= [];
    claims[key].push({
      code,
      status: "PASSED",
      evidenceKey: `launchCoverage.${key}.${code}`,
      observedAt,
    });
  }
  return claims;
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}
