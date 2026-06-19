import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  assertAllowedFoundationRequest,
  buildInitializationDraft,
  readFoundationConfig,
  redactFoundationEvidence,
  runFoundationInitialization,
  validateFoundationRegistry,
} from "./foundation-initialization-lib.mjs";

const REGISTRY_PATH = fileURLToPath(
  new URL(
    "./manifests/foundation-authority-registry-1.0.0.json",
    import.meta.url,
  ),
);
const API_BASE_URL = "https://127.0.0.1/medkernel/api/v1";
const COVERAGE = Object.freeze([
  "SOURCE_LICENSE_MANIFEST",
  "DATA_ELEMENT_CATALOG",
  "TERMINOLOGY_CODE_SYSTEM",
  "VALUE_SET_SYSTEM_ACTION_DICTIONARY",
  "UNIT_DIMENSION_ALIAS_CONVERSION",
  "MASTER_DATA",
  "INTEROPERABILITY_MAPPING_PROFILE",
  "SEMANTIC_RELATION_DEPRECATION_REDIRECT",
  "EVIDENCE_GRADE_AUTHORITY",
  "DEPENDENCY_COMPATIBILITY_IMPACT",
  "AUTHORITATIVE_SOURCE_SCOPE",
  "GOLDEN_REGRESSION_BOM_COVERAGE",
]);
const FOUNDATION_CODES = Object.freeze([
  "KNOWGEN-29",
  "KNOWGEN-01",
  "KNOWGEN-26",
  "KNOWGEN-27",
  "KNOWGEN-28",
  "KNOWGEN-25",
  "KNOWGEN-15",
  "KNOWGEN-32",
]);

test("基础权威来源目录覆盖全部稳定维度且只登记官方来源元数据", () => {
  const registry = JSON.parse(readFileSync(REGISTRY_PATH, "utf8"));

  assert.doesNotThrow(() => validateFoundationRegistry(registry));
  assert.deepEqual(registry.coverage, COVERAGE);
  assert.deepEqual(
    registry.entries.map((entry) => entry.catalogCode),
    FOUNDATION_CODES,
  );
  assert.equal(registry.entries[0].source.versionNo, "1.0.1");
  assert.equal(
    registry.entries.slice(1).every((entry) => entry.source.versionNo === "1.0.0"),
    true,
  );
  assert.equal(
    new Set(registry.entries.map((entry) => entry.canonicalId)).size,
    8,
  );
  assert.equal(
    registry.entries.every(
      (entry) =>
        entry.medicalContentStatus === "PENDING_AUTHORING" &&
        entry.generatedByModel === false &&
        entry.domain === "GENERAL" &&
        entry.identityDomain === "OTHER" &&
        entry.source.authorityLevel === "D_HOSPITAL" &&
        entry.officialReferences.length > 0 &&
        entry.officialReferences.every(
          (reference) =>
            reference.url.startsWith("https://") &&
            reference.publisher &&
            reference.accessPolicy &&
            reference.checkedAt === "2026-06-19",
        ),
    ),
    true,
  );
});

test("配置只从受控账号文件选定来源登记人与独立治理人", () => {
  const config = readFoundationConfig(
    {
      FOUNDATION_INIT_API_BASE_URL: API_BASE_URL,
      FOUNDATION_INIT_CREDENTIALS_FILE: "/controlled/accounts.json",
      FOUNDATION_INIT_REGISTRY_PATH: REGISTRY_PATH,
      FOUNDATION_INIT_EVIDENCE_PATH: "/tmp/foundation-init.json",
    },
    {
      readFile: (path) =>
        path === REGISTRY_PATH
          ? readFileSync(path, "utf8")
          : JSON.stringify({
              tenantId: "t-1",
              accounts: [
                {
                  username: "knowledge-source-steward",
                  role: "platform-knowledge-governor",
                  password: "source-password",
                },
                {
                  username: "platform-owner",
                  role: "system-superadmin",
                  password: "governor-password",
                },
              ],
            }),
    },
  );

  assert.equal(config.tenantId, "t-1");
  assert.equal(config.sourceActor.username, "knowledge-source-steward");
  assert.equal(config.governorActor.username, "platform-owner");
  assert.notEqual(config.sourceActor.username, config.governorActor.username);
  assert.equal(config.registry.entries.length, 8);

  assert.throws(
    () =>
      readFoundationConfig(
        {
          FOUNDATION_INIT_API_BASE_URL: API_BASE_URL,
          FOUNDATION_INIT_CREDENTIALS_FILE: "/controlled/accounts.json",
          FOUNDATION_INIT_REGISTRY_PATH: REGISTRY_PATH,
          FOUNDATION_INIT_EVIDENCE_PATH: "/tmp/foundation-init.json",
          FOUNDATION_INIT_SOURCE_ACTOR: "platform-owner",
          FOUNDATION_INIT_GOVERNOR_ACTOR: "platform-owner",
        },
        {
          readFile: (path) =>
            path === REGISTRY_PATH
              ? readFileSync(path, "utf8")
              : JSON.stringify({
                  tenantId: "t-1",
                  accounts: [
                    {
                      username: "platform-owner",
                      role: "system-superadmin",
                      password: "governor-password",
                    },
                  ],
                }),
        },
      ),
    /来源登记人与治理人必须分离/,
  );
});

