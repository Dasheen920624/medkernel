import { randomBytes } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { launchCoverageClaims } from "./stage-launch-coverage-lib.mjs";

export const ASSIGNABLE_ROLES = Object.freeze([
  "platform-admin",
  "engine-operator",
  "clinical-user",
  "auditor",
]);

const REHEARSAL_HOSPITAL = Object.freeze({
  code: "REHEARSAL-HOSPITAL",
  name: "完整上线演练医院",
  facilityType: "HOSPITAL",
});
const ROLE_SET = new Set(ASSIGNABLE_ROLES);
const REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const RETIRED_CREDENTIAL_FIELDS = Object.freeze([
  "accounts",
  "roleAccounts",
  "platformRoleAccounts",
  "customerTenant",
]);
const ALLOWED_PATHS = Object.freeze([
  ["GET", /^\/bootstrap\/status$/u],
  ["POST", /^\/bootstrap\/init-token$/u],
  ["POST", /^\/bootstrap\/password$/u],
  ["POST", /^\/auth\/login$/u],
  ["POST", /^\/auth\/change-password$/u],
  ["POST", /^\/compliance\/users$/u],
  ["POST", /^\/compliance\/users\/[^/]+\/roles$/u],
  ["POST", /^\/admin\/tenants$/u],
  ["GET", /^\/engine\/org\/org-units\/by-level\?level=TENANT$/u],
  ["POST", /^\/engine\/org\/org-units$/u],
  ["GET", /^\/security\/me$/u],
]);

export function buildLaunchCredentialPlan(options = {}) {
  const generatedAt = toIso(options.generatedAt ?? new Date());
  const passwordFactory = options.passwordFactory ?? securePassword;
  const platformTenantId = "t-1";
  const rehearsalTenantId = "t-rehearsal";
  return {
    schemaVersion: "1.0.0",
    status: "READY",
    generatedAt,
    platform: {
      tenantId: platformTenantId,
      takeover: account({
        tenantId: platformTenantId,
        username: "system-takeover",
        role: "system-superadmin",
        assignable: false,
        passwordFactory,
        passwordLabel: "platform-takeover",
      }),
      accounts: roleAccounts(platformTenantId, "platform", passwordFactory),
    },
    rehearsal: {
      tenantId: rehearsalTenantId,
      tenantName: "完整上线演练机构",
      hospital: { ...REHEARSAL_HOSPITAL },
      accounts: roleAccounts(rehearsalTenantId, "rehearsal", passwordFactory),
    },
  };
}

export function validateLaunchCredentials(credentials) {
  requireObject(credentials, "上线凭据");
  for (const field of RETIRED_CREDENTIAL_FIELDS) {
    if (Object.hasOwn(credentials, field)) {
      throw new Error(`上线凭据禁止保留旧凭据字段 ${field}`);
    }
  }
  if (credentials.schemaVersion !== "1.0.0") {
    throw new Error("上线凭据 schemaVersion 必须为 1.0.0");
  }
  if (credentials.status !== "READY") {
    throw new Error("上线凭据状态必须为 READY");
  }
  requireText(credentials.generatedAt, "credentials.generatedAt");
  validateScope(credentials.platform, "platform", "t-1");
  validateScope(credentials.rehearsal, "rehearsal", "t-rehearsal");
  requireText(credentials.rehearsal.tenantName, "rehearsal.tenantName");
  validateRehearsalHospital(credentials.rehearsal.hospital);
  validateAccount(
    credentials.platform.takeover,
    "platform.takeover",
    "t-1",
    "system-superadmin",
  );
  if (credentials.platform.takeover.assignable !== false) {
    throw new Error("内置接管身份必须明确标记为不可分配");
  }
  rejectInitialPassword(credentials);
  return credentials;
}

