// GA-PERF-01 · 模型能力网关降级链压测
// 模拟主模型不可用时，验证模型任务进入 B0 或备用路由后的端到端 P95
//
// 使用：k6 run docs/handbook/performance/k6-llm-degradation.js
//
// 验收：
// - 主备切换 P95 < 1.5s
// - 错误率 < 0.5%

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080/medkernel';
const AUTH_TOKEN = __ENV.MEDKERNEL_AUTH_TOKEN || __ENV.AUTH_TOKEN || '';
const JSON_HEADERS = {
  'Content-Type': 'application/json',
  ...(AUTH_TOKEN ? { Authorization: `Bearer ${AUTH_TOKEN}` } : {}),
};

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '10m', target: 100 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'],
    http_req_failed: ['rate<0.005'],
  },
};

const PROMPTS = [
  '请从已登记指南来源中提取适合人工审核的知识候选摘要。',
  '请检查一段脱敏病历摘要是否存在内涵质控缺项。',
  '请生成标准术语映射候选的人工核查说明。',
  '请把确定性规则命中证据转换为可追溯说明。',
];

export default function () {
  const prompt = PROMPTS[Math.floor(Math.random() * PROMPTS.length)];
  const res = http.post(
    `${BASE_URL}/api/v1/model-capabilities/tasks`,
    JSON.stringify({
      capabilityCode: 'knowledge.production.knowledge',
      inputData: prompt,
      timeoutSeconds: 10,
      requiredRouteStrategy: 'AUTO',
      authoritativeOutputContext: '性能压测只校验模型任务降级链，不生成临床事实或医嘱。',
    }),
    { headers: JSON_HEADERS },
  );
  check(res, {
    'status 200': (r) => r.status === 200,
    'has task state': (r) => {
      try {
        const parsed = JSON.parse(r.body);
        return parsed.data?.status != null || parsed.data?.egressConfirmation != null;
      } catch {
        return false;
      }
    },
  });
  sleep(Math.random());
}
