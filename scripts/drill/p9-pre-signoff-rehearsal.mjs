#!/usr/bin/env node

import { pathToFileURL } from "node:url";

import {
  readPreSignoffConfig,
  runPreSignoffRehearsal,
  writeEvidenceAtomic,
} from "./p9-pre-signoff-rehearsal-lib.mjs";

export async function main(env = process.env) {
  const config = readPreSignoffConfig(env);
  const evidence = await runPreSignoffRehearsal(config);
  writeEvidenceAtomic(config.outputPath, evidence);
  process.stdout.write(
    `${JSON.stringify({
      status: evidence.status,
      outputPath: config.outputPath,
      providerCount: evidence.providers.length,
      failures: evidence.failures,
      containsCredentials: evidence.containsCredentials,
      containsPatientData: evidence.containsPatientData,
    })}\n`,
  );
  return evidence.status === "PASSED" ? 0 : 1;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    process.exitCode = await main();
  } catch (error) {
    process.stderr.write(
      `${error instanceof Error ? error.message : "预演发生未知错误"}\n`,
    );
    process.exitCode = 1;
  }
}