export function selectLaunchAccount(credentials, scope, role) {
  validateLaunchCredentials(credentials);
  if (!ROLE_SET.has(role)) throw new Error(`非法的四职责编码 ${role}`);
  if (scope !== "platform" && scope !== "rehearsal") {
    throw new Error(`非法的上线凭据作用域 ${scope}`);
  }
  return credentials[scope].accounts[role];
}

export function readLaunchBootstrapConfig(env, options = {}) {
  const readFile = options.readFile ?? ((file) => readFileSync(file, "utf8"));
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  for (const key of [
    "LAUNCH_API_BASE_URL",
    "LAUNCH_BOOTSTRAP_TOKEN_FILE",
    "LAUNCH_CREDENTIALS_FILE",
  ]) {
    if (!hasText(env?.[key])) throw new Error(`缺少必填环境变量 ${key}`);
  }
  const runtimeRoot = path.resolve(
    env.MEDKERNEL_RUNTIME_ROOT?.trim() || "/var/lib/medkernel",
  );
  const tokenPath = outsideRepo(
    env.LAUNCH_BOOTSTRAP_TOKEN_FILE,
    repoRoot,
    "接管令牌路径",
  );
  const credentialsPath = outsideRepo(
    env.LAUNCH_CREDENTIALS_FILE,
    repoRoot,
    "上线凭据路径",
  );
  const evidencePath = outsideRepo(
    env.LAUNCH_ACCOUNT_EVIDENCE_PATH?.trim() ||
      path.join(runtimeRoot, "evidence/current-launch/account-bootstrap.json"),
    repoRoot,
    "接管证据路径",
  );
  const bootstrapToken = requireText(
    readFile(tokenPath),
    "bootstrap init token",
  );
  if (bootstrapToken.length < 32)
    throw new Error("bootstrap init token 长度不足 32 位");
  return {
    apiBaseUrl: normalizeBaseUrl(env.LAUNCH_API_BASE_URL),
    tokenPath,
    credentialsPath,
    evidencePath,
    bootstrapToken,
  };
}

export function assertLaunchOutputPathsAvailable(
  config,
  pathExists = existsSync,
) {
  if (pathExists(config.credentialsPath)) {
    throw new Error(`全新接管拒绝覆盖既有上线凭据：${config.credentialsPath}`);
  }
  if (pathExists(config.evidencePath)) {
    throw new Error(`全新接管拒绝覆盖既有接管证据：${config.evidencePath}`);
  }
}