test("请求白名单不允许审核候选、启用 Provider、P6 或医学签署", () => {
  const allowed = [
    ["POST", "/auth/login"],
    ["GET", "/model-evaluations/regression-cases?enabledFlag=Y"],
    ["POST", "/model-evaluations/regression-cases"],
    ["GET", "/engine/knowledge-production/initialization/batches"],
    ["GET", "/engine/knowledge-production/initialization/batches/MK-FND-1.0.0"],
    ["POST", "/engine/knowledge/sources"],
    ["POST", "/engine/knowledge/sources/12/versions"],
    ["POST", "/engine/knowledge/sources/fragments"],
    [
      "POST",
      "/engine/knowledge-production/initialization/source-versions/18/approval",
    ],
    ["GET", "/engine/knowledge/identities/by-code/mk.foundation.source"],
    ["GET", "/engine/knowledge/identities/31/candidates?page=1&size=200"],
    ["POST", "/engine/knowledge-production/generate"],
    ["POST", "/engine/knowledge-production/initialization/batches/preview"],
    ["POST", "/engine/knowledge-production/initialization/batches"],
  ];
  for (const [method, path] of allowed) {
    assert.doesNotThrow(() => assertAllowedFoundationRequest(method, path));
  }

  const forbidden = [
    ["POST", "/engine/knowledge/candidates/1/review"],
    ["POST", "/model-evaluations/1/sign-off"],
    ["POST", "/model-providers/x/enable"],
    ["PUT", "/system-configs/knowledge.production.p6-independent-acceptance"],
    [
      "POST",
      "/engine/knowledge-production/initialization/batches/x/approve-low",
    ],
  ];
  for (const [method, path] of forbidden) {
    assert.throws(
      () => assertAllowedFoundationRequest(method, path),
      /不在稳定知识初始化白名单/,
    );
  }
});

test("初始化草案冻结八条 MEDIUM 候选且不声称基础发行完成", () => {
  const registry = JSON.parse(readFileSync(REGISTRY_PATH, "utf8"));
  const candidateRefs = new Map(
    registry.entries.map((entry, index) => [
      entry.canonicalId,
      `kv:${index + 1}:draft-from-1.0.0`,
    ]),
  );

  const draft = buildInitializationDraft(registry, candidateRefs);

  assert.equal(draft.releaseType, "FOUNDATION");
  assert.equal(draft.phase, "F8");
  assert.equal(draft.declaredEntryCount, 8);
  assert.equal(draft.declaredSourceFileCount, 8);
  assert.match(draft.summary, /B0.*待人工逐条审核/);
  assert.doesNotMatch(draft.summary, /基础发行已完成|正式医学知识已发布/);
  assert.equal(
    draft.entries.every((entry) => entry.changeType === "NEW"),
    true,
  );
});

