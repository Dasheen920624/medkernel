import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  FULL_KNOWLEDGE_DOMAINS,
  buildPublicationQualityRecordRequest,
  buildModelPrompt,
  buildRehearsalPlan,
  isAcceptableShadowRun,
  readRehearsalConfig,
  redactEvidence,
  validateFullKnowledgeManifest,
  verifyOfficialSource,
} from "./full-knowledge-rehearsal-lib.mjs";

const MANIFEST_PATH = fileURLToPath(
  new URL("./manifests/full-knowledge-rehearsal-1.0.0.json", import.meta.url),
);
const manifest = JSON.parse(readFileSync(MANIFEST_PATH, "utf8"));

test("正式知识演练清单恰好覆盖全部十一知识域且不混入结构资产", () => {
  assert.doesNotThrow(() => validateFullKnowledgeManifest(manifest));
  assert.deepEqual(
    manifest.entries.map((entry) => entry.domain).sort(),
    [...FULL_KNOWLEDGE_DOMAINS].sort(),
  );
  assert.equal(manifest.entries.length, 11);
  assert.ok(FULL_KNOWLEDGE_DOMAINS.includes("DIAGNOSTIC_ITEM"));
  assert.ok(!FULL_KNOWLEDGE_DOMAINS.includes("REPORT"));
  assert.ok(manifest.entries.some((entry) => entry.domain === "DIAGNOSTIC_ITEM"));
  assert.ok(!manifest.entries.some((entry) => entry.domain === "REPORT"));
  assert.equal(
    manifest.entries.every(
      (entry) =>
        entry.assetType === "KNOWLEDGE" &&
        entry.riskLevel === "LOW" &&
        entry.generatedByModel === true,
    ),
    true,
  );
});