export async function runLaunchAccountBootstrap(options) {
  const apiBaseUrl = normalizeBaseUrl(options?.apiBaseUrl);
  const bootstrapToken = requireText(options?.bootstrapToken, "bootstrapToken");
  const fetchImpl = options?.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function")
    throw new Error("当前 Node.js 运行时不支持 fetch");
  const plan = structuredClone(options?.plan ?? buildLaunchCredentialPlan());
  const requests = [];
  const startedAt = now(options?.now);

  const status = await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    method: "GET",
    path: "/bootstrap/status",
    label: "核对首次接管状态",
  });
  if (status.data?.initialized !== false) {
    throw new Error("全新接管前系统必须处于未初始化状态");
  }
  await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    method: "POST",
    path: "/bootstrap/init-token",
    body: { token: bootstrapToken },
    label: "校验首次接管令牌",
  });
  await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    method: "POST",
    path: "/bootstrap/password",
    body: {
      token: bootstrapToken,
      username: plan.platform.takeover.username,
      password: plan.platform.takeover.initialPassword,
    },
    label: "创建内置接管身份",
  });

  const takeoverSession = await finalizeAccount({
    apiBaseUrl,
    fetchImpl,
    requests,
    account: plan.platform.takeover,
    expectedRole: "system-superadmin",
  });

  const platformSessions = new Map();
  for (const role of ASSIGNABLE_ROLES) {
    const target = plan.platform.accounts[role];
    await createMember({
      apiBaseUrl,
      fetchImpl,
      requests,
      session: takeoverSession,
      account: target,
    });
    platformSessions.set(
      role,
      await finalizeAccount({
        apiBaseUrl,
        fetchImpl,
        requests,
        account: target,
        expectedRole: role,
      }),
    );
  }

  const rehearsalAdmin = plan.rehearsal.accounts["platform-admin"];
  await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    session: platformSessions.get("platform-admin"),
    method: "POST",
    path: "/admin/tenants",
    body: {
      tenantId: plan.rehearsal.tenantId,
      tenantName: plan.rehearsal.tenantName,
      adminUsername: rehearsalAdmin.username,
      adminInitialPassword: rehearsalAdmin.initialPassword,
    },
    label: "开通完整上线演练机构",
  });
  let rehearsalAdminSession = await finalizeAccount({
    apiBaseUrl,
    fetchImpl,
    requests,
    account: rehearsalAdmin,
    expectedRole: "platform-admin",
  });
  const rehearsalHospital = await provisionRehearsalHospital({
    apiBaseUrl,
    fetchImpl,
    requests,
    session: rehearsalAdminSession,
    hospital: plan.rehearsal.hospital,
  });
  await assignFacilityRole({
    apiBaseUrl,
    fetchImpl,
    requests,
    session: rehearsalAdminSession,
    account: rehearsalAdmin,
    hospitalId: rehearsalHospital.id,
  });
  rehearsalAdminSession = await verifyFinalAccount({
    apiBaseUrl,
    fetchImpl,
    requests,
    account: rehearsalAdmin,
    expectedRole: "platform-admin",
    expectedScope: {
      tenantId: rehearsalAdmin.tenantId,
      hospitalId: rehearsalHospital.id,
    },
  });

  for (const role of ASSIGNABLE_ROLES.filter(
    (value) => value !== "platform-admin",
  )) {
    const target = plan.rehearsal.accounts[role];
    await createMember({
      apiBaseUrl,
      fetchImpl,
      requests,
      session: rehearsalAdminSession,
      account: target,
    });
    await assignFacilityRole({
      apiBaseUrl,
      fetchImpl,
      requests,
      session: rehearsalAdminSession,
      account: target,
      hospitalId: rehearsalHospital.id,
    });
    await finalizeAccount({
      apiBaseUrl,
      fetchImpl,
      requests,
      account: target,
      expectedRole: role,
      expectedScope: {
        tenantId: target.tenantId,
        hospitalId: rehearsalHospital.id,
      },
    });
  }

  const credentials = stripTransientSecrets(plan);
  validateLaunchCredentials(credentials);
  return {
    credentials,
    evidence: {
      status: "PASSED",
      stage: "ACCOUNT_BOOTSTRAP",
      startedAt,
      finishedAt: now(options?.now),
      platformTenantId: credentials.platform.tenantId,
      rehearsalTenantId: credentials.rehearsal.tenantId,
      rehearsalHospitalId: rehearsalHospital.id,
      verifiedRoles: [...ASSIGNABLE_ROLES],
      verifiedAccountCount: 9,
      mfaRequired: false,
      launchCoverage: launchCoverageClaims(
        [
          ["productLayers", "FOUNDATION_GOVERNANCE"],
          ["organizationLevels", "PLATFORM"],
          ["organizationLevels", "HOSPITAL"],
        ],
        now(options?.now),
      ),
      requests,
    },
  };
}

