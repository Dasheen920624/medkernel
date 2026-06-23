// GA-PERF-01 · MedKernel v1.0 GA 1000 并发 60 min 压测脚本
// 工具：k6 (https://k6.io)
// 使用：k6 run docs/performance/k6-1000-concurrent.js
//      或 k6 run -e BASE_URL=https://medkernel-staging.your-hospital.cn docs/performance/k6-1000-concurrent.js
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
  // 临床运行域：占总流量 60%
  { weight: 30, name: 'mpi-search', run: () => mpiSearch() },
  { weight: 15, name: 'cdss-alerts', run: () => cdssAlerts() },
  { weight: 10, name: 'rule-validate', run: () => ruleValidate() },
  { weight: 5, name: 'patient-pathways', run: () => patientPathways() },

  // 试点准备域：占 15%
  { weight: 8, name: 'pathway-list', run: () => pathwayList() },
  { weight: 7, name: 'runtime-releases', run: () => runtimeReleases() },

  // 质控改进域：占 12%
  { weight: 8, name: 'qc-dashboard', run: () => qcDashboard() },
  { weight: 4, name: 'insurance-audit', run: () => insuranceAudit() },

  // 合规运维域：占 8%
  { weight: 5, name: 'audit-events', run: () => auditEvents() },
  { weight: 3, name: 'audit-snapshot', run: () => auditSnapshot() },

  // 高级工具：占 5%（含 LLM Gateway 降级链）
  { weight: 3, name: 'llm-providers', run: () => llmProviders() },
  { weight: 2, name: 'llm-chat', run: () => llmChat() },
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
  const res = http.get(`${BASE_URL}/api/v1/engine/mpi/patients?keyword=12`);
  track(res, mpiLatency);
}
function cdssAlerts() {
  const res = http.get(`${BASE_URL}/api/v1/clinical/cdss/alerts`);
  track(res, cdssLatency);
}
function ruleValidate() {
  const res = http.post(
    `${BASE_URL}/api/v1/tenant/rules/validate`,
    JSON.stringify({ patientMpi: 'MPI-000123456', orderText: '头孢曲松 1g qd' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  track(res, ruleValidateLatency);
}
function patientPathways() {
  const res = http.get(`${BASE_URL}/api/v1/clinical/cdss/alerts`);
  track(res);
}
function pathwayList() {
  const res = http.get(`${BASE_URL}/api/v1/tenant/pathways`);
  track(res);
}
function runtimeReleases() {
  const res = http.get(`${BASE_URL}/api/v1/engine/releases/platform-baselines/current`);
  track(res);
}
function qcDashboard() {
  const res = http.get(`${BASE_URL}/api/v1/engine/mpi/stats`);
  track(res);
}
function insuranceAudit() {
  const res = http.get(`${BASE_URL}/api/v1/quality/insurance/drg/rulesets`);
  track(res);
}
function auditEvents() {
  const res = http.get(`${BASE_URL}/api/v1/compliance/audit/events`);
  track(res);
}
function auditSnapshot() {
  const res = http.post(`${BASE_URL}/api/v1/compliance/audit/snapshot?reason=k6-perf`);
  track(res);
}
function llmProviders() {
  const res = http.get(`${BASE_URL}/api/v1/advanced/llm/providers`);
  track(res);
}
function llmChat() {
  const res = http.post(
    `${BASE_URL}/api/v1/advanced/llm/chat`,
    JSON.stringify({ prompt: '胸痛 AMI 患者建议' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  track(res);
}
