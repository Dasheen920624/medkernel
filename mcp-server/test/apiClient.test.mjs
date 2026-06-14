import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createClient, McpApiError } from '../src/apiClient.mjs';

function scriptedFetch(response) {
  const calls = [];
  const impl = async (url, options) => {
    calls.push({ url, options });
    if (typeof response === 'function') {
      throw response();
    }
    return {
      ok: response.status >= 200 && response.status < 300,
      status: response.status,
      text: async () => (response.body == null ? '' : JSON.stringify(response.body)),
    };
  };
  impl.calls = calls;
  return impl;
}

test('executeTool POST 到受控工具执行入口并携 Bearer 令牌', async () => {
  const fetchImpl = scriptedFetch({
    status: 200,
    body: { success: true, data: { toolName: 'explainRule' } },
  });
  const client = createClient({ baseUrl: 'https://api.example.org', token: 'tok-1', fetchImpl });

  const envelope = await client.executeTool('explainRule', { purpose: 'Agent 解释', target: 'R-7' });

  assert.equal(fetchImpl.calls[0].url, 'https://api.example.org/api/v1/engine-data/tools/explainRule:execute');
  assert.equal(fetchImpl.calls[0].options.headers.Authorization, 'Bearer tok-1');
  assert.equal(envelope.toolName, 'explainRule');
});

test('非 2xx ProblemDetail 转结构化 McpApiError 不泄漏内部', async () => {
  const fetchImpl = scriptedFetch({
    status: 403,
    body: { title: 'Forbidden', status: 403, detail: '无权限执行该操作', code: 'ENG-API-004' },
  });
  const client = createClient({ baseUrl: 'https://api.example.org', token: 'tok-1', fetchImpl });

  await assert.rejects(
    () => client.get('/api/v1/engine-data/tools'),
    (err) => {
      assert.ok(err instanceof McpApiError);
      assert.equal(err.status, 403);
      assert.equal(err.code, 'ENG-API-004');
      assert.doesNotMatch(err.message, /SQL|stack|Exception/);
      return true;
    },
  );
});

test('后端不可达诚实报错不伪造数据', async () => {
  const fetchImpl = scriptedFetch(() => new Error('ECONNREFUSED'));
  const client = createClient({ baseUrl: 'https://api.example.org', token: 'tok-1', fetchImpl });

  await assert.rejects(
    () => client.get('/api/v1/engine-data/tools'),
    (err) => err instanceof McpApiError && /不可达/.test(err.message),
  );
});
