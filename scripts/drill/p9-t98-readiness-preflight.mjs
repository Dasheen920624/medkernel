#!/usr/bin/env node

import {
  readPreflightConfig,
  runReadinessPreflight,
  writeEvidenceAtomic,
} from "./p9-t98-readiness-preflight-lib.mjs";

try {
  const config = readPreflightConfig(process.env);
  const result = await runReadinessPreflight(config);
  writeEvidenceAtomic(config.outputPath, result);
  console.log(
    JSON.stringify({
      status: result.status,
      failureCount: result.failures.length,
      outputPath: config.outputPath,
    }),
  );
  if (result.status !== "PASSED") {
    process.exitCode = 1;
  }
} catch (error) {
  console.error(
    JSON.stringify({
      status: "ERROR",
      message:
        error instanceof Error && error.message
          ? error.message
          : "T9.8 只读预检启动失败",
    }),
  );
  process.exitCode = 1;
}