test("已有同批次时只读复核并幂等退出，不产生任何知识写请求", async () => {
  const registry = JSON.parse(readFileSync(REGISTRY_PATH, "utf8"));
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    const target = new URL(url);
    const path = `${target.pathname.replace("/medkernel/api/v1", "")}${target.search}`;
    calls.push({ method: init.method ?? "GET", path });
    if (path === "/auth/login") {
      return jsonResponse(
        { code: "OK", data: { tenantId: "t-1" } },
        {
          headers: {
            "set-cookie":
              "mk_access=secret; Path=/; HttpOnly, XSRF-TOKEN=xsrf; Path=/",
          },
        },
      );
    }
    if (path === "/engine/knowledge-production/initialization/batches") {
      return jsonResponse({
        code: "OK",
        data: [
          {
            batchCode: registry.batchCode,
            status: "IN_REVIEW",
            candidateCount: 8,
            mediumCount: 8,
          },
        ],
      });
    }
    if (
      path ===
      `/engine/knowledge-production/initialization/batches/${registry.batchCode}`
    ) {
      return jsonResponse({
        code: "OK",
        data: {
          batch: {
            batchCode: registry.batchCode,
            status: "IN_REVIEW",
            candidateCount: 8,
            mediumCount: 8,
          },
          items: registry.entries.map((entry) => ({
            canonicalId: entry.canonicalId,
            status: "PENDING_REVIEW",
            riskLevel: "MEDIUM",
          })),
        },
      });
    }
    throw new Error(`unexpected ${path}`);
  };

  const evidence = await runFoundationInitialization({
    apiBaseUrl: API_BASE_URL,
    tenantId: "t-1",
    sourceActor: {
      username: "knowledge-source-steward",
      password: "source-password",
    },
    governorActor: {
      username: "platform-owner",
      password: "governor-password",
    },
    registry,
    fetchImpl,
    now: () => "2026-06-19T12:00:00.000Z",
  });

  assert.equal(evidence.status, "REUSED");
  assert.equal(evidence.batch.status, "IN_REVIEW");
  assert.equal(calls.filter((call) => call.method === "POST").length, 1);
});

