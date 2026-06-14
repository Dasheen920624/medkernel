import assert from 'node:assert/strict';
import { test } from 'node:test';

import { loadConfig, McpConfigError } from '../src/config.mjs';

test('loadConfig 读取 API 基址与令牌（去尾斜杠）', () => {
  const config = loadConfig({
    MEDKERNEL_API_BASE: 'https://medkernel.example.org/',
    MEDKERNEL_API_TOKEN: 'tok-1',
  });
  assert.equal(config.baseUrl, 'https://medkernel.example.org');
  assert.equal(config.token, 'tok-1');
});

test('loadConfig 缺少基址抛 McpConfigError', () => {
  assert.throws(
    () => loadConfig({ MEDKERNEL_API_TOKEN: 'tok-1' }),
    (err) => err instanceof McpConfigError && /MEDKERNEL_API_BASE/.test(err.message),
  );
});

test('loadConfig 绝不读取数据库连接串（治理边界）', () => {
  const config = loadConfig({
    MEDKERNEL_API_BASE: 'https://medkernel.example.org',
    MEDKERNEL_API_TOKEN: 'tok-1',
    DATABASE_URL: 'jdbc:postgresql://10.0.0.1:5432/medkernel',
  });
  assert.deepEqual(Object.keys(config).sort(), ['baseUrl', 'token']);
  assert.equal(JSON.stringify(config).includes('jdbc:'), false);
});
