#!/usr/bin/env node
import { readFileSync } from "node:fs";
import path from "node:path";

import {
  buildTargetEnvironmentRehearsalEvidence,
} from "./full-system-rehearsal-lib.mjs";
import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";

try {
  const sourcePath = requireText(
    process.env.LAUNCH_TARGET_ENVIRONMENT_SOURCE_PATH,
    "LAUNCH_TARGET_ENVIRONMENT_SOURCE_PATH",
  );
  const outputPath = requireText(
    process.env.LAUNCH_TARGET_ENVIRONMENT_EVIDENCE_PATH,
    "LAUNCH_TARGET_ENVIRONMENT_EVIDENCE_PATH",
  );
  const sourceEvidence = JSON.parse(readFileSync(path.resolve(sourcePath), "utf8"));
  const evidence = buildTargetEnvironmentRehearsalEvidence(sourceEvidence, {
    webBaseUrl: process.env.LAUNCH_WEB_BASE_URL,
    apiBaseUrl: process.env.LAUNCH_API_BASE_URL,
    source: process.env.LAUNCH_SOURCE,
  });
  writeJsonAtomic(outputPath, evidence);
} catch (error) {
  console.error(error.message);
  process.exit(1);
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label}不能为空`);
  }
  return value.trim();
}
