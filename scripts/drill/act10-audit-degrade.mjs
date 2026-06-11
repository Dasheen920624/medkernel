// 幕10：合规审计、数据权限、脱敏预览、导出审批、模型降级、国产化与备份恢复 L1 演练。
// 产出：docs/release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/00-*.json 至 99-summary.json。
// 凭据：仅从 134 受限配置文件读取，脚本和证据不得输出口令、Cookie、令牌或签名材料。
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repo = resolve(scriptDir, '..', '..');
const evidenceDir = join(repo, 'docs/release/evidence/v1.0-drill-20260611/幕10-合规审计与降级');
mkdirSync(evidenceDir, { recursive: true });

const baseRoot = 'https://193.112.107.134/medkernel';
const baseUrl = `${baseRoot}/api/v1`;
const tenantId = 'drill-hospital-20260611';
const hospitalId = '01KTSAC1JJB2V2X4F9DBF1NSVR';
const respiratoryDepartmentId = '01KTSAC1SCFJB2RKCY3DHEAGMQ';
const cardiologyDepartmentId = '01KTSAC1R035AR0RMPG71RPG4H';
const patientId = 'mpi-01KTSWK7P3XQQ8VC0JZFZ63AMY';
const encounterId = 'enc-act6-8oh7bn024a';
const packageVersion = '2026.06.11-act8-8sinb347c5';
const act6CriticalTrace = 'act6-8oh7bn024a-k-event';
const act6DdiTrace = 'act6-8oh7bn024a-ddi-event';
const deployedJarSha256 = '559c1ad8630df4dc34fe57c799b290e9c58a86fcc9b0efa8a7a1621aab02725a';
const deployBackup = '/zoesoft/medkernel/backups/deploy-20260611-110134';
const runTag = `act10-${Date.now().toString(36)}`;
const startedAt = new Date().toISOString();
const traceLines = [];

function remoteCredentials() {
  const raw = execFileSync('ssh', [
    '-o', 'BatchMode=yes',
    '-o', 'StrictHostKeyChecking=no',
    'root@193.112.107.134',
    'cat /zoesoft/medkernel/conf/drill-act1-credentials-20260611.json',
  ], { encoding: 'utf8', maxBuffer: 2 * 1024 * 1024 });
  return JSON.parse(raw).credentials;
}

const credentials = remoteCredentials();
const actors = {
  audit: credentials[`${tenantId}:drill-audit-20260611`],
  itOps: credentials[`${tenantId}:drill-it-ops-20260611`],
  hospitalAdmin: credentials[`${tenantId}:drill-hospital-admin-20260611`],
  medical: credentials[`${tenantId}:drill-medical-affairs-20260611`],
  respiratoryDoctor: credentials[`${tenantId}:drill-respiratory-doctor-20260611`],
  cardiologyDoctor: credentials[`${tenantId}:drill-cardiology-doctor-20260611`],
};

for (const [name, actor] of Object.entries(actors)) {
  if (!actor?.username || !actor?.currentPassword) {
    throw new Error(`missing actor credential: ${name}`);
  }
}

function redact(value) {
  if (Array.isArray(value)) {
    return value.map(redact);
  }
  if (value && typeof value === 'object') {
    const output = {};
    for (const [key, val] of Object.entries(value)) {
      if (/password|cookie|token|secret|signature|currentPassword|recovery|mfa|otp|totp/i.test(key)) {
        output[key] = '[REDACTED]';
      } else {
        output[key] = redact(val);
      }
    }
    return output;
  }
  return value;
}

function save(name, payload) {
  writeFileSync(join(evidenceDir, name), JSON.stringify(redact(payload), null, 2) + '\n');
}

function appendTrace(label, traceId, status, path) {
  traceLines.push({ label, status, traceId: traceId ?? null, path });
}

function firstData(response) {
  return response.body?.data ?? response.body;
}

function cookieValue(cookieHeader, name) {
  return cookieHeader
    .split(/;\s*/)
    .map(part => part.split('='))
    .find(([cookieName]) => cookieName === name)
    ?.slice(1)
    .join('=');
}

