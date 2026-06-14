#!/usr/bin/env node
/**
 * MedKernel 产品级 CLI 入口（DATASVC-01 FR-3）。
 *
 * 用法：medkernel <命令域> <动作> [参数] [--purpose 用途]
 * 走后端 API 鉴权（MEDKERNEL_API_BASE / MEDKERNEL_API_TOKEN），不直连库、不绕治理。
 */

import { loadConfig, CliConfigError } from './config.mjs';
import { createClient, CliApiError } from './apiClient.mjs';
import { runCommand, CliUsageError, DOMAINS } from './commands.mjs';

export function parseArgs(argv) {
  const options = {};
  const positional = [];
  let domain;
  let action;
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else if (arg.startsWith('--')) {
      options[arg.slice(2)] = argv[++i];
    } else if (domain === undefined) {
      domain = arg;
    } else if (action === undefined) {
      action = arg;
    } else {
      positional.push(arg);
    }
  }
  return { domain, action, positional, options };
}

function printHelp(out) {
  out('用法：medkernel <命令域> <动作> [参数] [--purpose 用途]');
  out('命令域：');
  for (const [domain, desc] of Object.entries(DOMAINS)) {
    out(`  ${domain.padEnd(18)} ${desc}`);
  }
}

export async function main(argv = process.argv.slice(2), env = process.env, { fetchImpl } = {}) {
  const out = (line) => console.log(line);
  const err = (line) => console.error(line);

  const { domain, action, positional, options } = parseArgs(argv);
  if (!domain || options.help) {
    printHelp(out);
    return 0;
  }

  let config;
  try {
    config = loadConfig(env);
  } catch (configError) {
    if (configError instanceof CliConfigError) {
      err(`配置错误：${configError.message}`);
      return 2;
    }
    throw configError;
  }

  const client = createClient({ ...config, fetchImpl });
  try {
    const result = await runCommand(client, domain, action, positional, options);
    out(JSON.stringify(result, null, 2));
    return 0;
  } catch (runError) {
    if (runError instanceof CliUsageError) {
      err(`用法错误：${runError.message}`);
      return 2;
    }
    if (runError instanceof CliApiError) {
      err(`请求失败${runError.code ? ` [${runError.code}]` : ''}：${runError.message}`);
      return 1;
    }
    throw runError;
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main()
    .then((code) => process.exit(code))
    .catch((fatal) => {
      console.error(fatal);
      process.exit(1);
    });
}
