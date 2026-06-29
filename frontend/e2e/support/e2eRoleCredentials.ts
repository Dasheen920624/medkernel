export const ROLE_ACCOUNT_CODES = [
  "platform-admin",
  "engine-operator",
  "clinical-user",
  "auditor",
] as const;

export type RoleAccountCode = (typeof ROLE_ACCOUNT_CODES)[number];

export type RoleCredentialOverride = {
  tenantId: string;
  username: string;
  role: RoleAccountCode;
  password: string;
};

export type RoleCredentialOverrides = Record<RoleAccountCode, RoleCredentialOverride>;

type CredentialBlock = {
  tenantId?: unknown;
  accounts?: unknown;
};

export function resolveRoleCredentialOverrides(source: unknown): RoleCredentialOverrides {
  const contract = requireRecord(source, "E2E 上线凭据");
  if (contract.schemaVersion !== "1.0.0" || contract.status !== "READY") {
    throw new Error("E2E 上线凭据必须使用 READY 状态的 1.0.0 契约");
  }

  if ("rehearsal" in contract && contract.rehearsal != null) {
    return parseCredentialBlock("演练机构", contract.rehearsal);
  }
  if ("platform" in contract && contract.platform != null) {
    return parseCredentialBlock("平台治理", contract.platform);
  }
  throw new Error("E2E 上线凭据缺少平台或演练机构四职责账号");
}

function parseCredentialBlock(label: string, block: unknown): RoleCredentialOverrides {
  const record = requireRecord(block, `${label}凭据`);
  const tenantId = requireText((record as CredentialBlock).tenantId, `${label}租户`);
  const accounts = requireRecord((record as CredentialBlock).accounts, `${label}账号`);
  return Object.fromEntries(
    ROLE_ACCOUNT_CODES.map((role) => [role, parseRoleAccount(label, tenantId, role, accounts[role])]),
  ) as RoleCredentialOverrides;
}

function parseRoleAccount(
  label: string,
  fallbackTenantId: string,
  role: RoleAccountCode,
  value: unknown,
): RoleCredentialOverride {
  const account = requireRecord(value, `${label}${role}账号`);
  const tenantId = textOrFallback(account.tenantId, fallbackTenantId);
  const username = requireText(account.username, `${label}${role}用户名`);
  const accountRole = requireText(account.role, `${label}${role}职责`);
  const password = requireText(account.password, `${label}${role}密码`);
  if (accountRole !== role) {
    throw new Error(`${label}${role}账号职责不匹配: ${accountRole}`);
  }
  return { tenantId, username, role, password };
}

function requireRecord(value: unknown, label: string): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label}必须是对象`);
  }
  return value as Record<string, unknown>;
}

function requireText(value: unknown, label: string) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label}不能为空`);
  }
  return value.trim();
}

function textOrFallback(value: unknown, fallback: string) {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}