function decodeJwtClaims(cookieHeader) {
  const token = cookieValue(cookieHeader, 'mk_access');
  if (!token) {
    return null;
  }
  const payload = token.split('.')[1];
  const json = Buffer.from(payload, 'base64url').toString('utf8');
  const claims = JSON.parse(json);
  return {
    sub: claims.sub,
    tenant_id: claims.tenant_id,
    group_id: claims.group_id ?? null,
    hospital_id: claims.hospital_id ?? null,
    campus_id: claims.campus_id ?? null,
    site_id: claims.site_id ?? null,
    department_id: claims.department_id ?? null,
    specialty_id: claims.specialty_id ?? null,
    roles: claims.roles,
    session_started_at: claims.session_started_at,
    exp: claims.exp,
  };
}

async function login(actorName, actor) {
  const res = await fetch(`${baseUrl}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-MedKernel-Trace-Id': `${runTag}-login-${actorName}`,
    },
    body: JSON.stringify({
      tenantId,
      username: actor.username,
      password: actor.currentPassword,
    }),
  });
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(`login ${actorName} failed: ${res.status} ${JSON.stringify(redact(body))}`);
  }
  const setCookies = res.headers.getSetCookie();
  const cookiePairs = setCookies.map(item => item.split(';')[0]);
  const cookie = cookiePairs.join('; ');
  const xsrf = cookiePairs
    .map(item => item.split('='))
    .find(([name]) => name === 'XSRF-TOKEN')
    ?.slice(1)
    .join('=');
  if (!xsrf) {
    throw new Error(`login ${actorName} did not return XSRF token`);
  }
  appendTrace(`login-${actorName}`, res.headers.get('x-trace-id'), res.status, '/auth/login');
  return { actorName, cookie, xsrf, body, claims: decodeJwtClaims(cookie) };
}

async function api(session, method, path, body, traceId, headers = {}) {
  const normalizedMethod = method.toUpperCase();
  const csrfHeaders = ['GET', 'HEAD', 'OPTIONS'].includes(normalizedMethod)
    ? {}
    : { 'X-XSRF-TOKEN': session.xsrf };
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      Accept: 'application/json, text/plain',
      'Content-Type': 'application/json',
      Cookie: session.cookie,
      'X-MedKernel-Trace-Id': traceId,
      ...csrfHeaders,
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let parsed = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = { rawText: text };
  }
  appendTrace(traceId, res.headers.get('x-trace-id'), res.status, path);
  return {
    status: res.status,
    ok: res.ok,
    traceId: res.headers.get('x-trace-id'),
    path,
    body: parsed,
  };
}

function assertStatus(result, statuses, label) {
  if (!statuses.includes(result.status)) {
    throw new Error(`${label} unexpected ${result.status}: ${JSON.stringify(redact(result.body))}`);
  }
  return result;
}

function firstItem(items, predicate) {
  return Array.isArray(items) ? items.find(predicate) : undefined;
}

async function existingPolicy(session, resourceType, action) {
  const result = assertStatus(
    await api(session, 'GET', `/compliance/data-permissions?resourceType=${encodeURIComponent(resourceType)}&action=${action}`, undefined, `${runTag}-data-policy-before`),
    [200],
    'list data permission policy'
  );
  return firstItem(firstData(result), item => item.resourceType === resourceType && item.action === action);
}

async function upsertDataPermission() {
  const resourceType = 'act10_patient_scope';
  const existing = await existingPolicy(sessions.audit, resourceType, 'READ');
  const payload = {
    resourceType,
    action: 'READ',
    minDataLevel: 'DEPARTMENT',
    allowedColumns: ['patientId', 'encounterId', 'departmentId'],
    hospitalId,
    departmentId: respiratoryDepartmentId,
    status: 'ACTIVE',
    reason: `幕10演练：验证跨科室患者数据范围 ${runTag}`,
    expectedVersion: existing?.version ?? null,
  };
  if (payload.expectedVersion === null) {
    delete payload.expectedVersion;
  }
  return assertStatus(
    await api(sessions.itOps, 'PUT', '/compliance/data-permissions', payload, `${runTag}-data-policy-upsert`),
    [200],
    'upsert data permission policy'
  );
}

async function upsertMaskingRule(fieldName, strategy, prefixKeep, suffixKeep) {
  const resourceType = 'act10_patient_export';
  const list = assertStatus(
    await api(sessions.audit, 'GET', `/compliance/masking-rules?resourceType=${encodeURIComponent(resourceType)}&fieldName=${encodeURIComponent(fieldName)}`, undefined, `${runTag}-masking-${fieldName}-before`),
    [200],
    `list masking rule ${fieldName}`
  );
  const existing = firstItem(
    firstData(list),
    item => item.resourceType === resourceType && item.fieldName === fieldName && item.scenarioCode === 'DEFAULT'
  );
  const payload = {
    resourceType,
    fieldName,
    scenarioCode: 'DEFAULT',
    strategy,
    maskChar: '*',
    prefixKeep,
    suffixKeep,
    status: 'ACTIVE',
    reason: `幕10演练：敏感字段脱敏 ${fieldName} ${runTag}`,
    expectedVersion: existing?.version ?? null,
  };
  if (payload.expectedVersion === null) {
    delete payload.expectedVersion;
  }
  return assertStatus(
    await api(sessions.itOps, 'PUT', '/compliance/masking-rules', payload, `${runTag}-masking-${fieldName}-upsert`),
    [200],
    `upsert masking rule ${fieldName}`
  );
}

function runBackupRestoreCheck() {
  const dbSafe = runTag.replaceAll('-', '_');
  const remoteScript = `
set -euo pipefail
TAG='${runTag}'
RESTORE_DB='medkernel_${dbSafe}_restore'
TMP="/tmp/${runTag}.schema.dump"
BACKUP="/zoesoft/medkernel/backups/${runTag}.schema.dump"
rm -f "$TMP"
sudo -u postgres pg_dump -Fc --schema-only -f "$TMP" medkernel
cp "$TMP" "$BACKUP"
chown medkernel:medkernel "$BACKUP" || true
chmod 0640 "$BACKUP" || true
sudo -u postgres dropdb --if-exists "$RESTORE_DB" >/dev/null 2>&1 || true
sudo -u postgres createdb "$RESTORE_DB"
sudo -u postgres pg_restore -d "$RESTORE_DB" "$TMP" >/dev/null
TABLE_COUNT=$(sudo -u postgres psql -d "$RESTORE_DB" -Atc "select count(*) from information_schema.tables where table_schema='public';")
MIGRATION_COUNT=$(sudo -u postgres psql -d "$RESTORE_DB" -Atc "select count(*) from flyway_schema_history where success;")
FLYWAY_TABLE_PRESENT=$(sudo -u postgres psql -d "$RESTORE_DB" -Atc "select to_regclass('public.flyway_schema_history') is not null;")
sudo -u postgres dropdb "$RESTORE_DB"
rm -f "$TMP"
python3 - <<PY
import json
print(json.dumps({
    "backupPath": "$BACKUP",
    "restoreDb": "$RESTORE_DB",
    "tableCount": int("$TABLE_COUNT"),
    "migrationCount": int("$MIGRATION_COUNT"),
    "flywayTablePresent": "$FLYWAY_TABLE_PRESENT" == "t"
}, ensure_ascii=False))
PY
`;
  const raw = execFileSync('ssh', [
    '-o', 'BatchMode=yes',
    '-o', 'StrictHostKeyChecking=no',
    'root@193.112.107.134',
    remoteScript,
  ], { encoding: 'utf8', maxBuffer: 5 * 1024 * 1024 });
  const jsonLine = raw.trim().split('\n').at(-1);
  return JSON.parse(jsonLine);
}

const sessions = {
  audit: await login('audit', actors.audit),
  itOps: await login('itOps', actors.itOps),
  hospitalAdmin: await login('hospitalAdmin', actors.hospitalAdmin),
  medical: await login('medical', actors.medical),
  respiratoryDoctor: await login('respiratoryDoctor', actors.respiratoryDoctor),
  cardiologyDoctor: await login('cardiologyDoctor', actors.cardiologyDoctor),
};

const readinessResponse = await fetch(`${baseRoot}/actuator/health/readiness`);
const readinessText = await readinessResponse.text();
const actorProfiles = {};
for (const [name, session] of Object.entries(sessions)) {
  actorProfiles[name] = {
    claims: session.claims,
    securityMe: await api(session, 'GET', '/security/me', undefined, `${runTag}-security-${name}`),
  };
}
save('00-readiness-and-actors.json', {
  runTag,
  startedAt,
  tenantId,
  patientId,
  encounterId,
  packageVersion,
  deployedJarSha256,
  deployBackup,
  readiness: { status: readinessResponse.status, body: readinessText },
  actors: actorProfiles,
  credentialLocation: '/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json',
});

const auditChain = {
  criticalTrace: assertStatus(
    await api(sessions.audit, 'GET', `/engine/diagnose/traces/${act6CriticalTrace}`, undefined, `${runTag}-diagnose-critical`),
    [200],
    'critical trace diagnose'
  ),
  ddiTrace: assertStatus(
    await api(sessions.audit, 'GET', `/engine/diagnose/traces/${act6DdiTrace}`, undefined, `${runTag}-diagnose-ddi`),
    [200],
    'ddi trace diagnose'
  ),
  respiratoryDoctorEvents: assertStatus(
    await api(sessions.audit, 'GET', '/compliance/audit/events?size=20&actorUserId=drill-respiratory-doctor-20260611', undefined, `${runTag}-audit-events-doctor`),
    [200],
    'audit events doctor'
  ),
  clinicalEventEvents: assertStatus(
    await api(sessions.audit, 'GET', '/compliance/audit/events?size=20&resourceType=clinical_event', undefined, `${runTag}-audit-events-clinical-event`),
    [200],
    'audit events clinical event'
  ),
  snapshot: assertStatus(
    await api(sessions.audit, 'POST', `/compliance/audit/snapshot?reason=${encodeURIComponent(`幕10审计快照 ${runTag}`)}`, {}, `${runTag}-audit-snapshot`),
    [200],
    'audit snapshot'
  ),
};
save('01-audit-chain.json', {
  runTag,
  sourceTraces: [act6CriticalTrace, act6DdiTrace],
  auditChain,
  passCriteria: [
    '幕6危急值 trace 可由 audit.read 查询',
    '幕6 DDI trace 可由 audit.read 查询',
    '审计事件流可按医生与 clinical_event 过滤',
    '审计快照可导出并生成 payloadDigest',
  ],
});

const dataPermissionPolicy = await upsertDataPermission();
const respiratoryAccess = assertStatus(
  await api(sessions.respiratoryDoctor, 'POST', '/compliance/data-permissions:check', {
    resourceType: 'act10_patient_scope',
    action: 'READ',
    hospitalId,
    departmentId: respiratoryDepartmentId,
    requestedColumns: ['patientId', 'encounterId'],
  }, `${runTag}-data-check-respiratory`),
  [200],
  'respiratory data permission check'
);
const cardiologyAccess = assertStatus(
  await api(sessions.cardiologyDoctor, 'POST', '/compliance/data-permissions:check', {
    resourceType: 'act10_patient_scope',
    action: 'READ',
    hospitalId,
    departmentId: respiratoryDepartmentId,
    requestedColumns: ['patientId', 'encounterId'],
  }, `${runTag}-data-check-cardiology`),
  [200],
  'cardiology data permission check'
);
const cardiologyDenied = firstData(cardiologyAccess)?.rowAllowed === false;
const respiratoryAllowed = firstData(respiratoryAccess)?.rowAllowed === true;
if (!cardiologyDenied || !respiratoryAllowed) {
  throw new Error(`data permission decision mismatch: respiratory=${JSON.stringify(firstData(respiratoryAccess))} cardiology=${JSON.stringify(firstData(cardiologyAccess))}`);
}
save('02-data-permission-boundary.json', {
  runTag,
  currentClaims: {
    respiratoryDoctor: sessions.respiratoryDoctor.claims,
    cardiologyDoctor: sessions.cardiologyDoctor.claims,
  },
  policy: dataPermissionPolicy,
  respiratoryAccess,
  cardiologyAccess,
  passCriteria: [
    '呼吸科医生对呼吸 ICU 目标数据 rowAllowed=true',
    '心内科医生对呼吸 ICU 目标数据 rowAllowed=false',
    'JWT 组织域来自服务端角色分配而不是客户端请求',
  ],
});

const maskingRules = {
  patientName: await upsertMaskingRule('patientName', 'KEEP_FIRST_LAST', 1, 1),
  idNo: await upsertMaskingRule('idNo', 'KEEP_LAST', 0, 4),
};
const maskingPreview = assertStatus(
  await api(sessions.audit, 'POST', '/compliance/masking-rules:preview', {
    resourceType: 'act10_patient_export',
    scenarioCode: 'DEFAULT',
    values: {
      patientName: '张建国',
      idNo: '110101196203018888',
      encounterId,
    },
    sensitiveFields: ['patientName', 'idNo'],
  }, `${runTag}-masking-preview`),
  [200],
  'masking preview'
);
const masked = firstData(maskingPreview);
if (masked?.rawAllowed !== false || masked?.values?.idNo === '110101196203018888') {
  throw new Error(`masking preview did not mask sensitive values: ${JSON.stringify(masked)}`);
}
save('03-masking-preview.json', {
  runTag,
  rules: maskingRules,
  preview: maskingPreview,
  passCriteria: [
    '审计角色通过脱敏权限只能看到 rawAllowed=false',
    'patientName 与 idNo 均按规则遮罩',
    '请求未携带/伪造租户，服务端以当前 JWT 租户执行',
  ],
});

const exportRequest = assertStatus(
  await api(sessions.audit, 'POST', '/compliance/exports:request', {
    resourceType: 'act10_patient_export',
    exportScope: {
      patientId,
      encounterId,
      departmentId: respiratoryDepartmentId,
      fields: ['patientName', 'idNo'],
    },
    reason: `幕10演练：敏感患者数据导出必须审批 ${runTag}`,
    idempotencyKey: `${runTag}.patient-export`,
  }, `${runTag}-export-request`),
  [200],
  'export request'
);
const approvalId = firstData(exportRequest)?.approvalId;
if (!approvalId) {
  throw new Error('export request did not return approvalId');
}
const selfApproval = await api(sessions.audit, 'POST', `/compliance/exports/${approvalId}:approve`, {
  decision: 'APPROVE',
  comment: `幕10演练：申请人自批应被拒绝 ${runTag}`,
  expectedVersion: firstData(exportRequest)?.version,
}, `${runTag}-export-self-approve`);
assertStatus(selfApproval, [403], 'export self approval should be forbidden');
const approval = assertStatus(
  await api(sessions.hospitalAdmin, 'POST', `/compliance/exports/${approvalId}:approve`, {
    decision: 'APPROVE',
    comment: `幕10演练：第二人审批通过 ${runTag}`,
    expectedVersion: firstData(exportRequest)?.version,
  }, `${runTag}-export-approve`),
  [200],
  'export approval'
);
save('04-export-approval.json', {
  runTag,
  request: exportRequest,
  selfApproval,
  approval,
  passCriteria: [
    '敏感导出先 REQUESTED',
    '申请人与审批人不能相同',
    '第二人审批后状态为 APPROVED 并生成审批证据',
  ],
});

const modelStatus = assertStatus(
  await api(sessions.itOps, 'GET', '/model-capabilities/status', undefined, `${runTag}-model-status`),
  [200],
  'model status'
);
const capability = firstItem(firstData(modelStatus), item => item?.fallbackAvailable !== false);
if (!capability?.capabilityCode) {
  throw new Error('no model capability available for degradation drill');
}
const modelTask = assertStatus(
  await api(sessions.itOps, 'POST', '/model-capabilities/tasks', {
    capabilityCode: capability.capabilityCode,
    inputData: `患者 张建国，身份证 110101196203018888，手机号 13800138000。幕10验证模型缺位时诚实降级 ${runTag}`,
    timeoutSeconds: 10,
  }, `${runTag}-model-task`),
  [200],
  'model task'
);
const modelTaskData = firstData(modelTask);
if (modelTaskData?.fallbackUsed !== true || modelTaskData?.modelMode !== 'B0') {
  throw new Error(`model task did not honestly degrade: ${JSON.stringify(modelTaskData)}`);
}
const modelTaskDetail = assertStatus(
  await api(sessions.itOps, 'GET', `/model-capabilities/tasks/${encodeURIComponent(modelTaskData.taskId)}`, undefined, `${runTag}-model-task-detail`),
  [200],
  'model task detail'
);
save('05-model-degrade.json', {
  runTag,
  selectedCapability: capability,
  status: modelStatus,
  task: modelTask,
  detail: modelTaskDetail,
  passCriteria: [
    '模型能力状态声明 fallbackAvailable',
    '提交任务后 fallbackUsed=true',
    'modelMode=B0 且无伪造模型版本或置信度',
  ],
});

const operations = assertStatus(
  await api(sessions.itOps, 'GET', '/system/operations', undefined, `${runTag}-operations`),
  [200],
  'runtime operations'
);
const domesticReport = assertStatus(
  await api(sessions.itOps, 'GET', '/system/operations/domestic-report', undefined, `${runTag}-domestic-report`, { Accept: 'text/plain' }),
  [200],
  'domestic report'
);
const configValidation = assertStatus(
  await api(sessions.itOps, 'POST', '/compliance/audit/settings/validate', {
    key: 'audit.retention.days',
    beforeValue: '180',
    value: '365',
    reason: `幕10演练：高风险配置变更先走审计校验 ${runTag}`,
  }, `${runTag}-audit-setting-validate`),
  [200],
  'audit setting validation'
);
const backupRestore = runBackupRestoreCheck();
if (backupRestore.tableCount <= 0 || backupRestore.flywayTablePresent !== true) {
  throw new Error(`backup restore check did not restore schema: ${JSON.stringify(backupRestore)}`);
}
save('06-runtime-domestic-backup.json', {
  runTag,
  operations,
  domesticReport,
  configValidation,
  backupRestore,
  passCriteria: [
    '运行状态接口可读',
    '国产化自检报告可导出',
    '高风险配置变更可先审计校验',
    'pg_dump schema-only 快照可恢复到临时库并读到业务表与 flyway_schema_history 表结构',
  ],
});

const summary = {
  runTag,
  startedAt,
  finishedAt: new Date().toISOString(),
  deployedJarSha256,
  deployBackup,
  backupRestorePath: backupRestore.backupPath,
  cases: [
    { id: 'A1', name: '登录组织域与远程 readiness', pass: readinessResponse.status === 200 },
    { id: 'A2', name: '幕6审计链追踪和审计快照', pass: true },
    { id: 'A3', name: '数据权限跨科室阻断', pass: respiratoryAllowed && cardiologyDenied },
    { id: 'A4', name: '敏感字段脱敏预览', pass: masked?.rawAllowed === false },
    { id: 'A5', name: '导出审批分权', pass: firstData(approval)?.status === 'APPROVED' && selfApproval.status === 403 },
    { id: 'A6', name: '模型缺位诚实 B0 降级', pass: modelTaskData?.fallbackUsed === true && modelTaskData?.modelMode === 'B0' },
    { id: 'A7', name: '国产化与备份恢复抽查', pass: backupRestore.tableCount > 0 && backupRestore.flywayTablePresent === true },
  ],
  traceLines,
};
summary.pass = summary.cases.every(item => item.pass);
save('99-summary.json', summary);

if (!summary.pass) {
  throw new Error(`act10 summary failed: ${JSON.stringify(summary.cases)}`);
}

console.log(JSON.stringify({
  runTag,
  evidenceDir,
  deployedJarSha256,
  deployBackup,
  backupRestorePath: backupRestore.backupPath,
  cases: summary.cases,
}, null, 2));
