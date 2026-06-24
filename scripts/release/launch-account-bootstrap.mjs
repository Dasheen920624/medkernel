#!/usr/bin/env node

import {
  assertLaunchOutputPathsAvailable,
  buildLaunchCredentialPlan,
  readLaunchBootstrapConfig,
  runLaunchAccountBootstrap,
  writeJsonAtomic,
} from "./launch-account-bootstrap-lib.mjs";

async function main() {
  const config = readLaunchBootstrapConfig(process.env);
  assertLaunchOutputPathsAvailable(config);
  const result = await runLaunchAccountBootstrap({
    apiBaseUrl: config.apiBaseUrl,
    bootstrapToken: config.bootstrapToken,
    plan: buildLaunchCredentialPlan(),
  });
  writeJsonAtomic(config.credentialsPath, result.credentials);
  writeJsonAtomic(config.evidencePath, result.evidence);
  process.stdout.write(
    `PASSED account-bootstrap credentials=${config.credentialsPath} evidence=${config.evidencePath}\n`,
  );
}

main().catch((error) => {
  process.stderr.write(
    `全新接管与四职责开通失败：${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
});
