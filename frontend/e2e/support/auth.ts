import { expect, type APIResponse, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";
import { readFileSync } from "node:fs";

import {
  ROLE_ACCOUNT_CODES,
  resolveLaunchCredentialScopes,
  type RoleAccountCode,
  type RoleCredentialScope,
  type ScopedRoleCredentialOverrides,
} from "./e2eRoleCredentials";

export const apiBase = requireEnv("E2E_API_BASE_URL");
export const appBase = (process.env.E2E_BASE_URL?.trim() || "http://localhost:5173").replace(
  /\/+$/,
  "",
);
const frontendApiBase = resolveFrontendApiBase(appBase);
export const tenantId = "t-1";
const platformTenantId = tenantId;
const defaultPassword = "Mk@2026dev";
const localInitialPassword = "Mk@2026localinit";
const localRehearsalTenantId =
  process.env.E2E_LOCAL_REHEARSAL_TENANT_ID?.trim() || "t-e2e-rehearsal-local";
const localRehearsalTenantName = "本地上线演练服务机构";
const localRehearsalAdminUsername = "e2e-rehearsal-admin";
const localRehearsalAdminPassword = "Mk@2026localadmin";
const localRehearsalHospitalCode = "e2e-rehearsal-hospital";
const localRehearsalHospitalName = "本地上线演练医院";
export const roleAccounts = ROLE_ACCOUNT_CODES;
const defaultCredentialScope: RoleCredentialScope = "rehearsal";
const platformDiagnosticItemKnowledgeIdentity = "plat:diagnostic_item:lab-potassium";
const requiredRuntimeAssets = [
  { assetType: "FIELD_CATALOG" },
  { assetType: "RULE" },
  { assetType: "KNOWLEDGE", assetIdentity: platformDiagnosticItemKnowledgeIdentity },
] as const;
const requiredRuntimeAssetTypes = ["FIELD_CATALOG", "RULE", "KNOWLEDGE"] as const;

export type RoleAccount = RoleAccountCode;
type RuntimeAssetSelection = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};
type RuntimeAssetCandidate = {
  assetType: string;
  assetIdentity: string;
  versionId: string;
};
type BaselineRuntimeAssets = {
  baselineReleaseId: string | null;
  activeAssets: RuntimeAssetSelection[];
};

const credentialsConfigured = Boolean(process.env.E2E_ROLE_CREDENTIALS_FILE?.trim());
const credentialOverrides = loadCredentialOverrides();
let localRehearsalReadyPromise: Promise<void> | null = null;

export async function ensureReadySession(
  page: Page,
  role: RoleAccount,
  scope: RoleCredentialScope = defaultCredentialScope,
) {
  await ensureLocalRehearsalReady(page, scope);
  await resetRoleSession(page);
  const password = stablePassword(role, scope);
  const username = usernameFor(role, scope);
  let currentPassword = password;
  let login = await loginWith(page, username, password, scope);
  if (!login.ok() && !credentialsConfigured) {
    for (const fallbackPassword of fallbackPasswordsFor(scope)) {
      currentPassword = fallbackPassword;
      login = await loginWith(page, username, fallbackPassword, scope);
      if (login.ok()) break;
    }
  }
  await expectOk(login, `${role} 登录`);
  let result = (await login.json()).data;

  if (process.env.E2E_EXPECT_MFA_DISABLED === "1") {
    expect(result.mfaRequired, `${role} 上线默认 MFA 必须关闭`).toBe(false);
  }

  if (result.mustChangePwd) {
    const change = await postApi(page, "/auth/change-password", {
      oldPassword: currentPassword,
      newPassword: password,
    });
    if (!change.ok() && !credentialsConfigured) {
      let retry: APIResponse | null = null;
      for (const fallbackPassword of fallbackPasswordsFor(scope)) {
        retry = await postApi(page, "/auth/change-password", {
          oldPassword: fallbackPassword,
          newPassword: password,
        });
        if (retry.ok()) break;
      }
      await expectOk(retry ?? change, `${role} 首次改密`);
    } else {
      await expectOk(change, `${role} 首次改密`);
    }
    const relogin = await loginWith(page, username, password, scope);
    await expectOk(relogin, `${role} 改密后重新登录`);
    result = (await relogin.json()).data;
    if (process.env.E2E_EXPECT_MFA_DISABLED === "1") {
      expect(result.mfaRequired, `${role} 改密后默认 MFA 必须关闭`).toBe(false);
    }
  }

  if (result.mfaRequired && !result.mfaBound) {
    const setupResponse = await postApi(page, "/auth/mfa/bind", { label: `${role} 安全设备` });
    await expectOk(setupResponse, `${role} 生成 MFA 密钥`);
    const setup = (await setupResponse.json()).data;
    const verifyResponse = await postApi(page, "/auth/mfa/bind", {
      label: `${role} 安全设备`,
      secret: setup.secret,
      code: totp(setup.secret),
    });
    await expectOk(verifyResponse, `${role} 验证 MFA`);
    const relogin = await loginWith(page, username, password, scope);
    await expectOk(relogin, `${role} MFA 后重新登录`);
  }
  await loginWithFrontend(page, role, password, scope);
  const frontendProfile = await page.request.get(`${frontendApiBase}/security/me`, {
    headers: { "X-Trace-Id": `e2e-front-profile-${role}-${Date.now()}` },
  });
  await expectOk(frontendProfile, `${role} 前台会话画像`);
  const profile = (await frontendProfile.json()).data as {
    roles: Array<{ code: string }>;
    menuKeys: string[];
  };
  expect(profile.roles.map((item) => item.code)).toContain(role);
  expect(profile.menuKeys).toContain("workbench");
  await reloadFrontendSession(page, role);
}

