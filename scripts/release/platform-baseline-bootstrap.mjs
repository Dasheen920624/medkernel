#!/usr/bin/env node
import { existsSync } from "node:fs";

import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import {
  readPlatformBaselineBootstrapConfig,
  runPlatformBaselineBootstrap,
} from "./platform-baseline-bootstrap-lib.mjs";

async function main() {
  const config = readPlatformBaselineBootstrapConfig(process.env);
  if (existsSync(config.evidencePath)) {
    throw new Error(`全新平台基线启动拒绝覆盖既有证据：${config.evidencePath}`);
  }
  const evidence = await runPlatformBaselineBootstrap(config);
  writeJsonAtomic(config.evidencePath, evidence);
  process.stdout.write(
    `PASSED platform-baseline evidence=${config.evidencePath} baseline=${evidence.baseline.baselineReleaseId}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(
    `平台基线启动失败：${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
});
