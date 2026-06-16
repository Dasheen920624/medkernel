import assert from 'node:assert/strict';
import { test } from 'node:test';

import { handleRequest, PROTOCOL_VERSION } from '../src/protocol.mjs';

function fakeClient(overrides = {}) {
  const calls = { get: [], executeTool: [] };
  const client = {
    calls,
    get: async (path) => {
      calls.get.push(path);
      return [
        { name: 'queryRuleUsage', purpose: '查询规则使用聚合统计', dataLevel: 'D2', requiredPermission: 'engine-data.read' },
        { name: 'explainRule', purpose: '解释单条规则', dataLevel: 'D1', requiredPermission: 'engine-data.read' },
        { name: 'submitProductionCandidate', purpose: 'Agent 受控回写候选', dataLevel: 'D1', requiredPermission: 'knowledge.write' },
      ];
    },
    executeTool: async (name, body) => {
      calls.executeTool.push({ name, body });
      return { toolName: name, dataLevel: 'D1', degraded: false, outputHash: 'abc', payload: {} };
    },
    ...overrides,
  };
  return client;
}

test('initialize 返回协议版本、tools 能力与 serverInfo', async () => {
  const res = await handleRequest({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {} }, { client: fakeClient() });
  assert.equal(res.id, 1);
  assert.equal(res.result.protocolVersion, PROTOCOL_VERSION);
  assert.ok(res.result.capabilities.tools);
  assert.equal(res.result.serverInfo.name, 'medkernel-mcp');
});

test('tools/list 经后端 /tools 映射为 MCP 工具（含 inputSchema，purpose 必填）', async () => {
  const client = fakeClient();
  const res = await handleRequest({ jsonrpc: '2.0', id: 2, method: 'tools/list' }, { client });

  assert.equal(client.calls.get[0], '/api/v1/engine-data/tools');
  assert.equal(res.result.tools.length, 3);
  const explain = res.result.tools.find((t) => t.name === 'explainRule');
  assert.match(explain.description, /D1/);
  assert.equal(explain.inputSchema.type, 'object');
  assert.deepEqual(explain.inputSchema.required, ['purpose']);
  const submit = res.result.tools.find((t) => t.name === 'submitProductionCandidate');
  assert.equal(submit.inputSchema.properties.payload.type, 'object');
});

test('tools/call 派发后端受控工具执行并以 text content 返回信封', async () => {
  const client = fakeClient();
  const res = await handleRequest(
    { jsonrpc: '2.0', id: 3, method: 'tools/call', params: { name: 'explainRule', arguments: { purpose: 'Agent 解释', target: 'R-7' } } },
    { client },
  );

  assert.deepEqual(client.calls.executeTool[0], { name: 'explainRule', body: { purpose: 'Agent 解释', target: 'R-7' } });
  assert.equal(res.result.content[0].type, 'text');
  const envelope = JSON.parse(res.result.content[0].text);
  assert.equal(envelope.toolName, 'explainRule');
  assert.notEqual(res.result.isError, true);
});

test('tools/call 后端错误以 isError 结果返回不泄漏内部', async () => {
  const client = fakeClient({
    executeTool: async () => {
      const err = new Error('无权限执行该操作');
      throw err;
    },
  });
  const res = await handleRequest(
    { jsonrpc: '2.0', id: 4, method: 'tools/call', params: { name: 'explainRule', arguments: { purpose: 'x', target: 'R-1' } } },
    { client },
  );

  assert.equal(res.result.isError, true);
  assert.match(res.result.content[0].text, /无权限/);
  assert.doesNotMatch(res.result.content[0].text, /SQL|stack/);
});

test('notifications/initialized 不返回响应', async () => {
  const res = await handleRequest({ jsonrpc: '2.0', method: 'notifications/initialized' }, { client: fakeClient() });
  assert.equal(res, null);
});

test('未知方法返回 JSON-RPC -32601', async () => {
  const res = await handleRequest({ jsonrpc: '2.0', id: 9, method: 'evil/dropTables' }, { client: fakeClient() });
  assert.equal(res.error.code, -32601);
});