export async function loginFromPlatformPage(page: Page, role: RoleAccount) {
  await page.context().clearCookies();
  await page.goto(appPath("/login"));
  await expectLoginPageReady(page);
  const scope: RoleCredentialScope = "platform";

  const platformTenantSwitch = page
    .locator('[aria-label="登录类型切换"]')
    .getByRole("button", { name: "平台治理", exact: true });
  const platformHeading = page.getByRole("heading", { name: "登录平台治理" });
  await expect(platformTenantSwitch.or(platformHeading).first()).toBeVisible();
  if (await platformTenantSwitch.isVisible()) {
    await platformTenantSwitch.click();
    await expect(platformTenantSwitch).toHaveAttribute("aria-pressed", "true");
  }
  await expect(page.getByRole("heading", { name: "登录平台治理" })).toBeVisible();

  await page.getByLabel("工号 / 账号").fill(usernameFor(role, scope));
  await page.getByLabel("密码").fill(stablePassword(role, scope));
  await page.getByRole("button", { name: "进入工作台" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

export async function expectLoginPageReady(page: Page) {
  await expect(page.getByRole("main", { name: "登录 MedKernel 工作台" })).toBeVisible();
  await expect(page.getByRole("heading", { name: /登录(?:平台治理|机构工作台)/ })).toBeVisible();
}

export async function loginWith(
  page: Page,
  username: string,
  password: string,
  scope: RoleCredentialScope = defaultCredentialScope,
) {
  return postApi(page, "/auth/login", {
    username,
    password,
    tenantId: tenantIdFor(username, scope),
  });
}

async function loginWithFrontend(
  page: Page,
  role: RoleAccount,
  password: string,
  scope: RoleCredentialScope,
) {
  const username = usernameFor(role, scope);
  const response = await page.request.post(`${frontendApiBase}/auth/login`, {
    data: { username, password, tenantId: tenantIdFor(role, scope) },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-front-login-${role}-${Date.now()}`,
    },
  });
  await expectOk(response, `${role} 前台代理登录`);
  await mirrorSecureCookiesForLocalProxy(page, response);
}

async function resetRoleSession(page: Page) {
  await Promise.allSettled([
    page.request.post(`${frontendApiBase}/auth/logout`, {
      headers: { "X-Trace-Id": `e2e-front-logout-${Date.now()}` },
    }),
    page.request.post(`${apiBase}/auth/logout`, {
      headers: { "X-Trace-Id": `e2e-api-logout-${Date.now()}` },
    }),
  ]);
  await page.context().clearCookies();
  await page.goto(appPath(`/login?e2e-session-reset=${Date.now()}`), {
    waitUntil: "domcontentloaded",
  });
}

async function reloadFrontendSession(page: Page, role: RoleAccount) {
  await page.goto(appPath(`/dashboard?e2e-session-refresh=${role}-${Date.now()}`), {
    waitUntil: "domcontentloaded",
  });
  await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible({
    timeout: 20_000,
  });
}

async function ensureLocalRehearsalReady(page: Page, scope: RoleCredentialScope) {
  if (credentialsConfigured || scope !== "rehearsal") {
    return;
  }
  localRehearsalReadyPromise ??= bootstrapLocalRehearsal(page).catch((error) => {
    localRehearsalReadyPromise = null;
    throw error;
  });
  await localRehearsalReadyPromise;
}

async function bootstrapLocalRehearsal(page: Page) {
  await ensureApiRoleSession(page, "platform-admin", "platform");
  await ensureLocalTenant(page);

  await ensureApiLoginSession(page, {
    username: localRehearsalAdminUsername,
    tenantId: localRehearsalTenantId,
    preferredPassword: localRehearsalAdminPassword,
    fallbackPasswords: [localInitialPassword, defaultPassword],
    finalPassword: localRehearsalAdminPassword,
    label: "本地上线演练管理员",
  });
  const hospital = await ensureLocalHospital(page);
  for (const role of ROLE_ACCOUNT_CODES) {
    await ensureLocalRoleAccount(page, role, hospital.id);
  }

  const baseline = await ensurePlatformBaseline(page);
  await ensureApiRoleSession(page, "engine-operator", "rehearsal");
  await ensureHospitalRuntime(page, hospital.id, baseline);
}

async function ensureApiRoleSession(page: Page, role: RoleAccount, scope: RoleCredentialScope) {
  return ensureApiLoginSession(page, {
    username: usernameFor(role, scope),
    tenantId: resolvedTenantIdFor(role, scope),
    preferredPassword: stablePassword(role, scope),
    fallbackPasswords: credentialsConfigured ? [] : fallbackPasswordsFor(scope),
    finalPassword: stablePassword(role, scope),
    label: `${role} API 会话`,
  });
}

async function ensureApiLoginSession(
  page: Page,
  options: {
    username: string;
    tenantId: string;
    preferredPassword: string;
    fallbackPasswords: string[];
    finalPassword: string;
    label: string;
  },
) {
  await page.context().clearCookies();
  const passwords = [
    options.preferredPassword,
    ...options.fallbackPasswords.filter((password) => password !== options.preferredPassword),
  ];
  let currentPassword = passwords[0];
  let login: APIResponse | null = null;
  for (const password of passwords) {
    currentPassword = password;
    login = await apiLogin(page, options.username, password, options.tenantId);
    if (login.ok()) break;
  }
  if (!login) {
    throw new Error(`${options.label} 缺少可尝试密码`);
  }
  await expectOk(login, `${options.label} 登录`);
  let result = (await login.json()).data as {
    mustChangePwd?: boolean;
    mfaRequired?: boolean;
    mfaBound?: boolean;
  };

  if (result.mustChangePwd) {
    const change = await postApi(page, "/auth/change-password", {
      oldPassword: currentPassword,
      newPassword: options.finalPassword,
    });
    await expectOk(change, `${options.label} 首次改密`);
    const relogin = await apiLogin(page, options.username, options.finalPassword, options.tenantId);
    await expectOk(relogin, `${options.label} 改密后重新登录`);
    result = (await relogin.json()).data;
  }

  if (result.mfaRequired && !result.mfaBound) {
    const setupResponse = await postApi(page, "/auth/mfa/bind", {
      label: `${options.label} 安全设备`,
    });
    await expectOk(setupResponse, `${options.label} 生成 MFA 密钥`);
    const setup = (await setupResponse.json()).data;
    const verifyResponse = await postApi(page, "/auth/mfa/bind", {
      label: `${options.label} 安全设备`,
      secret: setup.secret,
      code: totp(setup.secret),
    });
    await expectOk(verifyResponse, `${options.label} 验证 MFA`);
    const relogin = await apiLogin(page, options.username, options.finalPassword, options.tenantId);
    await expectOk(relogin, `${options.label} MFA 后重新登录`);
  }
}

async function apiLogin(page: Page, username: string, password: string, sessionTenantId: string) {
  return page.request.post(`${apiBase}/auth/login`, {
    data: { username, password, tenantId: sessionTenantId },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-api-login-${username}-${Date.now()}`,
    },
  });
}

async function ensureLocalTenant(page: Page) {
  const response = await getApi(page, "/admin/tenants");
  await expectOk(response, "读取本地上线演练租户台账");
  const tenants = arrayData(await responseData(response));
  if (tenants.some((tenant) => textField(tenant, "tenantId") === localRehearsalTenantId)) {
    return;
  }
  const created = await postApi(page, "/admin/tenants", {
    tenantId: localRehearsalTenantId,
    tenantName: localRehearsalTenantName,
    adminUsername: localRehearsalAdminUsername,
    adminInitialPassword: localInitialPassword,
  });
  if (!created.ok() && created.status() !== 409) {
    await expectOk(created, "开通本地上线演练租户");
  }
}

async function ensureLocalHospital(page: Page) {
  const existing = await getApi(
    page,
    `/engine/org/org-units/${encodeURIComponent(localRehearsalHospitalCode)}`,
  );
  if (existing.ok()) {
    return requireOrgUnit(await responseData(existing), "读取本地上线演练医院");
  }
  if (existing.status() !== 404) {
    await expectOk(existing, "读取本地上线演练医院");
  }

  const rootsResponse = await getApi(page, "/engine/org/org-units/by-level?level=TENANT");
  await expectOk(rootsResponse, "读取本地上线演练租户根组织");
  const root = arrayData(await responseData(rootsResponse)).find(
    (item) =>
      textField(item, "tenantId") === localRehearsalTenantId ||
      textField(item, "code") === localRehearsalTenantId,
  );
  const rootId = textField(root, "id");
  if (!rootId) {
    throw new Error("本地上线演练租户缺少 TENANT 根组织，无法创建医院节点");
  }

  const created = await postApi(page, "/engine/org/org-units", {
    parentId: rootId,
    level: "FACILITY",
    code: localRehearsalHospitalCode,
    name: localRehearsalHospitalName,
    namePinyin: "bendi shangxian yanlian yiyuan",
    facilityType: "HOSPITAL",
    status: "ACTIVE",
  });
  await expectOk(created, "创建本地上线演练医院");
  return requireOrgUnit(await responseData(created), "创建本地上线演练医院");
}

async function ensureLocalRoleAccount(page: Page, role: RoleAccount, hospitalId: string) {
  const detail = await getApi(page, `/compliance/users/${encodeURIComponent(role)}`);
  if (detail.status() === 404) {
    const created = await postApi(page, "/compliance/users", {
      credentialManaged: true,
      userId: role,
      displayName: localRoleDisplayName(role),
      username: usernameFor(role, "rehearsal"),
      initialPassword: localInitialPassword,
    });
    await expectOk(created, `创建本地上线演练账号 ${role}`);
  } else {
    await expectOk(detail, `读取本地上线演练账号 ${role}`);
  }

  const assigned = await postApi(page, `/compliance/users/${encodeURIComponent(role)}/roles`, {
    roleCode: role,
    scopeLevel: "FACILITY",
    scopeCode: hospitalId,
  });
  await expectOk(assigned, `绑定本地上线演练账号 ${role} 医院职责`);
}

async function ensurePlatformBaseline(page: Page): Promise<BaselineRuntimeAssets> {
  await ensureApiRoleSession(page, "engine-operator", "platform");
  const current = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(current, "读取当前平台标准版本");
  const currentBaseline = resolveBaselineRuntimeAssets(await responseData(current));
  if (
    currentBaseline.baselineReleaseId &&
    runtimeAssetsCoverRequiredTypes(currentBaseline.activeAssets)
  ) {
    return currentBaseline;
  }

  const candidatesResponse = await getApi(
    page,
    "/engine/releases/platform-baselines/candidates?page=1&size=100",
  );
  await expectOk(candidatesResponse, "读取平台标准版本候选资产");
  let candidates = resolveRuntimeAssetCandidates(await responseData(candidatesResponse));
  if (!runtimeCandidatesCoverRequiredTypes(candidates)) {
    await ensurePlatformRuntimeAssetCandidates(page, missingRuntimeCandidateTypes(candidates));
    const refreshedCandidatesResponse = await getApi(
      page,
      "/engine/releases/platform-baselines/candidates?page=1&size=100",
    );
    await expectOk(refreshedCandidatesResponse, "重读平台标准版本候选资产");
    candidates = resolveRuntimeAssetCandidates(await responseData(refreshedCandidatesResponse));
  }

  const versionIds = versionIdsForRequiredRuntimeCandidates(candidates);
  if (!runtimeCandidatesCoverRequiredTypes(candidates) || versionIds.length === 0) {
    throw new Error("本地上线演练缺少可发布的平台运行资产，无法准备医院生效版本");
  }

  const published = await postApi(page, "/engine/releases/platform-baselines", {
    publishVersionIds: versionIds,
    disabledAssets: [],
  });
  await expectOk(published, "发布本地上线演练平台标准版本");
  const publishedReleaseId = textField(await responseData(published), "baselineReleaseId");
  if (!publishedReleaseId) {
    throw new Error("平台标准版本发布响应缺少 baselineReleaseId");
  }
  const refreshed = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(refreshed, "重读当前平台标准版本");
  const refreshedBaseline = resolveBaselineRuntimeAssets(await responseData(refreshed));
  if (!refreshedBaseline.baselineReleaseId) {
    throw new Error("平台标准版本发布后仍缺少 baselineReleaseId");
  }
  if (!runtimeAssetsCoverRequiredTypes(refreshedBaseline.activeAssets)) {
    throw new Error(
      "平台标准版本缺少 active FIELD_CATALOG 或 RULE，无法准备本地上线演练医院生效版本",
    );
  }
  return refreshedBaseline;
}

async function ensurePlatformRuntimeAssetCandidates(
  page: Page,
  missingTypes: Array<(typeof requiredRuntimeAssetTypes)[number]>,
) {
  if (missingTypes.includes("FIELD_CATALOG")) {
    const fieldCatalog = await postApi(page, "/engine/context/field-catalog/drafts", {});
    await expectOk(fieldCatalog, "固化本地上线演练字段目录资产");
  }
  if (missingTypes.includes("RULE")) {
    const rule = await postApi(page, "/engine/rule/rules", platformRehearsalRuleRequest());
    await expectOk(rule, "创建本地上线演练平台规则资产");
  }
  if (missingTypes.includes("KNOWLEDGE")) {
    await ensurePlatformDiagnosticItemKnowledgeCandidate(page);
  }
}

export function platformRehearsalRuleRequest() {
  const traceId = `e2e-platform-rule-${Date.now()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: platformTenantId,
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
    triggers: [
      {
        trigger_point: "patient-view",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId"],
      },
      {
        trigger_point: "order-sign",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId", "encounterId", "orders"],
      },
      {
        trigger_point: "medication-prescribe",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId", "encounterId", "medications"],
      },
    ],
    ruleCode: "RULE.LOCAL.REHEARSAL.BASELINE",
    name: "本地上线演练平台基础规则",
    ruleType: "QUALITY",
    authoringMode: "DSL",
    riskLevel: "LOW",
    priority: 100,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:platform-baseline-runtime-assets",
    changeSummary: "清库上线演练自动准备平台基础运行规则",
    dsl: {
      applicability: {
        population: {},
        orgScope: {},
        settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
        effective: {
          rolloutPercent: 100,
        },
      },
      when: {
        all: [{ fact: "patient.age", operator: "gte", value: 0 }],
      },
      then: [
        {
          actionCode: "REMIND",
          atSeverity: "LOW",
          indicator: "info",
          summary: "本地上线演练基础提醒",
          detail: "用于验证平台标准版本、医院生效版本和规则执行链路已启用。",
          source: { label: "MedKernel 本地上线演练" },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: false,
        },
      ],
      explain: {
        title: "本地上线演练基础规则",
        summary: "证明清库环境已具备可发布规则资产。",
      },
    },
    explanation: {
      title: "本地上线演练基础规则",
      summary: "证明清库环境已具备可发布规则资产。",
    },
    parameterBindings: {},
  };
}

async function ensurePlatformDiagnosticItemKnowledgeCandidate(page: Page) {
  const identity = await ensurePlatformDiagnosticItemKnowledgeIdentity(page);
  const identityId = numericField(identity, "id");
  if (!identityId) {
    throw new Error("本地上线演练医技项目说明书身份缺少 id");
  }
  const source = await ensurePlatformDiagnosticItemSource(page);
  const sourceDocumentId = numericField(source, "id");
  if (!sourceDocumentId) {
    throw new Error("本地上线演练医技项目说明书来源缺少 id");
  }
  const sourceVersion = await ensurePlatformDiagnosticItemSourceVersion(page, sourceDocumentId);
  const sourceVersionId = numericField(sourceVersion, "id");
  if (!sourceVersionId) {
    throw new Error("本地上线演练医技项目说明书来源版本缺少 id");
  }
  const fragment = await ensurePlatformDiagnosticItemSourceFragment(page, sourceVersionId);
  const sourceFragmentId = numericField(fragment, "id");
  if (!sourceFragmentId) {
    throw new Error("本地上线演练医技项目说明书来源片段缺少 id");
  }
  const existingVersion = await findPlatformDiagnosticItemKnowledgeVersion(page, identityId);
  const version =
    existingVersion ??
    (await createPlatformDiagnosticItemKnowledgeVersion(
      page,
      identityId,
      sourceDocumentId,
      sourceVersionId,
    ));
  const versionId = numericField(version, "id");
  if (!versionId) {
    throw new Error("本地上线演练医技项目说明书版本缺少 id");
  }
  const citation = await postApi(page, "/engine/knowledge/citations", {
    assetVersionId: versionId,
    sourceFragmentId,
    relation: "DERIVED_FROM",
    weight: 90,
    startOffset: null,
    endOffset: null,
  });
  await expectOk(citation, "绑定本地上线演练医技项目说明书来源引用");
}

async function ensurePlatformDiagnosticItemKnowledgeIdentity(page: Page) {
  const existing = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(
      platformDiagnosticItemKnowledgeIdentity,
    )}`,
  );
  if (existing.ok()) {
    return responseData(existing);
  }
  if (existing.status() !== 404) {
    await expectOk(existing, "读取本地上线演练医技项目说明书身份");
  }
  const created = await postApi(
    page,
    "/engine/knowledge/identities",
    platformDiagnosticItemKnowledgeIdentityRequest(),
  );
  await expectOk(created, "创建本地上线演练医技项目说明书身份");
  return responseData(created);
}

async function ensurePlatformDiagnosticItemSource(page: Page) {
  const created = await postApi(page, "/engine/knowledge/sources", {
    ...platformKnowledgeContext("e2e-knowledge-source"),
    sourceCode: "local-e2e-lab-potassium",
    sourceType: "HOSPITAL_PROTOCOL",
    authorityLevel: "D_HOSPITAL",
    authorityBasis: "本地上线演练内置医技项目说明书，用于清库验证报告解读主链路。",
    title: "本地上线演练血钾检验说明书来源",
    publisher: "MedKernel 本地上线演练",
    license: "内部演练",
    language: "zh-CN",
  });
  await expectOk(created, "登记本地上线演练医技项目说明书来源");
  return responseData(created);
}

async function ensurePlatformDiagnosticItemSourceVersion(page: Page, sourceDocumentId: number) {
  const created = await postApi(page, `/engine/knowledge/sources/${sourceDocumentId}/versions`, {
    ...platformKnowledgeContext("e2e-knowledge-source-version"),
    versionNo: "2026",
    publishedAt: "2026-07-05T00:00:00Z",
    fileUri: "medkernel://local-e2e/diagnostic-item/lab-potassium.md",
    language: "zh-CN",
    content: platformDiagnosticItemKnowledgeContent(),
  });
  await expectOk(created, "登记本地上线演练医技项目说明书来源版本");
  return responseData(created);
}

async function ensurePlatformDiagnosticItemSourceFragment(page: Page, sourceVersionId: number) {
  const created = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId,
    anchorPath: "diagnostic-item/lab-potassium",
    anchorLabel: "血钾检验危急值说明",
    textExcerpt: platformDiagnosticItemKnowledgeContent(),
  });
  await expectOk(created, "登记本地上线演练医技项目说明书来源片段");
  return responseData(created);
}

