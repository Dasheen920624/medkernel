#!/usr/bin/env node

import {
  buildRehearsalPlan,
  readRehearsalConfig,
  runFullKnowledgeRehearsal,
  writeEvidenceAtomic,
} from "./full-knowledge-rehearsal-lib.mjs";

async function main() {
  const config = readRehearsalConfig(process.env);
  const evidence = config.dryRun
    ? {
        status: "VALIDATED",
        stage: "FULL_FUNCTION_FULL_KNOWLEDGE",
        tenantId: config.tenantId,
        manifestCode: config.manifest.manifestCode,
        releaseVersion: config.manifest.releaseVersion,
        providerCode: config.providerCode,
        plan: buildRehearsalPlan(config.manifest),
        safety: {
          containsCredentials: false,
          containsPatientData: false,
          clinicalActionGenerated: false,
          automatedOrderGenerated: false,
          mfaRequired: false,
        },
      }
    : await runFullKnowledgeRehearsal(config);
  writeEvidenceAtomic(config.evidencePath, evidence);
  process.stdout.write(
    `${evidence.status} ${config.manifest.manifestCode} evidence=${config.evidencePath}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(
    `正式全知识演练失败：${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
});
