// GA-PERF-01 · MedKernel v1.0 GA 1000 并发 60 min 压测脚本
// 工具：k6 (https://k6.io)
// 使用：k6 run docs/handbook/performance/k6-1000-concurrent.js
//      或 k6 run -e BASE_URL=https://medkernel-staging.your-hospital.cn docs/handbook/performance/k6-1000-concurrent.js
//
// 验收硬指标（与 docs/CONSTITUTION.md 性能基线对齐）：
// - 1000 并发 60 min 无错误
// - 核心 API P95 < 300ms / P99 < 800ms
// - 错误率 < 0.1%
// - 0 连接池泄漏（HikariCP 监控 leak detection）

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080/medkernel';
const AUTH_TOKEN = __ENV.MEDKERNEL_AUTH_TOKEN || __ENV.AUTH_TOKEN || '';
const JSON_HEADERS = {
  'Content-Type': 'application/json',
  ...(AUTH_TOKEN ? { Authorization: `Bearer ${AUTH_TOKEN}` } : {}),
};
const GET_PARAMS = AUTH_TOKEN ? { headers: { Authorization: `Bearer ${AUTH_TOKEN}` } } : {};

// 自定义指标
const cdssLatency = new Trend('cdss_p95_latency');
const mpiLatency = new Trend('mpi_p95_latency');
const ruleValidateLatency = new Trend('rule_validate_p95_latency');
const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '2m', target: 100 },     // 预热 100
    { duration: '5m', target: 500 },     // 爬坡 500
    { duration: '5m', target: 1000 },    // 爬到 1000
    { duration: '60m', target: 1000 },   // 1000 并发持续 60 分钟（GA-PERF-01 主指标）
    { duration: '3m', target: 0 },       // 缓降
  ],
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<800'],
    http_req_failed: ['rate<0.001'],
    cdss_p95_latency: ['p(95)<200'],
    mpi_p95_latency: ['p(95)<150'],
    rule_validate_p95_latency: ['p(95)<400'],
    errors: ['rate<0.001'],
  },
};

const SCENARIOS = [
  // 医疗引擎：占总流量 55%
  { weight: 30, name: 'mpi-search', run: () => mpiSearch() },
  { weight: 15, name: 'cdss-alerts', run: () => cdssAlerts() },
  { weight: 10, name: 'rule-validate', run: () => ruleValidate() },

  // 知识生产：占 20%
  { weight: 8, name: 'pathway-list', run: () => pathwayList() },
  { weight: 7, name: 'runtime-releases', run: () => runtimeReleases() },
  { weight: 5, name: 'model-capability-status', run: () => modelCapabilityStatus() },

  // 质量管理：占 12%
  { weight: 8, name: 'qc-dashboard', run: () => qcDashboard() },
  { weight: 4, name: 'insurance-audit', run: () => insuranceAudit() },

  // 平台管理：占 8%
  { weight: 5, name: 'audit-events', run: () => auditEvents() },
  { weight: 3, name: 'audit-snapshot', run: () => auditSnapshot() },

  // 模型能力与证据诊断：占 5%（含模型能力网关降级链）
  { weight: 3, name: 'model-providers', run: () => modelProviders() },
  { weight: 2, name: 'model-task', run: () => modelTask() },
];

export default function () {
  const r = Math.random() * 100;
  let cum = 0;
  for (const s of SCENARIOS) {
    cum += s.weight;
    if (r <= cum) {
      s.run();
      break;
    }
  }
  sleep(Math.random() * 2);
}

function track(res, customMetric) {
  const ok = check(res, { 'status 200': (r) => r.status === 200 });
  errorRate.add(!ok);
  if (customMetric) customMetric.add(res.timings.duration);
}

function mpiSearch() {
  const res = http.get(`${BASE_URL}/api/v1/engine/mpi/patients?keyword=12`, GET_PARAMS);
  track(res, mpiLatency);
}
function cdssAlerts() {
  const res = http.get(`${BASE_URL}/api/v1/clinical/cdss/alerts`, GET_PARAMS);
  track(res, cdssLatency);
}
function ruleValidate() {
  const res = http.post(
    `${BASE_URL}/api/v1/tenant/rules/validate`,
    JSON.stringify({ patientMpi: 'PERF-MPI-0001', orderText: '受控性能压测医嘱文本' }),
    { headers: JSON_HEADERS },
  );
  track(res, ruleValidateLatency);
}
function pathwayList() {
  const res = http.get(`${BASE_URL}/api/v1/tenant/pathways`, GET_PARAMS);
  track(res);
}
function runtimeReleases() {
  const res = http.get(`${BASE_URL}/api/v1/engine/releases/platform-baselines/current`, GET_PARAMS);
  track(res);
}
function modelCapabilityStatus() {
  const res = http.get(`${BASE_URL}/api/v1/model-capabilities/status`, GET_PARAMS);
  track(res);
}
function qcDashboard() {
  const res = http.get(`${BASE_URL}/api/v1/engine/quality/dashboard`, GET_PARAMS);
  track(res);
}
function insuranceAudit() {
  const res = http.get(`${BASE_URL}/api/v1/engine/quality/insurance-issues?page=1&size=20`, GET_PARAMS);
  track(res);
}
function auditEvents() {
  const res = http.get(`${BASE_URL}/api/v1/compliance/audit/events?page=1&size=20`, GET_PARAMS);
  track(res);
}
function auditSnapshot() {
  const res = http.post(
    `${BASE_URL}/api/v1/compliance/audit/snapshot?reason=k6-perf`,
    null,
    { headers: JSON_HEADERS },
  );
  track(res);
}
function modelProviders() {
  const res = http.get(`${BASE_URL}/api/v1/model-providers?page=1&size=20`, GET_PARAMS);
  track(res);
}
function modelTask() {
  const res = http.post(
    `${BASE_URL}/api/v1/model-capabilities/tasks`,
    JSON.stringify({
      capabilityCode: 'knowledge.production.knowledge',
      inputData: '请基于已登记权威来源生成一段可供人工审核的知识候选摘要。',
      timeoutSeconds: 10,
      requiredRouteStrategy: 'AUTO',
      authoritativeOutputContext: '性能压测仅校验模型能力网关、降级与结构化响应，不写入临床事实。',
    }),
    { headers: JSON_HEADERS },
  );
  track(res);
}