async function findPlatformDiagnosticItemKnowledgeVersion(page: Page, identityId: number) {
  const versions = await getApi(
    page,
    `/engine/knowledge/identities/${identityId}/versions?page=1&size=100`,
  );
  await expectOk(versions, "读取本地上线演练医技项目说明书版本");
  return (
    pageItems(await responseData(versions)).find((item) => textField(item, "versionNo") === "V1") ??
    null
  );
}

async function createPlatformDiagnosticItemKnowledgeVersion(
  page: Page,
  identityId: number,
  sourceDocumentId: number,
  sourceVersionId: number,
) {
  const created = await postApi(
    page,
    `/engine/knowledge/identities/${identityId}/versions`,
    platformDiagnosticItemKnowledgeVersionRequest(sourceDocumentId, sourceVersionId),
  );
  await expectOk(created, "创建本地上线演练医技项目说明书版本");
  const createdData = await responseData(created);
  const candidate = pageItems(recordField(createdData, "candidates")).find(
    (item) => textField(item, "versionNo") === "V1",
  );
  if (!candidate) {
    throw new Error("本地上线演练医技项目说明书候选响应缺少 V1 版本");
  }
  return candidate;
}

export function platformDiagnosticItemKnowledgeIdentityRequest() {
  return {
    ...platformKnowledgeContext("e2e-knowledge-identity"),
    identitySlug: "lab-potassium",
    domain: "DIAGNOSTIC_ITEM",
    subject: "血钾检验说明书",
    assetSpecialtyId: null,
    description: "用于本地清库上线演练的医技报告解读基础说明书。",
  };
}

