import assert from "node:assert/strict";
import { mkdir, mkdtemp, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import test from "node:test";

import { hasBlockingViolations, scanFiles } from "./config-boundary-guard.mjs";

test("新增后端代码禁止直接读取非启动必需的 medkernel 配置", async () => {
  const root = await mkdtemp(join(tmpdir(), "medkernel-config-boundary-"));
  const file = "medkernel-backend/src/main/java/com/medkernel/shared/security/BadConfig.java";
  await mkdir(join(root, "medkernel-backend/src/main/java/com/medkernel/shared/security"), { recursive: true });
  await writeFile(
    join(root, file),
    `
      package com.medkernel.shared.security;

      import org.springframework.beans.factory.annotation.Value;

      public class BadConfig {
          BadConfig(@Value("\${medkernel.auth.jwt.ttl-seconds:28800}") long ttl) {}
      }
    `,
  );

  const report = await scanFiles(root, [file]);

  assert.equal(hasBlockingViolations(report), true);
  assert.deepEqual(report.violations.map((item) => item.ruleId), ["config-boundary.direct-medkernel-read"]);
});

test("启动密钥、产品版本等启动边界配置允许保留在启动配置", async () => {
  const root = await mkdtemp(join(tmpdir(), "medkernel-config-boundary-"));
  const file = "medkernel-backend/src/main/java/com/medkernel/shared/security/AllowedConfig.java";
  await mkdir(join(root, "medkernel-backend/src/main/java/com/medkernel/shared/security"), { recursive: true });
  await writeFile(
    join(root, file),
    `
      package com.medkernel.shared.security;

      import org.springframework.beans.factory.annotation.Value;

      public class AllowedConfig {
          AllowedConfig(
              @Value("\${medkernel.jwt.dev-secret:change-me}") String secret,
              @Value("\${medkernel.version:1.0}") String version) {}
      }
    `,
  );

  const report = await scanFiles(root, [file]);

  assert.equal(hasBlockingViolations(report), false);
});
