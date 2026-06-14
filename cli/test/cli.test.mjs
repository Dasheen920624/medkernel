import assert from 'node:assert/strict';
import { test } from 'node:test';

import { main, parseArgs } from '../src/cli.mjs';

const ENV = { MEDKERNEL_API_BASE: 'https://api.example.org', MEDKERNEL_API_TOKEN: 'tok-1' };

function okFetch(body) {
  return async () => ({ ok: true, status: 200, text: async () => JSON.stringify({ success: true, data: body }) });
}

test('parseArgs 解析命令域/动作/位置参/选项', () => {
  const parsed = parseArgs(['rules', 'explain', 'R-7', '--purpose', 'CLI 解释']);
  assert.equal(parsed.domain, 'rules');
  assert.equal(parsed.action, 'explain');
  assert.deepEqual(parsed.positional, ['R-7']);
  assert.equal(parsed.options.purpose, 'CLI 解释');
});

test('无命令域时打印帮助，返回 0', async () => {
  const code = await main([], ENV);
  assert.equal(code, 0);
});

test('缺少配置返回退出码 2', async () => {
  const code = await main(['diagnostics'], {});
  assert.equal(code, 2);
});

test('diagnostics 成功返回退出码 0', async () => {
  const code = await main(['diagnostics'], ENV, { fetchImpl: okFetch([{ name: 'queryRuleUsage' }]) });
  assert.equal(code, 0);
});

test('未知命令域返回退出码 2（用法错误）', async () => {
  const code = await main(['dropdb'], ENV, { fetchImpl: okFetch({}) });
  assert.equal(code, 2);
});

test('后端不可达返回退出码 1（请求失败）', async () => {
  const downFetch = async () => {
    throw new Error('ECONNREFUSED');
  };
  const code = await main(['diagnostics'], ENV, { fetchImpl: downFetch });
  assert.equal(code, 1);
});
