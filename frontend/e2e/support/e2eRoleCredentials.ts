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
export type RoleCredentialScope = "platform" | "rehearsal";
export type ScopedRoleCredentialOverrides = Record<RoleCredentialScope, RoleCredentialOverrides>;

type CredentialBlock = {
  tenantId?: unknown;
  accounts?: unknown;
};

export function resolveLaunchCredentialScopes(source: unknown): ScopedRoleCredentialOverrides {
  const contract = requireRecord(source, "E2E 上线凭据");
  if (contract.schemaVersion !== "1.0.0" || contract.status !== "READY") {
    throw new Error("E2E 上线凭据必须使用 READY 状态的 1.0.0 契约");
  }

  if (
    !contract.platform ||
    typeof contract.platform !== "object" ||
    Array.isArray(contract.platform)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical platform.accounts 四职责账号");
  }
  const platform = contract.platform as CredentialBlock;
  if (
    !platform.accounts ||
    typeof platform.accounts !== "object" ||
    Array.isArray(platform.accounts)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical platform.accounts 四职责账号");
  }
  if (
    !contract.rehearsal ||
    typeof contract.rehearsal !== "object" ||
    Array.isArray(contract.rehearsal)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical rehearsal.accounts 机构四职责账号");
  }
  const rehearsal = contract.rehearsal as CredentialBlock;
  if (
    !rehearsal.accounts ||
    typeof rehearsal.accounts !== "object" ||
    Array.isArray(rehearsal.accounts)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical rehearsal.accounts 机构四职责账号");
  }
  return {
    platform: parseCredentialBlock("平台治理", platform),
    rehearsal: parseCredentialBlock("上线机构", rehearsal),
  };
}

export function resolveRoleCredentialOverrides(
  source: unknown,
  scope: RoleCredentialScope = "rehearsal",
): RoleCredentialOverrides {
  return resolveLaunchCredentialScopes(source)[scope];
}

function parseCredentialBlock(label: string, block: unknown): RoleCredentialOverrides {
  const record = requireRecord(block, `${label}凭据`);
  const tenantId = requireText((record as CredentialBlock).tenantId, `${label}租户`);
  const accounts = requireRecord((record as CredentialBlock).accounts, `${label}账号`);
  return Object.fromEntries(
    ROLE_ACCOUNT_CODES.map((role) => [
      role,
      parseRoleAccount(label, tenantId, role, accounts[role]),
    ]),
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