export function platformDiagnosticItemKnowledgeVersionRequest(
  sourceDocumentId: number,
  sourceVersionId: number,
) {
  return {
    ...platformKnowledgeContext("e2e-knowledge-version"),
    versionNo: "V1",
    versionLabel: "本地上线演练血钾检验说明书 V1",
    sourceDocumentId,
    sourceVersionId,
    content: platformDiagnosticItemKnowledgeContent(),
    anchors: JSON.stringify([
      {
        anchorPath: "diagnostic-item/lab-potassium",
        label: "血钾检验危急值说明",
      },
    ]),
    riskLevel: "LOW",
    gradeQuality: "LOW",
    gradeStrength: "WEAK",
    reviewCycleMonths: 12,
  };
}

function platformKnowledgeContext(prefix: string) {
  const traceId = `${prefix}-${Date.now()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: platformTenantId,
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function platformDiagnosticItemKnowledgeContent() {
  return [
    "血钾检验说明书。",
    "适用于本地上线演练中的血钾检验报告阅读辅助。",
    "当报告结论包含血钾升高、危急值或 critical 表述时，应提示医师结合症状、既往趋势和原始报告人工复核。",
    "系统不改写已签发报告，不自动开立医嘱。",
  ].join("\n");
}

async function ensureHospitalRuntime(
  page: Page,
  hospitalId: string,
  baseline: BaselineRuntimeAssets,
) {
  if (!baseline.baselineReleaseId) {
    throw new Error("本地上线演练缺少平台标准版本，无法准备医院生效版本");
  }
  if (!runtimeAssetsCoverRequiredTypes(baseline.activeAssets)) {
    throw new Error("平台标准版本缺少 active FIELD_CATALOG 或 RULE，无法激活医院生效版本");
  }
  const path = `/engine/releases/hospitals/${encodeURIComponent(
    hospitalId,
  )}/runtime-releases/current`;
  const current = await getApi(page, path);
  await expectOk(current, "读取本地上线演练医院生效版本");
  const currentRuntime = hospitalRuntimeCoversRequiredAssets(await responseData(current));
  if (currentRuntime.ready) {
    return;
  }

  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases`,
    {
      platformBaselineReleaseId: baseline.baselineReleaseId,
      expectedCurrentReleaseId: currentRuntime.releaseId,
      activeAssets: baseline.activeAssets,
    },
  );
  await expectOk(activated, "激活本地上线演练医院生效版本");
}

