import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createClient, CliApiError } from '../src/apiClient.mjs';

/** 受脚本控制的 fetch 替身：记录请求、按预设返回 Response 形态。 */
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

test('get 携带 Bearer 令牌并解包 ApiResult.data', async () => {
  const fetchImpl = scriptedFetch({
    status: 200,
    body: { success: true, code: 'OK', message: 'ok', data: { tools: ['queryRuleUsage'] }, traceId: 't-1' },
  });
  const client = createClient({ baseUrl: 'https://api.example.org', token: 'tok-1', fetchImpl });

  const data = await client.get('/api/v1/engine-data/tools');

  assert.deepEqual(data, { tools: ['queryRuleUsage'] });
  assert.equal(fetchImpl.calls[0].url, 'https://api.example.org/api/v1/engine-data/tools');
  assert.equal(fetchImpl.calls[0].options.headers.Authorization, 'Bearer tok-1');
});

test('executeTool POST 到受控工具执行入口并带用途与目标', async () => {
  const fetchImpl = scriptedFetch({
    status: 200,
    body: { success: true, code: 'OK', message: 'ok', data: { toolName: 'searchKnowledge', payload: {} } },
  });
  const client = createClient({ baseUrl: 'https://api.example.org', token: 'tok-1', fetchImpl });

  const envelope = await client.executeTool('searchKnowledge', { purpose: 'CLI 检索知识', target: '糖尿病' });

  assert.equal(fetchImpl.calls[0].url, 'https://api.example.org/api/v1/engine-data/tools/searchKnowledge:execute');
  assert.equal(fetchImpl.calls[0].options.method, 'POST');
  const sentBody = JSON.parse(fetchImpl.calls[0].options.body);
  assert.equal(sentBody.purpose, 'CLI 检索知识');
  assert.equal(sentBody.target, '糖尿病');
  assert.equal(envelope.toolName, 'searchKnowledge');
});

test('非 2xx ProblemDetail 转结构化 CliApiError 不泄漏内部', async () => {
  const fetchImpl = scriptedFetch({
    status: 403,
    body: { type: 'about:blank', title: 'Forbidden', status: 403, detail: '无权限执行该操作', code: 'ENG-API-004' },
  });
  const client = createClient({ baseUrl: 'https://api.example.org', token: 'tok-1', fetchImpl });

  await assert.rejects(
    () => client.get('/api/v1/engine-data/rule-usage'),
    (err) => {
      assert.ok(err instanceof CliApiError);
      assert.equal(err.status, 403);
      assert.equal(err.code, 'ENG-API-004');
      assert.match(err.message, /无权限/);
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
    (err) => err instanceof CliApiError && /不可达/.test(err.message),
  );
});
