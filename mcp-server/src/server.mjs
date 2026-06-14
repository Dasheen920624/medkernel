#!/usr/bin/env node
/**
 * MedKernel MCP 服务入口（DATASVC-01 FR-4，初版 stdio JSON-RPC 传输）。
 *
 * 读取 stdin 的换行分隔 JSON-RPC 消息，经 {@link handleRequest} 派发到后端受控工具合同，
 * 响应写回 stdout。鉴权用 MEDKERNEL_API_BASE / MEDKERNEL_API_TOKEN，不直连库、不绕治理。
 */

import readline from 'node:readline';

import { loadConfig, McpConfigError } from './config.mjs';
import { createClient } from './apiClient.mjs';
import { handleRequest } from './protocol.mjs';

export function createDispatcher(client, { write } = {}) {
  return async function dispatch(line) {
    const trimmed = typeof line === 'string' ? line.trim() : '';
    if (!trimmed) {
      return;
    }
    let message;
    try {
      message = JSON.parse(trimmed);
    } catch {
      // JSON-RPC 解析错误（不泄漏内部）。
      write({ jsonrpc: '2.0', id: null, error: { code: -32700, message: '消息解析失败' } });
      return;
    }
    const response = await handleRequest(message, { client });
    if (response != null) {
      write(response);
    }
  };
}

export async function main({ stdin = process.stdin, stdout = process.stdout, env = process.env } = {}) {
  let config;
  try {
    config = loadConfig(env);
  } catch (configError) {
    if (configError instanceof McpConfigError) {
      process.stderr.write(`配置错误：${configError.message}\n`);
      return 2;
    }
    throw configError;
  }

  const client = createClient(config);
  const write = (obj) => stdout.write(`${JSON.stringify(obj)}\n`);
  const dispatch = createDispatcher(client, { write });

  const rl = readline.createInterface({ input: stdin, crlfDelay: Infinity });
  for await (const line of rl) {
    await dispatch(line);
  }
  return 0;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main()
    .then((code) => {
      if (code) process.exit(code);
    })
    .catch((fatal) => {
      console.error(fatal);
      process.exit(1);
    });
}
