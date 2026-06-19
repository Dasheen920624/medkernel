#!/usr/bin/env node

import {
  readFoundationConfig,
  redactFoundationEvidence,
  runFoundationInitialization,
  writeFoundationEvidenceAtomic,
} from "./foundation-initialization-lib.mjs";

async function main() {
  const config = readFoundationConfig(process.env);
  let evidence;
  if (config.dryRun) {
    evidence = {
      status: "VALIDATED",
      stage: "FOUNDATION_B0_INITIALIZATION",
      tenantId: config.tenantId,
      registryCode: config.registry.registryCode,
      registryVersion: config.registry.releaseVersion,
      batchCode: config.registry.batchCode,
      entryCount: config.registry.entries.length,
      sourceActor: config.sourceActor.username,
      governorActor: config.governorActor.username,
      safety: {
        containsCredentials: false,
        containsPatientData: false,
        providerEnableAttempted: false,
        p6MutationAttempted: false,
        automatedMedicalReviewAttempted: false,
        automatedExpertSignOff: false,
      },
    };
  } else {
    evidence = await runFoundationInitialization(config);
  }
  writeFoundationEvidenceAtomic(
    config.evidencePath,
    redactFoundationEvidence(evidence),
  );
  process.stdout.write(
    `${evidence.status} ${config.registry.batchCode} evidence=${config.evidencePath}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(
    `稳定知识初始化失败：${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
});