export function writeJsonAtomic(outputPath, value) {
  const target = path.resolve(requireText(outputPath, "outputPath"));
  const temporary = `${target}.${process.pid}.tmp`;
  mkdirSync(path.dirname(target), { recursive: true });
  try {
    writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`, {
      encoding: "utf8",
      mode: 0o600,
    });
    renameSync(temporary, target);
  } finally {
    if (existsSync(temporary)) rmSync(temporary, { force: true });
  }
}

async function createMember(context) {
  await requestJson({
    ...context,
    method: "POST",
    path: "/compliance/users",
    body: {
      credentialManaged: true,
      userId: context.account.userId,
      displayName: context.account.displayName,
      username: context.account.username,
      roleCode: context.account.role,
      initialPassword: context.account.initialPassword,
    },
    label: `开通 ${context.account.tenantId}/${context.account.role}`,
  });
}

async function provisionRehearsalHospital(context) {
  const tenantRoots = await requestJson({
    ...context,
    method: "GET",
    path: "/engine/org/org-units/by-level?level=TENANT",
    label: "读取完整上线演练机构根组织",
  });
  const root = Array.isArray(tenantRoots.data)
    ? tenantRoots.data.find((item) => item?.level === "TENANT")
    : null;
  if (!root?.id) {
    throw new Error("完整上线演练机构缺少租户根组织，无法创建演练医院");
  }
  const created = await requestJson({
    ...context,
    method: "POST",
    path: "/engine/org/org-units",
    body: {
      parentId: root.id,
      level: "FACILITY",
      code: context.hospital.code,
      name: context.hospital.name,
      facilityType: context.hospital.facilityType,
      status: "ACTIVE",
    },
    label: "创建完整上线演练医院",
  });
  if (!created.data?.id) {
    throw new Error("完整上线演练医院创建成功但响应缺少组织 ID");
  }
  return created.data;
}

async function assignFacilityRole(context) {
  await requestJson({
    ...context,
    method: "POST",
    path: `/compliance/users/${encodeURIComponent(context.account.userId)}/roles`,
    body: {
      roleCode: context.account.role,
      scopeLevel: "FACILITY",
      scopeCode: context.hospitalId,
    },
    label: `绑定 ${context.account.tenantId}/${context.account.role} 演练医院范围`,
  });
}

async function finalizeAccount(context) {
  const initial = await login(context, context.account.initialPassword);
  assertLogin(initial.data, context.account, context.expectedRole, true);
  const initialSession = authenticatedSession(initial.headers);
  await requestJson({
    ...context,
    session: initialSession,
    method: "POST",
    path: "/auth/change-password",
    body: {
      oldPassword: context.account.initialPassword,
      newPassword: context.account.password,
    },
    label: `${context.account.tenantId}/${context.account.username} 首登改密`,
  });
  return verifyFinalAccount(context);
}

async function verifyFinalAccount(context) {
  const finalLogin = await login(context, context.account.password);
  assertLogin(finalLogin.data, context.account, context.expectedRole, false);
  const finalSession = authenticatedSession(finalLogin.headers);
  const profile = await requestJson({
    ...context,
    session: finalSession,
    method: "GET",
    path: "/security/me",
    label: `${context.account.tenantId}/${context.account.username} 权限画像`,
  });
  assertProfile(profile.data, context.expectedRole, context.expectedScope);
  return finalSession;
}

function login(context, password) {
  return requestJson({
    ...context,
    method: "POST",
    path: "/auth/login",
    body: {
      tenantId: context.account.tenantId,
      username: context.account.username,
      password,
    },
    label: `${context.account.tenantId}/${context.account.username} 登录`,
  });
}

function assertLogin(data, accountValue, expectedRole, mustChangePwd) {
  if (
    !data ||
    data.tenantId !== accountValue.tenantId ||
    data.userId !== accountValue.userId
  ) {
    throw new Error(`${accountValue.username} 登录身份与目标账号不一致`);
  }
  if (data.mustChangePwd !== mustChangePwd) {
    throw new Error(`${accountValue.username} 首登改密状态不符合预期`);
  }
  if (data.mfaRequired !== false || data.mfaBound !== false) {
    throw new Error(`${accountValue.username} 上线默认 MFA 必须关闭且未绑定`);
  }
  if (
    !Array.isArray(data.roles) ||
    data.roles.length !== 1 ||
    data.roles[0] !== expectedRole
  ) {
    throw new Error(
      `${accountValue.username} 必须且只能拥有职责 ${expectedRole}`,
    );
  }
}

function assertProfile(data, expectedRole, expectedScope) {
  const roles = Array.isArray(data?.roles)
    ? data.roles.map((item) => item?.code)
    : [];
  if (roles.length !== 1 || roles[0] !== expectedRole) {
    throw new Error(`权限画像必须且只能包含职责 ${expectedRole}`);
  }
  if (
    data.mustChangePwd !== false ||
    data.mfaRequired !== false ||
    data.mfaBound !== false
  ) {
    throw new Error(
      `${expectedRole} 权限画像的改密或 MFA 状态不符合上线默认值`,
    );
  }
  if (!Array.isArray(data.menuKeys) || !data.menuKeys.includes("workbench")) {
    throw new Error(`${expectedRole} 权限画像缺少工作台入口`);
  }
  if (
    expectedScope?.tenantId &&
    data?.dataScope?.tenantId !== expectedScope.tenantId
  ) {
    throw new Error(
      `${expectedRole} 权限画像缺少租户范围 ${expectedScope.tenantId}`,
    );
  }
  if (
    expectedScope?.hospitalId &&
    data?.dataScope?.hospitalId !== expectedScope.hospitalId
  ) {
    throw new Error(
      `${expectedRole} 权限画像缺少医院范围 ${expectedScope.hospitalId}`,
    );
  }
}

async function requestJson(options) {
  assertAllowedPath(options.method, options.path);
  const headers = {
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-Trace-Id": `launch-bootstrap-${Date.now()}`,
  };
  if (options.session) {
    headers.Cookie = options.session.cookie;
    headers["X-XSRF-TOKEN"] = options.session.xsrf;
  }
  const response = await options.fetchImpl(
    `${options.apiBaseUrl}${options.path}`,
    {
      method: options.method,
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
    },
  );
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(
      `${options.label} 返回的不是合法 JSON（HTTP ${response.status}）`,
    );
  }
  options.requests.push({
    method: options.method,
    path: options.path,
    status: response.status,
    ok: response.ok,
    label: options.label,
  });
  if (!response.ok || payload?.success === false) {
    throw new Error(
      `${options.label} 失败（HTTP ${response.status}，${payload?.code ?? "NO_CODE"}）：` +
        `${payload?.detail ?? payload?.message ?? "无错误详情"}`,
    );
  }
  return { data: payload?.data, headers: response.headers };
}

function authenticatedSession(headers) {
  const raw = headers?.get?.("set-cookie") ?? "";
  const cookiePairs = splitSetCookie(raw)
    .map((item) => item.split(";", 1)[0]?.trim())
    .filter(Boolean);
  const access = cookiePairs.find((item) => item.startsWith("mk_access="));
  const xsrf = cookiePairs.find((item) => item.startsWith("XSRF-TOKEN="));
  if (!access || !xsrf)
    throw new Error("登录响应未返回 mk_access 与 XSRF-TOKEN");
  return {
    cookie: cookiePairs.join("; "),
    xsrf: decodeURIComponent(xsrf.slice("XSRF-TOKEN=".length)),
  };
}

function splitSetCookie(value) {
  return String(value)
    .split(/,(?=\s*[^;,=\s]+=[^;,]*)/u)
    .map((item) => item.trim())
    .filter(Boolean);
}

function roleAccounts(tenantId, scope, passwordFactory) {
  return Object.fromEntries(
    ASSIGNABLE_ROLES.map((role) => [
      role,
      account({
        tenantId,
        username: role,
        role,
        assignable: true,
        passwordFactory,
        passwordLabel: `${scope}-${role}`,
      }),
    ]),
  );
}

function account(options) {
  return {
    tenantId: options.tenantId,
    userId: options.username,
    username: options.username,
    displayName: options.role,
    role: options.role,
    assignable: options.assignable,
    initialPassword: requireText(
      options.passwordFactory(`initial-${options.passwordLabel}`),
      `${options.passwordLabel}.initialPassword`,
    ),
    password: requireText(
      options.passwordFactory(`final-${options.passwordLabel}`),
      `${options.passwordLabel}.password`,
    ),
  };
}

function stripTransientSecrets(plan) {
  delete plan.platform.takeover.initialPassword;
  for (const scope of [plan.platform, plan.rehearsal]) {
    for (const value of Object.values(scope.accounts))
      delete value.initialPassword;
  }
  return plan;
}

function validateScope(scope, name, expectedTenantId) {
  requireObject(scope, name);
  if (scope.tenantId !== expectedTenantId) {
    throw new Error(`${name}.tenantId 必须为 ${expectedTenantId}`);
  }
  requireObject(scope.accounts, `${name}.accounts`);
  const keys = Object.keys(scope.accounts);
  if (
    keys.length !== ASSIGNABLE_ROLES.length ||
    ASSIGNABLE_ROLES.some((role) => !Object.hasOwn(scope.accounts, role))
  ) {
    throw new Error(`${name}.accounts 必须恰好包含四职责`);
  }
  for (const role of ASSIGNABLE_ROLES) {
    validateAccount(
      scope.accounts[role],
      `${name}.accounts.${role}`,
      expectedTenantId,
      role,
    );
  }
}

function validateRehearsalHospital(value) {
  requireObject(value, "rehearsal.hospital");
  requireText(value.code, "rehearsal.hospital.code");
  requireText(value.name, "rehearsal.hospital.name");
  if (value.facilityType !== "HOSPITAL") {
    throw new Error("rehearsal.hospital.facilityType 必须为 HOSPITAL");
  }
}

function validateAccount(value, label, tenantId, role) {
  requireObject(value, label);
  for (const field of [
    "tenantId",
    "userId",
    "username",
    "displayName",
    "role",
    "password",
  ]) {
    requireText(value[field], `${label}.${field}`);
  }
  if (value.tenantId !== tenantId || value.role !== role) {
    throw new Error(`${label} 的租户或职责不匹配`);
  }
  if (role !== "system-superadmin" && value.assignable !== true) {
    throw new Error(`${label} 必须为可分配职责账号`);
  }
}

function rejectInitialPassword(value) {
  if (!value || typeof value !== "object") return;
  for (const [key, item] of Object.entries(value)) {
    if (key === "initialPassword") throw new Error("正式凭据禁止保留临时口令");
    rejectInitialPassword(item);
  }
}

function assertAllowedPath(method, requestPath) {
  if (
    !ALLOWED_PATHS.some(
      ([allowedMethod, pattern]) =>
        allowedMethod === method && pattern.test(requestPath),
    )
  ) {
    throw new Error(`接管脚本拒绝未列入白名单的接口 ${method} ${requestPath}`);
  }
}

function outsideRepo(value, repoRoot, label) {
  const target = path.resolve(requireText(value, label));
  const relative = path.relative(repoRoot, target);
  if (
    relative === "" ||
    (!relative.startsWith("..") && !path.isAbsolute(relative))
  ) {
    throw new Error(`${label}必须位于代码仓库之外`);
  }
  return target;
}

function normalizeBaseUrl(value) {
  const normalized = requireText(value, "apiBaseUrl").replace(/\/+$/u, "");
  const parsed = new URL(normalized);
  if (
    !/^https?:$/u.test(parsed.protocol) ||
    !parsed.pathname.endsWith("/api/v1")
  ) {
    throw new Error("上线 API 地址必须是以 /api/v1 结尾的 HTTP(S) 地址");
  }
  return normalized;
}

function securePassword(label) {
  return `Mk@${randomBytes(24).toString("base64url")}!${label.length}`;
}

function toIso(value) {
  return value instanceof Date
    ? value.toISOString()
    : new Date(value).toISOString();
}

function now(clock) {
  return clock ? toIso(clock()) : new Date().toISOString();
}

function requireObject(value, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label} 必须是 JSON 对象`);
  }
  return value;
}

function requireText(value, label) {
  if (!hasText(value)) throw new Error(`${label} 不能为空`);
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}
