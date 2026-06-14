import assert from 'node:assert/strict';
import { test } from 'node:test';

import { loadConfig, CliConfigError } from '../src/config.mjs';

test('loadConfig 从环境读取 API 基址与令牌（去尾斜杠）', () => {
  const config = loadConfig({
    MEDKERNEL_API_BASE: 'https://medkernel.example.org/',
    MEDKERNEL_API_TOKEN: 'tok-abc',
  });
  assert.equal(config.baseUrl, 'https://medkernel.example.org');
  assert.equal(config.token, 'tok-abc');
});

test('loadConfig 缺少 API 基址时结构化报错', () => {
  assert.throws(
    () => loadConfig({ MEDKERNEL_API_TOKEN: 'tok-abc' }),
    (err) => err instanceof CliConfigError && /MEDKERNEL_API_BASE/.test(err.message),
  );
});

test('loadConfig 缺少令牌时结构化报错', () => {
  assert.throws(
    () => loadConfig({ MEDKERNEL_API_BASE: 'https://medkernel.example.org' }),
    (err) => err instanceof CliConfigError && /MEDKERNEL_API_TOKEN/.test(err.message),
  );
});

test('loadConfig 绝不读取数据库连接串（治理边界）', () => {
  const config = loadConfig({
    MEDKERNEL_API_BASE: 'https://medkernel.example.org',
    MEDKERNEL_API_TOKEN: 'tok-abc',
    DATABASE_URL: 'jdbc:postgresql://10.0.0.1:5432/medkernel',
    SPRING_DATASOURCE_PASSWORD: 'super-secret',
  });
  // CLI 配置只含 API 基址与令牌，不含任何库连接串字段或其值。
  assert.deepEqual(Object.keys(config).sort(), ['baseUrl', 'token']);
  assert.equal(JSON.stringify(config).includes('jdbc:'), false);
  assert.equal(JSON.stringify(config).includes('super-secret'), false);
});