test("每个演练知识均绑定可核查官方 HTTPS 来源和短锚点", () => {
  for (const entry of manifest.entries) {
    assert.match(entry.source.url, /^https:\/\//u);
    assert.equal(entry.source.checkedAt, manifest.checkedAt);
    assert.ok(["OFFICIAL_PUBLICATION", "VERIFIED_SNAPSHOT"].includes(entry.source.publishedAtBasis));
    assert.ok(entry.source.publishedAtEvidence.length >= 12);
    assert.ok(entry.source.allowedHosts.includes(new URL(entry.source.url).hostname));
    assert.ok(entry.source.verificationTerms.length >= 1);
    assert.ok(entry.source.textExcerpt.length >= 12);
    assert.ok(entry.source.textExcerpt.length <= 240);
    assert.doesNotMatch(entry.source.textExcerpt, /诊断为|应使用|剂量为|自动开嘱/u);
  }
});

test("持续更新页面必须明确声明为核查快照，不能把核查日期伪装成官方发布日期", () => {
  const invalid = structuredClone(manifest);
  const snapshot = invalid.entries.find(
    (entry) => entry.source.publishedAtBasis === "VERIFIED_SNAPSHOT",
  );
  snapshot.source.publishedAtBasis = "OFFICIAL_PUBLICATION";

  assert.throws(
    () => validateFullKnowledgeManifest(invalid),
    /官方发布日期证据/u,
  );
});

test("路径知识演练来源必须使用可从 134 核验的官方路径工具页", () => {
  const pathway = manifest.entries.find(
    (entry) => entry.domain === "PATHWAY_KNOWLEDGE",
  );

  assert.equal(pathway.source.publisher, "World Health Organization");
  assert.equal(
    pathway.source.url,
    "https://www.who.int/tools/covid-19-clinical-care-pathway",
  );
  assert.ok(
    pathway.source.verificationTerms.includes("COVID-19 Clinical Care Pathway"),
  );
  assert.ok(pathway.source.verificationTerms.includes("clinical care pathway"));
  assert.doesNotMatch(pathway.source.url, /nice\.org\.uk/u);
});

test("文献知识演练来源必须使用可从 134 Node fetch 核验的 NLM 页面", () => {
  const literature = manifest.entries.find((entry) => entry.domain === "LITERATURE");

  assert.equal(literature.source.publisher, "U.S. National Library of Medicine");
  assert.equal(
    literature.source.url,
    "https://www.nlm.nih.gov/medline/medline_overview.html",
  );
  assert.ok(literature.source.verificationTerms.includes("MEDLINE"));
  assert.ok(literature.source.verificationTerms.includes("PubMed"));
  assert.ok(
    literature.source.verificationTerms.includes("National Library of Medicine"),
  );
  assert.doesNotMatch(literature.source.url, /pubmed\.ncbi\.nlm\.nih\.gov/u);
});

test("正式演练会真实抓取官方来源并验证允许主机、锚点词和内容摘要", async () => {
  const entry = manifest.entries[0];
  const verified = await verifyOfficialSource(entry, {
    fetchImpl: async () =>
      new Response(
        `<html><title>${entry.source.verificationTerms.join(" ")}</title></html>`,
        {
          status: 200,
          headers: { "content-type": "text/html; charset=utf-8" },
        },
      ),
    effectiveUrl: entry.source.url,
    now: () => "2026-06-22T10:00:00.000Z",
  });

  assert.equal(verified.status, "VERIFIED");
  assert.equal(verified.httpStatus, 200);
  assert.equal(verified.matchedTerms.length, entry.source.verificationTerms.length);
  assert.match(verified.contentSha256, /^[a-f0-9]{64}$/u);

  await assert.rejects(
    () =>
      verifyOfficialSource(entry, {
        fetchImpl: async () => new Response("<html>unrelated page</html>", { status: 200 }),
        effectiveUrl: "https://untrusted.example/source",
      }),
    /不在允许主机/u,
  );
});

test("模型提示只允许基于来源形成演练候选并要求返回模板 JSON", () => {
  const prompt = buildModelPrompt(manifest.entries[0], {
    assetType: "KNOWLEDGE",
    knowledgeDomain: "GUIDELINE",
    sections: [
      { key: "recommendation", label: "推荐意见", required: true },
      { key: "references", label: "参考文献", required: true },
    ],
  });

  assert.match(prompt, /只返回一个合法 JSON 对象/u);
  assert.match(prompt, /不得补造诊断、剂量、阈值、治疗建议/u);
  assert.match(prompt, /第一个字符必须是 \{/u);
  assert.match(prompt, /"domain": "GUIDELINE"/u);
  assert.match(prompt, /"clinicalActionable": false/u);
  assert.match(prompt, /"sourceReferences"/u);
  assert.match(prompt, /"limitations"/u);
  assert.match(prompt, /recommendation/u);
  assert.match(prompt, /references/u);
  assert.match(prompt, new RegExp(manifest.entries[0].source.sourceCode, "u"));
});

test("演练计划包含十一域 V1 发布以及代表域 V2、回滚和恢复", () => {
  const plan = buildRehearsalPlan(manifest);

  assert.equal(plan.v1.length, 11);
  assert.equal(plan.v2.identityCode, manifest.rollbackIdentityCode);
  assert.deepEqual(plan.rollbackSequence, ["V1", "V2", "V1", "V2"]);
});

test("审核与激活只提交服务端发布质量记录ID，不提交客户端布尔结论", () => {
  const request = buildPublicationQualityRecordRequest({
    candidateRef: "kv:11:v1",
    identityId: 11,
    versionId: 101,
  });

  assert.deepEqual(request, {
    candidateRef: "kv:11:v1",
    identityId: 11,
    versionId: 101,
  });
  assert.equal(JSON.stringify(request).includes("terminologyBinding"), false);
  assert.equal(JSON.stringify(request).includes("dependency"), false);
  assert.equal(JSON.stringify(request).includes("safetyMonotonicity"), false);
  assert.equal(JSON.stringify(request).includes("impactSimulation"), false);
});

test("候选影子评测接受通过或待人工重点复核且拒绝退化", () => {
  assert.equal(
    isAcceptableShadowRun({
      status: "PASSED",
      readyForReview: true,
      degradationDetected: false,
    }),
    true,
  );
  assert.equal(
    isAcceptableShadowRun({
      status: "PENDING_REVIEW",
      readyForReview: true,
      degradationDetected: false,
    }),
    true,
  );
  assert.equal(
    isAcceptableShadowRun({
      status: "PENDING_REVIEW",
      readyForReview: true,
      degradationDetected: true,
    }),
    false,
  );
  assert.equal(
    isAcceptableShadowRun({
      status: "FAILED",
      readyForReview: false,
      degradationDetected: true,
    }),
    false,
  );
});

test("演练运行上下文不提交旧包版本参数", () => {
  const source = readFileSync(
    fileURLToPath(new URL("./full-knowledge-rehearsal-lib.mjs", import.meta.url)),
    "utf8",
  );

  assert.equal(source.includes("package_version"), false);
  assert.equal(source.includes("packageVersion"), false);
});

test("配置只接受平台主源医疗引擎运营员和仓库外运行时证据目录", () => {
  const config = readRehearsalConfig(
    {
      FULL_KNOWLEDGE_API_BASE_URL: "https://127.0.0.1/medkernel/api/v1",
      FULL_KNOWLEDGE_CREDENTIALS_FILE: "/controlled/accounts.json",
      FULL_KNOWLEDGE_MANIFEST_PATH: MANIFEST_PATH,
      MEDKERNEL_RUNTIME_ROOT: "/var/lib/medkernel",
      FULL_KNOWLEDGE_PROVIDER_CODE: "formal-provider",
    },
    {
      repoRoot: "/workspace/medkernel",
      readFile: (path) =>
        path === MANIFEST_PATH
          ? readFileSync(path, "utf8")
          : canonicalCredentials(),
    },
  );

  assert.equal(config.tenantId, "t-1");
  assert.equal(config.operator.role, "engine-operator");
  assert.equal(
    config.evidencePath,
    "/var/lib/medkernel/evidence/current-launch/full-knowledge.json",
  );
  assert.throws(
    () =>
      readRehearsalConfig(
        {
          FULL_KNOWLEDGE_API_BASE_URL:
            "https://127.0.0.1/medkernel/api/v1",
          FULL_KNOWLEDGE_CREDENTIALS_FILE: "/controlled/accounts.json",
          FULL_KNOWLEDGE_MANIFEST_PATH: MANIFEST_PATH,
          FULL_KNOWLEDGE_EVIDENCE_PATH:
            "/workspace/medkernel/tmp/forbidden-evidence/result.json",
          FULL_KNOWLEDGE_PROVIDER_CODE: "formal-provider",
        },
        {
          repoRoot: "/workspace/medkernel",
          readFile: (path) =>
            path === MANIFEST_PATH
              ? readFileSync(path, "utf8")
              : canonicalCredentials(),
        },
      ),
    /证据路径必须位于仓库之外/u,
  );
});

function canonicalCredentials() {
  const account = (tenantId, role) => ({
    tenantId,
    userId: role,
    username: role,
    displayName: role,
    role,
    assignable: true,
    password: "controlled-password",
  });
  const accounts = (tenantId) => ({
    "platform-admin": account(tenantId, "platform-admin"),
    "engine-operator": account(tenantId, "engine-operator"),
    "clinical-user": account(tenantId, "clinical-user"),
    auditor: account(tenantId, "auditor"),
  });
  return JSON.stringify({
    schemaVersion: "1.0.0",
    status: "READY",
    generatedAt: "2026-06-22T08:00:00.000Z",
    platform: {
      tenantId: "t-1",
      takeover: {
        tenantId: "t-1",
        userId: "system-takeover",
        username: "system-takeover",
        displayName: "system-superadmin",
        role: "system-superadmin",
        assignable: false,
        password: "controlled-password",
      },
      accounts: accounts("t-1"),
    },
    rehearsal: {
      tenantId: "t-rehearsal",
      tenantName: "完整上线演练机构",
      hospital: {
        code: "REHEARSAL-HOSPITAL",
        name: "完整上线演练医院",
        facilityType: "HOSPITAL",
      },
      accounts: accounts("t-rehearsal"),
    },
  });
}

test("证据脱敏不泄露凭据、Cookie、令牌和患者数据", () => {
  const evidence = redactEvidence({
    password: "secret",
    cookie: "mk_access=secret",
    accessToken: "secret",
    patientData: "secret",
    safety: { containsPatientData: false },
    domain: "GUIDELINE",
  });

  assert.equal(evidence.password, "[REDACTED]");
  assert.equal(evidence.cookie, "[REDACTED]");
  assert.equal(evidence.accessToken, "[REDACTED]");
  assert.equal(evidence.patientData, "[REDACTED]");
  assert.equal(evidence.safety.containsPatientData, false);
  assert.equal(evidence.domain, "GUIDELINE");
});
