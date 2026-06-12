// P5：第一阶段核心只读探针。
// 产出：docs/release/evidence/p5-second-fresh-drill-20260612/core-readiness/p5-core-readiness-probe.json。
// 凭据：仅从 P5_ROLE_CREDENTIALS_FILE 指向的受控凭据副本读取，证据不得输出口令、Cookie、Token、MFA 或恢复码。
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

const repo = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const evidenceDir = join(
  repo,
  "docs/release/evidence/p5-second-fresh-drill-20260612/core-readiness",
);
const outputPath = join(evidenceDir, "p5-core-readiness-probe.json");
const baseUrl = (
  process.env.P5_API_BASE_URL ?? "https://193.112.107.134/medkernel/api/v1"
).replace(/\/+$/, "");
const credentialFile = process.env.P5_ROLE_CREDENTIALS_FILE;
if (!credentialFile) {
  throw new Error("缺少 P5_ROLE_CREDENTIALS_FILE，不能读取受控 P5 凭据。");
}

const credentials = JSON.parse(readFileSync(credentialFile, "utf8"));
const startedAt = new Date().toISOString();
const runTag = `p5-core-${Date.now().toString(36)}`;

const actorSpecs = [
  {
    role: "platform-knowledge-governor",
    probes: [
      ["平台知识身份列表", "GET", "/engine/knowledge/identities?page=1&size=5"],
      [
        "平台知识候选队列",
        "GET",
        "/engine/knowledge/review-queue?page=1&size=5",
      ],
    ],
  },
  {
    role: "knowledge-governor",
    probes: [
      ["机构知识身份列表", "GET", "/engine/knowledge/identities?page=1&size=5"],
      ["规则定义列表", "GET", "/engine/rule/rules?page=1&size=5"],
      [
        "路径模板列表",
        "GET",
        "/engine/pathway/pathway-templates?page=1&size=5",
      ],
    ],
  },
  {
    role: "clinical-decision-user",
    probes: [
      ["患者路径列表", "GET", "/engine/pathway/patient-pathways?page=1&size=5"],
      ["协同待办列表", "GET", "/engine/workflow/todos?page=1&size=5"],
      ["通知列表", "GET", "/engine/notifications?page=1&size=5"],
      ["CDSS 风险矩阵", "GET", "/engine/cdss/risk-matrix"],
    ],
  },
  {
    role: "quality-governor",
    probes: [
      ["质量驾驶舱", "GET", "/engine/quality/dashboard"],
      ["质量预警", "GET", "/engine/quality/alerts?page=1&size=5"],
      ["医保审核", "GET", "/engine/quality/insurance-issues?page=1&size=5"],
    ],
  },
  {
    role: "compliance-auditor",
    probes: [
      ["审计事件", "GET", "/compliance/audit/events?size=5"],
      ["导出审批", "GET", "/compliance/exports?resourceType=AUDIT_EVENT"],
      [
        "脱敏规则",
        "GET",
        "/compliance/masking-rules?resourceType=PATIENT&fieldName=patientName",
      ],
    ],
  },
  {
    role: "integration-operator",
    probes: [
      ["模型能力状态", "GET", "/model-capabilities/status"],
      ["运维操作", "GET", "/system/operations"],
      ["国产化报告", "GET", "/system/operations/domestic-report"],
    ],
  },
  {
    role: "implementation-operator",
    probes: [
      ["系统供应商", "GET", "/system/operations"],
      ["身份来源", "GET", "/compliance/identity-bindings?page=1&size=5"],
      ["人员账号", "GET", "/engine/org/org-units/users?page=1&size=5"],
    ],
  },
];

const forbiddenText =
  /mock|demo|演示路径|演示验收|固定(?:医学|病例|剧本|路径)|胸痛\s*AMI|医务处张三|头孢/i;
const results = [];
const failures = [];

mkdirSync(evidenceDir, { recursive: true });

