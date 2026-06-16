import assert from 'node:assert/strict';
import { test } from 'node:test';

import { runCommand, CliUsageError } from '../src/commands.mjs';

function fakeClient() {
  const calls = { get: [], post: [], executeTool: [] };
  const client = {
    calls,
    get: async (path) => {
      calls.get.push(path);
      // 对齐真实契约：GET /tools 解包 ApiResult.data 得受控工具数组；统计端点得响应对象。
      if (path.endsWith('/tools')) {
        return [{ name: 'queryRuleUsage', dataLevel: 'D2' }];
      }
      return { dataLevel: 'D2', total: 0, rows: [] };
    },
    post: async (path, body) => {
      calls.post.push({ path, body });
      return { jobCode: 'job-1', status: 'PENDING' };
    },
    executeTool: async (name, body) => {
      calls.executeTool.push({ name, body });
      return { toolName: name, dataLevel: 'D1', payload: {} };
    },
  };
  return client;
}

test('diagnostics 经 /tools 列举受控工具目录', async () => {
  const client = fakeClient();
  const result = await runCommand(client, 'diagnostics', undefined, [], { purpose: 'CLI 自检' });
  assert.equal(client.calls.get[0], '/api/v1/engine-data/tools');
  assert.ok(Array.isArray(result.tools));
});

test('rules explain 派发 explainRule 受控工具并带目标规则', async () => {
  const client = fakeClient();
  await runCommand(client, 'rules', 'explain', ['R-7'], { purpose: 'CLI 解释规则' });
  assert.deepEqual(client.calls.executeTool[0], {
    name: 'explainRule',
    body: { purpose: 'CLI 解释规则', target: 'R-7' },
  });
});

test('knowledge search 派发 searchKnowledge 并带关键词', async () => {
  const client = fakeClient();
  await runCommand(client, 'knowledge', 'search', ['糖尿病'], { purpose: 'CLI 检索知识' });
  assert.deepEqual(client.calls.executeTool[0], {
    name: 'searchKnowledge',
    body: { purpose: 'CLI 检索知识', target: '糖尿病' },
  });
});

test('privacy validate 派发 validatePrivacyPolicy 并带分级', async () => {
  const client = fakeClient();
  await runCommand(client, 'privacy', 'validate', ['D5'], { purpose: 'CLI 验证脱敏策略' });
  assert.deepEqual(client.calls.executeTool[0], {
    name: 'validatePrivacyPolicy',
    body: { purpose: 'CLI 验证脱敏策略', target: 'D5' },
  });
});

test('clinical-signals list 经只读统计端点取数', async () => {
  const client = fakeClient();
  await runCommand(client, 'clinical-signals', 'list', [], { purpose: 'CLI 查看信号' });
  assert.equal(client.calls.get[0], '/api/v1/engine-data/clinical-signals');
});

test('agent submit-candidate 派发 submitProductionCandidate 受控工具并携带结构化候选载荷', async () => {
  const client = fakeClient();
  const payload = {
    jobCode: 'job-agent',
    idempotencyKey: 'idem-agent-1',
    dataLevel: 'D1',
    submission: {
      candidate: {
        assetType: 'RULE',
        assetIdentity: 'rule:agent:1',
        subject: 'Agent 回写规则候选',
        versionLabel: 'agent-draft-v1',
        sources: [{ sourceRef: 'GL-HTN-2024:v1:section-1', authorityLevel: 'B_GUIDELINE' }],
        trustLevel: 'B_GUIDELINE',
        riskLevel: 'MEDIUM',
        orgScope: 'tenant-1',
        contentHash: 'a'.repeat(64),
        payload: '{"aiGenerated":true}',
        lifecycleStatus: 'DRAFT',
      },
      target: { targetIdentityId: 77 },
    },
  };

  await runCommand(client, 'agent', 'submit-candidate', [JSON.stringify(payload)], {
    purpose: 'Agent 受控回写',
  });

  assert.deepEqual(client.calls.executeTool[0], {
    name: 'submitProductionCandidate',
    body: { purpose: 'Agent 受控回写', payload },
  });
});

test('agent submit-candidate 候选载荷非 JSON 时结构化拒绝', async () => {
  const client = fakeClient();
  await assert.rejects(
    () => runCommand(client, 'agent', 'submit-candidate', ['not-json'], {}),
    (err) => err instanceof CliUsageError && /payloadJson/.test(err.message),
  );
  assert.equal(client.calls.executeTool.length, 0);
});

test('exports submit 经审批闸控制的导出端点，带审批ID与幂等键', async () => {
  const client = fakeClient();
  await runCommand(client, 'exports', 'submit', ['RULE_USAGE', 'exp-1', 'idem-1'], { windowDays: '90' });
  assert.deepEqual(client.calls.post[0], {
    path: '/api/v1/engine-data/exports',
    body: { exportType: 'RULE_USAGE', windowDays: 90, approvalId: 'exp-1', idempotencyKey: 'idem-1' },
  });
});

test('exports submit 缺审批ID结构化拒绝（不绕审批、不伪造）', async () => {
  const client = fakeClient();
  await assert.rejects(
    () => runCommand(client, 'exports', 'submit', ['RULE_USAGE'], {}),
    (err) => err instanceof CliUsageError && /approvalId/.test(err.message),
  );
  assert.equal(client.calls.post.length, 0);
});

test('exports status 经状态端点按 jobCode 查', async () => {
  const client = fakeClient();
  await runCommand(client, 'exports', 'status', ['job-1'], {});
  assert.equal(client.calls.get[0], '/api/v1/engine-data/exports/job-1');
});

test('exports list 经列表端点取近期作业', async () => {
  const client = fakeClient();
  await runCommand(client, 'exports', 'list', [], {});
  assert.equal(client.calls.get[0], '/api/v1/engine-data/exports');
});

test('exports complete 走合规导出审批登记端点，不绕审批', async () => {
  const client = fakeClient();
  await runCommand(client, 'exports', 'complete', ['exp-1', 'job-1'], {});
  assert.equal(client.calls.post[0].path, '/api/v1/compliance/exports/exp-1:complete-from-job');
  assert.equal(client.calls.post[0].body.jobId, 'job-1');
});

test('exports 未知动作结构化拒绝', async () => {
  const client = fakeClient();
  await assert.rejects(
    () => runCommand(client, 'exports', 'wipe', [], {}),
    (err) => err instanceof CliUsageError,
  );
});

test('未知命令域结构化拒绝', async () => {
  const client = fakeClient();
  await assert.rejects(
    () => runCommand(client, 'dropdb', undefined, [], {}),
    (err) => err instanceof CliUsageError,
  );
});