export function resolveBaselineRuntimeAssets(value: unknown): BaselineRuntimeAssets {
  const baselineReleaseId = textField(recordField(value, "release"), "baselineReleaseId");
  const activeAssets = pageItems(value)
    .filter((item) => textField(item, "entryState") === "ACTIVE")
    .map((item) => ({
      assetType: textField(item, "assetType"),
      assetIdentity: textField(item, "assetIdentity"),
    }))
    .filter((item): item is { assetType: string; assetIdentity: string } =>
      Boolean(item.assetType && item.assetIdentity),
    )
    .map((item) => ({
      assetType: item.assetType,
      assetIdentity: item.assetIdentity,
      versionId: null,
    }));
  return {
    baselineReleaseId,
    activeAssets: uniqueRuntimeAssets(activeAssets),
  };
}

export function hospitalRuntimeCoversRequiredAssets(value: unknown) {
  const releaseId = textField(recordField(value, "release"), "releaseId");
  const activeAssets = pageItems(value)
    .filter((item) => textField(item, "entryState") === "ACTIVE")
    .map((item) => ({
      assetType: textField(item, "assetType"),
      assetIdentity: textField(item, "assetIdentity"),
      versionId: textField(item, "versionId"),
    }))
    .filter((item): item is RuntimeAssetSelection =>
      Boolean(item.assetType && item.assetIdentity && item.versionId),
    );
  return {
    releaseId,
    ready: Boolean(releaseId) && runtimeAssetsCoverRequiredTypes(activeAssets),
  };
}

