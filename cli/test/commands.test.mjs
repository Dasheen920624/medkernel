import assert from 'node:assert/strict';
import { test } from 'node:test';

import { runCommand, CliUsageError } from '../src/commands.mjs';

function fakeClient() {
  const calls = { get: [], executeTool: [] };
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

test('exports 诚实标后端未实现，不调后端、不伪造任务', async () => {
  const client = fakeClient();
  const result = await runCommand(client, 'exports', 'submit', [], { purpose: 'CLI 导出' });
  // 不调后端、不伪造任务 id（铁律 #1）。
  assert.equal(client.calls.get.length, 0);
  assert.equal(client.calls.executeTool.length, 0);
  assert.equal(result.implemented, false);
  assert.match(result.message, /未实现|不绕过导出审批/);
  assert.equal(result.taskId, undefined);
});

test('未知命令域结构化拒绝', async () => {
  const client = fakeClient();
  await assert.rejects(
    () => runCommand(client, 'dropdb', undefined, [], {}),
    (err) => err instanceof CliUsageError,
  );
});