for (const spec of actorSpecs) {
  const actor = actorFor(spec.role);
  const session = await login(actor, spec.role);
  const profile = await api(
    session,
    "GET",
    "/security/me",
    `${runTag}-${spec.role}-profile`,
  );
  const roles = profile.body?.data?.roles?.map((role) => role.code) ?? [];
  if (!roles.includes(spec.role)) {
    failures.push(`${spec.role} 登录画像不含目标角色：${roles.join(",")}`);
  }
  for (const [index, [label, method, path]] of spec.probes.entries()) {
    const response = await api(
      session,
      method,
      path,
      `${runTag}-${spec.role}-probe-${index + 1}`,
    );
    const serialized = JSON.stringify(response.body ?? response.text ?? "");
    const fakeTextFound = forbiddenText.test(serialized);
    if (response.status >= 400) {
      failures.push(`${spec.role} ${label} 返回 ${response.status}`);
    }
    if (fakeTextFound) {
      failures.push(`${spec.role} ${label} 出现演示或固定医学文本`);
    }
    results.push({
      role: spec.role,
      label,
      method,
      path,
      status: response.status,
      dataShape: dataShape(response.body),
      fakeTextFound,
    });
  }
}

const evidence = {
  status: failures.length === 0 ? "PASSED" : "FAILED",
  startedAt,
  finishedAt: new Date().toISOString(),
  baseUrl,
  credentialBoundary: {
    source: "P5_ROLE_CREDENTIALS_FILE",
    repositoryContainsSecrets: false,
  },
  probes: results,
  failures,
};

writeFileSync(outputPath, `${JSON.stringify(redact(evidence), null, 2)}\n`);
console.log(
  JSON.stringify({
    status: evidence.status,
    probes: results.length,
    outputPath,
  }),
);

if (failures.length > 0) {
  process.exitCode = 1;
}

function actorFor(role) {
  if (role === "organization-admin") {
    return {
      username: credentials.customerTenant.adminUsername,
      password: credentials.customerTenant.password,
      tenantId: credentials.customerTenant.tenantId,
    };
  }
  const source =
    credentials.roleAccounts?.[role] ??
    credentials.platformRoleAccounts?.[role];
  if (!source?.username || !source?.password || !source?.tenantId) {
    throw new Error(`缺少 ${role} 受控凭据元数据。`);
  }
  return {
    username: source.username,
    password: source.password,
    tenantId: source.tenantId,
  };
}

async function login(actor, role) {
  const response = await fetch(`${baseUrl}/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `${runTag}-${role}-login`,
    },
    body: JSON.stringify({
      username: actor.username,
      password: actor.password,
      tenantId: actor.tenantId,
    }),
  });
  if (!response.ok) {
    throw new Error(`${role} 登录失败：${response.status}`);
  }
  return {
    role,
    cookie: cookieHeader(response.headers),
  };
}

async function api(session, method, path, traceId) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Accept: "application/json, text/plain",
      Cookie: session.cookie,
      "X-Trace-Id": traceId,
    },
  });
  const contentType = response.headers.get("content-type") ?? "";
  const text = await response.text();
  let body = null;
  if (contentType.includes("application/json") && text) {
    body = JSON.parse(text);
  }
  return {
    status: response.status,
    body,
    text: body ? undefined : text.slice(0, 500),
  };
}

function cookieHeader(headers) {
  const raw =
    typeof headers.getSetCookie === "function" ? headers.getSetCookie() : [];
  const values =
    raw.length > 0 ? raw : splitSetCookie(headers.get("set-cookie") ?? "");
  return values
    .map((line) => line.split(";")[0])
    .filter(Boolean)
    .join("; ");
}

function splitSetCookie(value) {
  if (!value) return [];
  return value.split(/,(?=\s*[^;,]+=)/);
}

function dataShape(body) {
  const data = body?.data;
  if (Array.isArray(data)) return { kind: "array", size: data.length };
  if (data?.content && Array.isArray(data.content)) {
    return {
      kind: "page",
      size: data.content.length,
      total: data.totalElements ?? null,
    };
  }
  if (data?.items && Array.isArray(data.items)) {
    return { kind: "page", size: data.items.length, total: data.total ?? null };
  }
  if (data && typeof data === "object")
    return { kind: "object", keys: Object.keys(data).sort() };
  return { kind: data === null ? "null" : typeof data };
}

function redact(value) {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, val]) => [
        key,
        /password|cookie|token|secret|signature|recovery|mfa|otp|totp/i.test(
          key,
        )
          ? "[REDACTED]"
          : redact(val),
      ]),
    );
  }
  return value;
}