export function runtimeAssetsCoverRequiredTypes(assets: RuntimeAssetSelection[]) {
  return requiredRuntimeAssets.every((required) =>
    assets.some((asset) => runtimeAssetMatchesRequired(asset, required)),
  );
}

export function missingRuntimeCandidateTypes(candidates: unknown[]) {
  return requiredRuntimeAssets
    .filter(
      (required) =>
        !candidates.some((candidate) => runtimeAssetMatchesRequired(candidate, required)),
    )
    .map((required) => required.assetType);
}

export function runtimeCandidatesCoverRequiredTypes(candidates: unknown[]) {
  return missingRuntimeCandidateTypes(candidates).length === 0;
}

export function versionIdsForRequiredRuntimeCandidates(candidates: unknown[]) {
  return requiredRuntimeAssets
    .map((required) => {
      const candidate = candidates.find((item) => runtimeAssetMatchesRequired(item, required));
      return candidate ? textField(candidate, "versionId") : null;
    })
    .filter((versionId): versionId is string => Boolean(versionId));
}

function runtimeAssetMatchesRequired(
  value: unknown,
  required: (typeof requiredRuntimeAssets)[number],
) {
  if (textField(value, "assetType") !== required.assetType) {
    return false;
  }
  return (
    !("assetIdentity" in required) || textField(value, "assetIdentity") === required.assetIdentity
  );
}

function resolveRuntimeAssetCandidates(value: unknown): RuntimeAssetCandidate[] {
  return pageItems(value)
    .map((item) => ({
      assetType: textField(item, "assetType"),
      assetIdentity: textField(item, "assetIdentity"),
      versionId: textField(item, "versionId"),
    }))
    .filter((item): item is RuntimeAssetCandidate =>
      Boolean(item.assetType && item.assetIdentity && item.versionId),
    );
}

