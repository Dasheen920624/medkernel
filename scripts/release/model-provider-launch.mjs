#!/usr/bin/env node
import { existsSync } from "node:fs";

import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import {
  readModelProviderLaunchConfig,
  runModelProviderLaunch,
} from "./model-provider-launch-lib.mjs";

async function main() {
  const config = readModelProviderLaunchConfig(process.env);
  if (existsSync(config.evidencePath)) {
    throw new Error(`全新 Provider 上线拒绝覆盖既有证据：${config.evidencePath}`);
  }
  const evidence = await runModelProviderLaunch(config);
  writeJsonAtomic(config.evidencePath, evidence);
  process.stdout.write(
    `${JSON.stringify({ status: evidence.status, evidencePath: config.evidencePath })}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(`Provider 上线失败：${error.message}\n`);
  process.exitCode = 1;
});
