import assert from 'node:assert/strict';
import { test } from 'node:test';

import { createDispatcher } from '../src/server.mjs';

function fakeClient() {
  return {
    get: async () => [{ name: 'queryRuleUsage', purpose: 'p', dataLevel: 'D2', requiredPermission: 'engine-data.read' }],
    executeTool: async (name) => ({ toolName: name }),
  };
}

function collector() {
  const writes = [];
  return { writes, write: (obj) => writes.push(obj) };
}

test('dispatch initialize 写出一条 JSON-RPC 响应', async () => {
  const sink = collector();
  const dispatch = createDispatcher(fakeClient(), sink);
  await dispatch(JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {} }));
  assert.equal(sink.writes.length, 1);
  assert.equal(sink.writes[0].result.serverInfo.name, 'medkernel-mcp');
});

test('dispatch 通知（无 id）不写响应', async () => {
  const sink = collector();
  const dispatch = createDispatcher(fakeClient(), sink);
  await dispatch(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }));
  assert.equal(sink.writes.length, 0);
});

test('dispatch 空行忽略', async () => {
  const sink = collector();
  const dispatch = createDispatcher(fakeClient(), sink);
  await dispatch('   ');
  assert.equal(sink.writes.length, 0);
});

test('dispatch 非法 JSON 写 JSON-RPC 解析错误 -32700', async () => {
  const sink = collector();
  const dispatch = createDispatcher(fakeClient(), sink);
  await dispatch('这不是 JSON');
  assert.equal(sink.writes.length, 1);
  assert.equal(sink.writes[0].error.code, -32700);
});