async function getApi(page: Page, path: string) {
  return page.request.get(`${apiBase}${path}`, {
    headers: { "X-Trace-Id": `e2e-api-get-${Date.now()}` },
  });
}

async function responseData(response: APIResponse) {
  const body = (await response.json()) as { data?: unknown };
  return body.data ?? null;
}

function pageItems(value: unknown) {
  const record = recordValue(value);
  const items = record ? record.items : undefined;
  return Array.isArray(items) ? items : [];
}

function arrayData(value: unknown) {
  return Array.isArray(value) ? value : pageItems(value);
}

function recordField(value: unknown, field: string) {
  const record = recordValue(value);
  return record ? record[field] : undefined;
}

function numericField(value: unknown, field: string) {
  const record = recordValue(value);
  const raw = record ? record[field] : undefined;
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return raw;
  }
  if (typeof raw === "string" && raw.trim()) {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function textField(value: unknown, field: string) {
  const record = recordValue(value);
  const raw = record ? record[field] : undefined;
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function requireOrgUnit(value: unknown, label: string) {
  const id = textField(value, "id");
  const code = textField(value, "code");
  if (!id || !code) {
    throw new Error(`${label} 响应缺少组织 id/code`);
  }
  return { id, code };
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const seen = new Set<string>();
  return assets.filter((asset) => {
    const key = `${asset.assetType}:${asset.assetIdentity}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function localRoleDisplayName(role: RoleAccount) {
  const names: Record<RoleAccount, string> = {
    "platform-admin": "本地演练平台管理员",
    "engine-operator": "本地演练医疗引擎运营员",
    "clinical-user": "本地演练临床使用者",
    auditor: "本地演练审计员",
  };
  return names[role];
}

export async function postApi(page: Page, path: string, data: unknown) {
  return writeApi(page, "post", path, data);
}

export async function patchApi(page: Page, path: string, data: unknown) {
  return writeApi(page, "patch", path, data);
}

async function writeApi(page: Page, method: "post" | "patch", path: string, data: unknown) {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Trace-Id": `e2e-${Date.now()}`,
  };
  const xsrf = (await page.context().cookies(apiBase)).find(
    (cookie) => cookie.name === "XSRF-TOKEN",
  );
  if (xsrf) {
    headers["X-XSRF-TOKEN"] = xsrf.value;
  }
  return page.request[method](`${apiBase}${path}`, { data, headers });
}

export async function expectOk(response: APIResponse, label: string) {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`${label} 失败：${response.status()} ${body}`);
  }
}

export function stablePassword(
  role: RoleAccount,
  scope: RoleCredentialScope = defaultCredentialScope,
) {
  return credentialOverrides[scope]?.[role]?.password ?? `Mk@2026${role.replace(/-/g, "")}`;
}

function usernameFor(role: RoleAccount, scope: RoleCredentialScope = defaultCredentialScope) {
  return credentialOverrides[scope]?.[role]?.username ?? role;
}

function fallbackPasswordsFor(scope: RoleCredentialScope) {
  return scope === "rehearsal" ? [localInitialPassword, defaultPassword] : [defaultPassword];
}

export function resolvedTenantIdFor(
  role: RoleAccount,
  scope: RoleCredentialScope = defaultCredentialScope,
) {
  return tenantIdFor(role, scope);
}

export function resolveFrontendApiBase(baseUrl: string) {
  const normalized = baseUrl.trim().replace(/\/+$/, "");
  const pathname = new URL(normalized).pathname.replace(/\/+$/, "");
  const contextPath = pathname.endsWith("/medkernel") ? "" : "/medkernel";
  return `${normalized}${contextPath}/api/v1`;
}

export function appPath(path: string) {
  const basePath = new URL(appBase).pathname.replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  if (!basePath || basePath === "/") return normalizedPath;
  if (normalizedPath === "/") return basePath;
  return `${basePath}${normalizedPath}`;
}

function tenantIdFor(username: string, scope: RoleCredentialScope = defaultCredentialScope) {
  return credentialFor(username, scope)?.tenantId ?? tenantId;
}

function credentialFor(principal: string, scope: RoleCredentialScope = defaultCredentialScope) {
  const byRole = credentialOverrides[scope]?.[principal as RoleAccount];
  if (byRole) return byRole;
  return Object.values(credentialOverrides[scope] ?? {}).find(
    (credential) => credential.username === principal,
  );
}

function loadCredentialOverrides() {
  const file = process.env.E2E_ROLE_CREDENTIALS_FILE?.trim();
  if (!file) return buildLocalCredentialOverrides();
  const parsed = JSON.parse(readFileSync(file, "utf8"));
  const source =
    parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as {
          schemaVersion?: unknown;
          status?: unknown;
          platform?: { tenantId?: unknown; accounts?: unknown };
          rehearsal?: { tenantId?: unknown; accounts?: unknown };
        })
      : null;
  if (!source || source.schemaVersion !== "1.0.0" || source.status !== "READY") {
    throw new Error("E2E 上线凭据必须使用 READY 状态的 1.0.0 契约");
  }
  const retiredCredentialFields = [
    "role" + "Accounts",
    "platform" + "RoleAccounts",
    "customer" + "Tenant",
  ];
  if (retiredCredentialFields.some((field) => hasOwnField(source, field))) {
    throw new Error("E2E 上线凭据不得包含已移除旧账号字段");
  }
  if (
    !source.platform ||
    typeof source.platform !== "object" ||
    Array.isArray(source.platform) ||
    !source.platform.accounts ||
    typeof source.platform.accounts !== "object" ||
    Array.isArray(source.platform.accounts)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical platform.accounts 四职责账号");
  }
  if (
    !source.rehearsal ||
    typeof source.rehearsal !== "object" ||
    Array.isArray(source.rehearsal) ||
    !source.rehearsal.accounts ||
    typeof source.rehearsal.accounts !== "object" ||
    Array.isArray(source.rehearsal.accounts)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical rehearsal.accounts 机构四职责账号");
  }
  return resolveLaunchCredentialScopes({
    schemaVersion: source.schemaVersion,
    status: source.status,
    platform: {
      tenantId: source.platform.tenantId,
      accounts: source.platform.accounts,
    },
    rehearsal: {
      tenantId: source.rehearsal.tenantId,
      accounts: source.rehearsal.accounts,
    },
  });
}

function buildLocalCredentialOverrides(): ScopedRoleCredentialOverrides {
  return {
    platform: Object.fromEntries(
      ROLE_ACCOUNT_CODES.map((role) => [
        role,
        {
          tenantId: platformTenantId,
          username: role,
          role,
          password: `Mk@2026${role.replace(/-/g, "")}`,
        },
      ]),
    ) as ScopedRoleCredentialOverrides["platform"],
    rehearsal: Object.fromEntries(
      ROLE_ACCOUNT_CODES.map((role) => [
        role,
        {
          tenantId: localRehearsalTenantId,
          username: role,
          role,
          password: `Mk@2026${role.replace(/-/g, "")}`,
        },
      ]),
    ) as ScopedRoleCredentialOverrides["rehearsal"],
  };
}

function hasOwnField(source: object, field: string) {
  return Object.prototype.hasOwnProperty.call(source, field);
}

async function mirrorSecureCookiesForLocalProxy(page: Page, response: APIResponse) {
  const frontendUrl = new URL(frontendApiBase);
  if (frontendUrl.protocol !== "http:" || !isLoopbackHost(frontendUrl.hostname)) {
    return;
  }
  const cookies = response
    .headersArray()
    .filter((header) => header.name.toLowerCase() === "set-cookie")
    .map((header) => parseSetCookieForLocalProxy(header.value, frontendUrl.origin))
    .filter((cookie): cookie is NonNullable<ReturnType<typeof parseSetCookieForLocalProxy>> =>
      Boolean(cookie),
    );
  if (cookies.length > 0) {
    await page.context().addCookies(cookies);
  }
}

export function parseSetCookieForLocalProxy(header: string, origin: string) {
  const [nameValue, ...attributes] = header.split(";");
  const separator = nameValue.indexOf("=");
  if (separator <= 0) return null;
  const secure = attributes.some((attribute) => attribute.trim().toLowerCase() === "secure");
  if (!secure) return null;
  const name = nameValue.slice(0, separator).trim();
  const value = nameValue.slice(separator + 1).trim();
  if (!name || !value) return null;
  const sameSite = sameSiteAttribute(cookieAttribute(attributes, "samesite"));
  return {
    name,
    value,
    url: origin,
    httpOnly: attributes.some((attribute) => attribute.trim().toLowerCase() === "httponly"),
    secure: false,
    sameSite,
  };
}

function cookieAttribute(attributes: string[], name: string) {
  const prefix = `${name.toLowerCase()}=`;
  const match = attributes.find((attribute) => attribute.trim().toLowerCase().startsWith(prefix));
  if (!match) return undefined;
  return match.trim().slice(prefix.length);
}

function sameSiteAttribute(value?: string) {
  const normalized = value?.toLowerCase();
  if (normalized === "lax") return "Lax" as const;
  if (normalized === "none") return "None" as const;
  return "Strict" as const;
}

function isLoopbackHost(hostname: string) {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

function requireEnv(name: string) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} 未配置，E2E 必须显式指向当前真实后端。`);
  }
  return value;
}

export function totp(secret: string) {
  const counter = Math.floor(Date.now() / 1000 / 30);
  const key = base32Decode(secret);
  const message = Buffer.alloc(8);
  message.writeBigInt64BE(BigInt(counter));
  const hash = createHmac("sha1", key).update(message).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  const binary =
    ((hash[offset] & 0x7f) << 24) |
    ((hash[offset + 1] & 0xff) << 16) |
    ((hash[offset + 2] & 0xff) << 8) |
    (hash[offset + 3] & 0xff);
  return String(binary % 1_000_000).padStart(6, "0");
}

function base32Decode(value: string) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let buffer = 0;
  let bitsLeft = 0;
  const bytes: number[] = [];
  for (const char of value.replace(/=|\s/g, "").toUpperCase()) {
    const index = alphabet.indexOf(char);
    if (index < 0) {
      throw new Error(`非法 TOTP 密钥字符：${char}`);
    }
    buffer = (buffer << 5) | index;
    bitsLeft += 5;
    if (bitsLeft >= 8) {
      bytes.push((buffer >> (bitsLeft - 8)) & 0xff);
      bitsLeft -= 8;
    }
  }
  return Buffer.from(bytes);
}
