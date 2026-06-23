#!/usr/bin/env node
import { existsSync } from "node:fs";

import {
  readFullSystemRehearsalConfig,
  runFullSystemRehearsal,
} from "./full-system-rehearsal-lib.mjs";

async function main() {
  const config = readFullSystemRehearsalConfig(process.env);
  if (existsSync(config.indexPath)) {
    throw new Error(`全新整套演练拒绝覆盖既有总证据：${config.indexPath}`);
  }
  const evidence = await runFullSystemRehearsal(config);
  process.stdout.write(
    `PASSED full-system-rehearsal evidence=${config.indexPath} stages=${evidence.stages.length}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(
    `整套系统上线演练失败：${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
});