test("首次执行只生成 B0 候选并创建 IN_REVIEW 冻结批次", async () => {
  const registry = JSON.parse(readFileSync(REGISTRY_PATH, "utf8"));
  const calls = [];
  let nextSourceId = 10;
  let nextVersionId = 100;
  let nextCandidateIdentityId = 1000;
  const sourceById = new Map();
  const versionById = new Map();
  const fetchImpl = async (url, init = {}) => {
    const target = new URL(url);
    const method = (init.method ?? "GET").toUpperCase();
    const path = `${target.pathname.replace("/medkernel/api/v1", "")}${target.search}`;
    const headers = new Headers(init.headers);
    const body = init.body ? JSON.parse(init.body) : null;
    calls.push({ method, path, headers, body });

    if (path === "/auth/login") {
      const username = body.username;
      return jsonResponse(
        { code: "OK", data: { tenantId: body.tenantId, username } },
        {
          headers: {
            "set-cookie":
              `mk_access=${username}-cookie; Path=/; HttpOnly, ` +
              `XSRF-TOKEN=${username}-xsrf; Path=/`,
          },
        },
      );
    }
    if (
      method === "GET" &&
      path === "/engine/knowledge-production/initialization/batches"
    ) {
      return jsonResponse({ code: "OK", data: [] });
    }
    if (path === "/model-evaluations/regression-cases?enabledFlag=Y") {
      return jsonResponse({ code: "OK", data: [] });
    }
    if (path === "/model-evaluations/regression-cases") {
      return jsonResponse({ code: "OK", data: { id: calls.length, ...body } });
    }
    if (path === "/engine/knowledge/sources") {
      const id = ++nextSourceId;
      sourceById.set(id, body);
      return jsonResponse({ code: "OK", data: { id, ...body } });
    }
    const versionMatch = path.match(
      /^\/engine\/knowledge\/sources\/(\d+)\/versions$/,
    );
    if (versionMatch) {
      const id = ++nextVersionId;
      versionById.set(id, {
        ...body,
        sourceDocumentId: Number(versionMatch[1]),
      });
      return jsonResponse({
        code: "OK",
        data: {
          id,
          sourceDocumentId: Number(versionMatch[1]),
          versionNo: body.versionNo,
          contentHash: body.contentHash,
        },
      });
    }
    if (path === "/engine/knowledge/sources/fragments") {
      return jsonResponse({
        code: "OK",
        data: { id: calls.length, contentHash: "f".repeat(64), ...body },
      });
    }
    if (
      /^\/engine\/knowledge-production\/initialization\/source-versions\/\d+\/approval$/.test(
        path,
      )
    ) {
      return jsonResponse({
        code: "OK",
        data: {
          status: "APPROVED",
          sourceHash: versionById.get(Number(path.match(/\d+/)[0])).contentHash,
        },
      });
    }
    if (path.startsWith("/engine/knowledge/identities/by-code/")) {
      return jsonResponse(
        { code: "NOT_FOUND", message: "not found" },
        { status: 404 },
      );
    }
    if (path === "/engine/knowledge-production/generate") {
      assert.equal(body.domain, "GENERAL");
      assert.equal(body.items[0].target.newIdentity.domain, "OTHER");
      const identityId = ++nextCandidateIdentityId;
      return jsonResponse({
        code: "OK",
        data: {
          candidates: [
            {
              assetType: body.items[0].assetType,
              jobCode: `JOB-${identityId}`,
              candidateRef: `kv:${identityId}:draft-from-${sourceById.get(versionById.get(body.sourceVersionId).sourceDocumentId)?.versionNo ?? "1.0.0"}`,
              routing: { riskLevel: "MEDIUM" },
            },
          ],
          skipped: [],
          blocked: [],
        },
      });
    }
    if (
      path === "/engine/knowledge-production/initialization/batches/preview"
    ) {
      return jsonResponse({
        code: "OK",
        data: {
          hashes: {
            sourceManifestHash: "a".repeat(64),
            candidateManifestHash: "b".repeat(64),
            overallHash: "c".repeat(64),
          },
          sourceCount: 8,
          candidateCount: 8,
          lowCount: 0,
          mediumCount: 8,
          highCount: 0,
        },
      });
    }
    if (
      method === "POST" &&
      path === "/engine/knowledge-production/initialization/batches"
    ) {
      return jsonResponse({
        code: "OK",
        data: {
          batch: {
            batchCode: registry.batchCode,
            status: "IN_REVIEW",
            candidateCount: 8,
            mediumCount: 8,
            overallHash: body.expectedOverallHash,
          },
          items: body.draft.entries.map((entry) => ({
            canonicalId: entry.canonicalId,
            candidateRef: entry.candidateRef,
            status: "PENDING_REVIEW",
            riskLevel: "MEDIUM",
          })),
        },
      });
    }
    throw new Error(`unexpected ${method} ${path}`);
  };

  const evidence = await runFoundationInitialization({
    apiBaseUrl: API_BASE_URL,
    tenantId: "t-1",
    sourceActor: {
      username: "knowledge-source-steward",
      password: "source-password",
    },
    governorActor: {
      username: "platform-owner",
      password: "governor-password",
    },
    registry,
    fetchImpl,
    now: () => "2026-06-19T12:00:00.000Z",
  });

  assert.equal(evidence.status, "CREATED");
  assert.equal(evidence.batch.status, "IN_REVIEW");
  assert.equal(evidence.batch.candidateCount, 8);
  assert.equal(evidence.safety.providerEnableAttempted, false);
  assert.equal(evidence.safety.p6MutationAttempted, false);
  assert.equal(evidence.safety.automatedMedicalReviewAttempted, false);
  assert.equal(
    calls.filter(
      (call) =>
        call.method === "POST" &&
        call.path === "/model-evaluations/regression-cases",
    ).length,
    3,
  );
  assert.equal(
    calls.filter(
      (call) =>
        call.method === "POST" &&
        call.path === "/engine/knowledge-production/generate",
    ).length,
    8,
  );
  assert.equal(
    calls
      .filter((call) => call.path.endsWith("/approval"))
      .every((call) => call.headers.get("cookie").includes("platform-owner")),
    true,
  );
  assert.equal(JSON.stringify(evidence).includes("source-password"), false);
  assert.equal(JSON.stringify(evidence).includes("governor-password"), false);
});

test("证据递归脱敏且保留安全边界布尔值", () => {
  const redacted = redactFoundationEvidence({
    password: "secret",
    cookie: "session",
    xsrfToken: "xsrf",
    containsCredentials: false,
    providerEnableAttempted: false,
    nested: { recoveryCode: "recovery", candidateRef: "kv:1:v1" },
  });

  assert.equal(redacted.password, "[REDACTED]");
  assert.equal(redacted.cookie, "[REDACTED]");
  assert.equal(redacted.xsrfToken, "[REDACTED]");
  assert.equal(redacted.nested.recoveryCode, "[REDACTED]");
  assert.equal(redacted.nested.candidateRef, "kv:1:v1");
  assert.equal(redacted.containsCredentials, false);
  assert.equal(redacted.providerEnableAttempted, false);
});

function jsonResponse(body, init = {}) {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: {
      "content-type": "application/json",
      ...(init.headers ?? {}),
    },
  });
}
