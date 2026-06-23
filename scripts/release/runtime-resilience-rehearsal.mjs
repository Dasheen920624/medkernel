#!/usr/bin/env node
import { existsSync } from "node:fs";

import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import {
  readRuntimeResilienceConfig,
  runRuntimeResilienceRehearsal,
} from "./runtime-resilience-rehearsal-lib.mjs";

async function main() {
  const config = readRuntimeResilienceConfig(process.env);
  if (existsSync(config.evidencePath)) {
    throw new Error(`全新运行韧性演练拒绝覆盖既有证据：${config.evidencePath}`);
  }
  const evidence = await runRuntimeResilienceRehearsal(config);
  writeJsonAtomic(config.evidencePath, evidence);
  process.stdout.write(
    `PASSED runtime-resilience evidence=${config.evidencePath} b0=${evidence.b0.passedCount}/${evidence.b0.fixtureCount}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(
    `运行韧性演练失败：${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
});
