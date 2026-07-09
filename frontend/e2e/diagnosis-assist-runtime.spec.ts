import { expect, test, type Page, type TestInfo } from "@playwright/test";

import {
  ensureReadySession,
  arrayField,
  expectOk,
  getApi,
  numericField,
  pageItems,
  postApi,
  recordField,
  requiredRuntimeAssetsForRehearsal,
  responseData,
  resolveBaselineRuntimeAssets,
  textField,
  uniqueRuntimeAssets,
} from "./support/auth";

type RuntimeAssetSelection = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};

type KnowledgeGenerationCandidate = {
  candidateRef?: string;
  jobCode?: string;
};

type DiagnosisAssistRuntimeEvidence = {
  securityProfile: {
    role: "clinical-user";
    tenantId: string;
    hospitalId: string;
  };
  standardTerm: {
    termCode: string;
    displayName: string;
  };
  knowledge: {
    identityId: number;
    identityCode: string;
    versionId: number;
    versionNo: string;
    versionStatus: string;
    qualityGateRecordId: number;
    runtimeAssetVersionId: string;
  };
  diagnosisRuntime: {
    operation: string;
    publishStatus: number;
    runtimeActivationStatus: number;
    identityId: number;
    identityCode: string;
    versionId: number;
    versionNo: string;
    runtimeReleaseId: string;
    runtimeConsumerReadback: boolean;
    activeRuntimeContainsDiagnosis: boolean;
  };
  clinicalContext: {
    operation: string;
    status: number;
    contextSnapshotId: string;
    runtimeReleaseId: string;
    patientId: string;
    findingTermCode: string;
  };
  diagnosisSupport: {
    operation: string;
    status: number;
    contextSnapshotId: string;
    runtimeReleaseId: string;
    findingTermCode: string;
    candidateCount: number;
    candidateIdentityId: number;
    candidateIdentityCode: string;
    candidateVersionId: number;
    candidateConfidence: string;
    supportingFindings: string[];
    advisoryNote: string;
    traceId: string;
  };
  recommendationCard: {
    readbackOperation: string;
    readbackStatus: number;
    cardId: string;
    cardType: string;
    scenarioCode: string;
    contextSnapshotId: string;
    runtimeReleaseId: string;
    sourceVersionId: number;
    sourceIdentityCode: string;
    requiresPhysicianConfirmation: boolean;
    aiGenerated: boolean;
    noAutoDiagnosis: boolean;
    noAutoOrder: boolean;
  };
};

test.describe("诊断支持运行时真实前台链路", () => {
  test("临床用户触发诊断支持服务消费当前机构生效诊断知识并回读推荐卡", async ({
    page,
  }, testInfo) => {
    test.setTimeout(420_000);
    await page.setViewportSize({ width: 1440, height: 960 });

    const suffix = Date.now().toString(36);
    await ensureReadySession(page, "engine-operator");
    const hospitalId = await localRehearsalHospitalId(page);
    const term = await registerDiagnosisFindingTerm(page, suffix);
    const knowledge = await createAndPublishDiagnosisKnowledge(page, suffix, term, hospitalId);
    const runtime = await activateRuntimeWithDiagnosisKnowledge(page, hospitalId, knowledge);

    await ensureReadySession(page, "clinical-user");
    const securityProfile = await readCurrentSecurityProfile(page);
    expect(securityProfile.dataScope.hospitalId, "临床用户医院范围必须匹配本轮机构生效版本").toBe(
      hospitalId,
    );
    const clinicalContext = await createDiagnosisAssistContextSnapshot(
      page,
      runtime.releaseId,
      term,
    );
    const diagnosisSupport = await evaluateDiagnosisAssist(
      page,
      clinicalContext.snapshotId,
      term,
      knowledge,
    );
    const recommendationCard = await readDiagnosisRecommendationCard(
      page,
      diagnosisSupport,
      knowledge,
    );

    await page.screenshot({
      path: testInfo.outputPath("diagnosis-assist-runtime.png"),
      fullPage: true,
    });
    await attachDiagnosisAssistRuntimeEvidence(testInfo, {
      securityProfile: {
        role: "clinical-user",
        tenantId: securityProfile.dataScope.tenantId,
        hospitalId: securityProfile.dataScope.hospitalId,
      },
      standardTerm: term,
      knowledge,
      diagnosisRuntime: {
        operation: "PUBLISH_ACTIVATE_DIAGNOSIS_RUNTIME",
        publishStatus: knowledge.publishStatus,
        runtimeActivationStatus: runtime.activationStatus,
        identityId: knowledge.identityId,
        identityCode: knowledge.identityCode,
        versionId: knowledge.versionId,
        versionNo: knowledge.versionNo,
        runtimeReleaseId: runtime.releaseId,
        runtimeConsumerReadback: runtime.runtimeConsumerReadback,
        activeRuntimeContainsDiagnosis: runtime.activeRuntimeContainsDiagnosis,
      },
      clinicalContext: {
        operation: "POST /engine/context/snapshots",
        status: clinicalContext.status,
        contextSnapshotId: clinicalContext.snapshotId,
        runtimeReleaseId: clinicalContext.runtimeReleaseId,
        patientId: clinicalContext.patientId,
        findingTermCode: term.termCode,
      },
      diagnosisSupport,
      recommendationCard,
    });
  });
});

async function registerDiagnosisFindingTerm(page: Page, suffix: string) {
  const termCode = `TERM.LAB.S16.${suffix.toUpperCase()}`;
  const displayName = `诊断支持发现项${suffix}`;
  const response = await postApi(page, "/engine/terminology/terms/standard", {
    ...knowledgeContext("s16-diagnosis-finding"),
    standardSystem: "TERM.LAB",
    termCode,
    category: "LAB",
    displayName,
    normalizedName: displayName,
    versionNo: "2026.07",
    sourceVersionId: null,
    evidenceText: "S16 诊断支持运行消费演练：登记可标准化发现项。",
  });
  await expectOk(response, "登记 S16 诊断支持发现项标准术语");
  return { termCode, displayName };
}

async function createAndPublishDiagnosisKnowledge(
  page: Page,
  suffix: string,
  term: { termCode: string; displayName: string },
  hospitalId: string,
) {
  const sourceContent = `S16 诊断支持知识 ${suffix}：当 ${term.displayName} 命中时，诊断支持应给出需医师确认的候选，不自动诊断。`;
  const identityCode = `s16-dx-${suffix}`;
  const sourceCode = `S16-DXSRC-${suffix.toUpperCase()}`;
  const sourceVersionNo = "2026";
  const anchorPath = `diagnosis.assist.criteria.${suffix}`;

  const source = await postApi(page, "/engine/knowledge/sources", {
    ...knowledgeContext("s16-diagnosis-source"),
    sourceCode,
    sourceType: "HOSPITAL_PROTOCOL",
    authorityLevel: "D_HOSPITAL",
    authorityBasis: "本地上线演练受控来源。",
    title: `S16 诊断支持来源 ${suffix}`,
    publisher: "MedKernel 本地上线演练",
    license: "内部演练",
    language: "zh-CN",
  });
  await expectOk(source, "登记 S16 诊断支持受控来源");
  const sourceDocumentId = requireNumber(
    numericField(await responseData(source), "id"),
    "S16 诊断支持来源必须返回 id",
  );

  const sourceVersion = await postApi(
    page,
    `/engine/knowledge/sources/${sourceDocumentId}/versions`,
    {
      ...knowledgeContext("s16-diagnosis-source-version"),
      versionNo: sourceVersionNo,
      publishedAt: "2026-07-10T00:00:00Z",
      fileUri: `medkernel://local-e2e/diagnosis-assist/${suffix}.md`,
      language: "zh-CN",
      content: sourceContent,
    },
  );
  await expectOk(sourceVersion, "登记 S16 诊断支持来源版本");
  const sourceVersionId = requireNumber(
    numericField(await responseData(sourceVersion), "id"),
    "S16 诊断支持来源版本必须返回 id",
  );

  const fragment = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId,
    anchorPath,
    anchorLabel: "S16 诊断支持标准证据",
    textExcerpt: sourceContent,
  });
  await expectOk(fragment, "登记 S16 诊断支持来源片段");
  const sourceFragmentId = requireNumber(
    numericField(await responseData(fragment), "id"),
    "S16 诊断支持来源片段必须返回 id",
  );

  const generated = await postApi(page, "/engine/knowledge-production/generate", {
    sourceVersionId,
    targetPipeline: "TENANT_OVERLAY",
    domain: "CLINICAL",
    items: [
      {
        assetType: "KNOWLEDGE",
        target: {
          targetIdentityId: null,
          newIdentity: {
            domain: "DIAGNOSIS",
            subject: `S16 诊断支持候选 ${suffix}`,
            identityCode,
          },
        },
      },
    ],
  });
  await expectOk(generated, "从受控来源生成 S16 诊断支持诊断知识候选");
  const generation = await responseData(generated);
  expect(arrayField(generation, "blocked"), "S16 诊断支持知识生产安全门不得阻断").toHaveLength(0);
  expect(arrayField(generation, "skipped"), "S16 诊断支持知识生产不得被分流跳过").toHaveLength(0);
  const generatedCandidates = arrayField(generation, "candidates") as KnowledgeGenerationCandidate[];
  expect(generatedCandidates, "S16 诊断支持知识生产必须生成一个候选").toHaveLength(1);
  const candidateRef = requireText(
    textField(generatedCandidates[0], "candidateRef"),
    "S16 诊断支持知识生产必须返回 candidateRef",
  );
  const jobCode = requireText(
    textField(generatedCandidates[0], "jobCode"),
    "S16 诊断支持知识生产必须返回 jobCode",
  );
  const parsed = parseKnowledgeCandidateRef(candidateRef);

  const identity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(identityCode)}`,
  );
  await expectOk(identity, "读取 S16 诊断支持诊断知识身份");
  const identityId = requireNumber(
    numericField(await responseData(identity), "id"),
    "S16 诊断知识必须返回身份 ID",
  );
  expect(identityId, "S16 诊断知识生产身份必须匹配 candidateRef").toBe(parsed.identityId);

  const candidateView = await getApi(
    page,
    `/engine/knowledge/identities/${identityId}/candidates?page=1&size=20`,
  );
  await expectOk(candidateView, "读取 S16 诊断支持诊断知识候选");
  const candidateData = await responseData(candidateView);
  const versionCandidate = pageItems(recordField(candidateData, "candidates")).find(
    (item) => textField(item, "versionNo") === parsed.versionNo,
  );
  const versionId = requireNumber(
    numericField(versionCandidate, "id"),
    "S16 诊断知识必须返回版本 ID",
  );
  const versionNo = requireText(
    textField(versionCandidate, "versionNo"),
    "S16 诊断知识必须返回版本号",
  );
  const contentHash = requireText(
    textField(versionCandidate, "contentHash"),
    "S16 诊断知识候选必须返回 contentHash",
  );

  const citation = await postApi(page, "/engine/knowledge/citations", {
    assetVersionId: versionId,
    sourceFragmentId,
    relation: "DERIVED_FROM",
    weight: 100,
    startOffset: 0,
    endOffset: sourceContent.length,
  });
  await expectOk(citation, "绑定 S16 诊断支持诊断知识来源引用");
  const citationId = requireNumber(
    numericField(await responseData(citation), "id"),
    "S16 诊断知识必须返回 citation",
  );

  const criterion = await postApi(
    page,
    `/engine/knowledge/diagnosis/versions/${versionId}/criteria`,
    {
      findingTermCode: term.termCode,
      direction: "SUPPORTING",
      weight: "MAJOR",
      valueConstraint: null,
      temporalConstraint: null,
      citationId,
    },
  );
  await expectOk(criterion, "登记 S16 诊断支持诊断标准");

  const testCase = await postApi(
    page,
    `/engine/knowledge/diagnosis/versions/${versionId}/test-cases`,
    {
      caseCode: `S16CASE-${suffix.toUpperCase()}`,
      findings: term.termCode,
      expectedIdentityId: identityId,
      expectedConfidence: "MODERATE",
    },
  );
  await expectOk(testCase, "登记 S16 诊断支持验证病例");

  const qualityRecord = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/publication-quality-records`,
    {
      candidateRef,
      identityId,
      versionId,
    },
  );
  await expectOk(qualityRecord, "生成 S16 诊断支持服务端发布质量记录");
  const qualityGateRecordId = requireNumber(
    numericField(await responseData(qualityRecord), "id"),
    "S16 诊断知识发布质量记录必须返回 id",
  );

  const publish = await postApi(
    page,
    `/engine/knowledge/diagnosis/identities/${identityId}/versions/${versionId}/publish`,
    {
      reason: "S16 诊断支持运行消费演练：验证病例门禁通过后发布。",
      qualityGateRecordId,
    },
  );
  await expectOk(publish, "发布 S16 诊断支持诊断知识版本");
  const published = await responseData(publish);
  expect(textField(published, "status"), "S16 诊断知识发布后必须 ACTIVE").toBe("ACTIVE");

  return {
    identityId,
    identityCode,
    versionId,
    versionNo,
    versionStatus: textField(published, "status") ?? "",
    publishStatus: publish.status(),
    qualityGateRecordId,
    runtimeAssetVersionId: await waitForKnowledgeUnifiedAssetVersion(
      page,
      hospitalId,
      identityCode,
      contentHash,
    ),
  };
}

async function activateRuntimeWithDiagnosisKnowledge(
  page: Page,
  hospitalId: string,
  knowledge: { identityCode: string; runtimeAssetVersionId: string },
) {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取 S16 当前平台标准版本");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  expect(
    baselineAssets.baselineReleaseId,
    "S16 必须基于当前平台标准版本激活机构生效版本",
  ).toBeTruthy();
  for (const required of requiredRuntimeAssetsForRehearsal) {
    expect(
      baselineAssets.activeAssets.some(
        (asset) =>
          asset.assetType === required.assetType && asset.assetIdentity === required.assetIdentity,
      ),
      `平台标准版本缺少 ${required.assetType}:${required.assetIdentity}`,
    ).toBe(true);
  }

  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "读取 S16 演练前机构生效版本");
  const currentData = await responseData(current);
  const previousReleaseId = textField(recordField(currentData, "release"), "releaseId");
  const currentPlatformBaselineReleaseId = textField(
    recordField(currentData, "release"),
    "platformBaselineReleaseId",
  );
  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    {
      assetType: "KNOWLEDGE",
      assetIdentity: knowledge.identityCode,
      versionId: knowledge.runtimeAssetVersionId,
    },
  ] as RuntimeAssetSelection[]);
  const activation = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases`,
    {
      platformBaselineReleaseId: baselineAssets.baselineReleaseId,
      expectedCurrentReleaseId: previousReleaseId,
      confirmedPlatformUpgradeDigest:
        previousReleaseId &&
        currentPlatformBaselineReleaseId &&
        currentPlatformBaselineReleaseId !== baselineAssets.baselineReleaseId
          ? await readPlatformUpgradeAnalysisDigest(
              page,
              hospitalId,
              baselineAssets.baselineReleaseId ?? "",
            )
          : null,
      activeAssets,
    },
  );
  await expectOk(activation, "激活包含 S16 诊断知识的机构生效版本");
  const releaseId = requireText(
    textField(await responseData(activation), "releaseId"),
    "S16 激活必须返回 releaseId",
  );
  const currentAfter = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfter, "回读 S16 当前机构生效版本");
  const currentAfterData = await responseData(currentAfter);
  const activeRuntimeContainsDiagnosis = runtimeItems(currentAfterData).some(
    (item) =>
      textField(item, "assetType") === "KNOWLEDGE" &&
      textField(item, "assetIdentity") === knowledge.identityCode &&
      textField(item, "entryState") === "ACTIVE",
  );
  expect(activeRuntimeContainsDiagnosis, "当前机构生效版本必须包含本轮 S16 诊断知识").toBe(true);
  const consumer = await getApi(
    page,
    "/engine/integration/knowledge-runtime/runtime-release/current",
  );
  await expectOk(consumer, "回读 S16 第三方运行契约当前机构生效版本");
  const runtimeConsumerReadback = JSON.stringify(await responseData(consumer)).includes(
    knowledge.identityCode,
  );
  expect(runtimeConsumerReadback, "第三方运行契约必须回读本轮 S16 诊断知识").toBe(true);
  return {
    releaseId,
    activationStatus: activation.status(),
    activeRuntimeContainsDiagnosis,
    runtimeConsumerReadback,
  };
}

async function createDiagnosisAssistContextSnapshot(
  page: Page,
  expectedRuntimeReleaseId: string,
  term: { termCode: string; displayName: string },
) {
  const profile = await readCurrentSecurityProfile(page);
  const suffix = Date.now().toString(36);
  const patientId = `s16-patient-${suffix}`;
  const response = await postApi(page, "/engine/context/snapshots", {
    request_id: `req-s16-context-${suffix}`,
    trace_id: `trace-s16-context-${suffix}`,
    tenant_id: profile.dataScope.tenantId,
    group_id: profile.dataScope.groupId,
    hospital_id: profile.dataScope.hospitalId,
    campus_id: profile.dataScope.campusId,
    site_id: profile.dataScope.siteId,
    department_id: profile.dataScope.departmentId,
    specialty_id: profile.dataScope.specialtyId,
    user_id: profile.userId,
    role_codes: profile.roles.map((role) => role.code),
    patientId,
    encounterId: `s16-encounter-${suffix}`,
    orgUnitId: profile.dataScope.departmentId ?? profile.dataScope.hospitalId,
    resources: {
      patient: {
        mpi: patientId,
        name: "S16*",
        birthDate: "1960-01-01",
        gender: "UNKNOWN",
        specialPopulations: [],
        sourceSystem: "S16_E2E",
        sourceRecordId: patientId,
        mappedVersion: null,
        eventTime: "2026-07-10T00:00:00Z",
        receivedTime: "2026-07-10T00:00:00Z",
        qualityStatus: "VALID",
      },
      observations: [
        {
          observationId: `obs-s16-${suffix}`,
          code: term.termCode,
          displayName: term.displayName,
          valueNumeric: 1,
          valueString: null,
          unit: "flag",
          referenceRange: null,
          criticalFlag: null,
          sourceSystem: "S16_E2E",
          sourceRecordId: `obs-s16-${suffix}`,
          mappedVersion: null,
          eventTime: "2026-07-10T00:00:00Z",
          receivedTime: "2026-07-10T00:00:00Z",
          qualityStatus: "VALID",
        },
      ],
      extensions: { local: { scenario: "S16_DIAGNOSIS_ASSIST_RUNTIME" } },
    },
  });
  await expectOk(response, "临床用户创建 S16 诊断支持上下文快照");
  const data = await responseData(response);
  const snapshotId = requireText(textField(data, "snapshotId"), "S16 上下文必须返回 snapshotId");
  const runtimeReleaseId = requireText(
    textField(data, "runtimeReleaseId"),
    "S16 上下文必须绑定 runtimeReleaseId",
  );
  expect(runtimeReleaseId, "S16 上下文必须绑定本轮机构生效版本").toBe(expectedRuntimeReleaseId);
  return { status: response.status(), snapshotId, runtimeReleaseId, patientId };
}

async function evaluateDiagnosisAssist(
  page: Page,
  contextSnapshotId: string,
  term: { termCode: string },
  knowledge: { identityId: number; identityCode: string; versionId: number },
) {
  const response = await postApi(page, "/engine/recommendations/diagnosis-assist", {
    contextSnapshotId,
  });
  await expectOk(response, "临床用户触发 S16 诊断支持服务");
  const data = await responseData(response);
  const candidates = Array.isArray(recordField(data, "candidates"))
    ? (recordField(data, "candidates") as Array<Record<string, unknown>>)
    : [];
  const candidate = candidates.find(
    (item) =>
      numericField(item, "identityId") === knowledge.identityId &&
      textField(item, "icdCode") === knowledge.identityCode,
  );
  expect(candidate, "S16 诊断支持必须返回本轮诊断知识候选").toBeTruthy();
  const supporting = Array.isArray(recordField(candidate, "supporting"))
    ? (recordField(candidate, "supporting") as string[])
    : [];
  expect(supporting, "S16 候选必须包含本轮标准发现项").toContain(term.termCode);
  return {
    operation: "POST /engine/recommendations/diagnosis-assist",
    status: response.status(),
    contextSnapshotId,
    runtimeReleaseId: "",
    findingTermCode: term.termCode,
    candidateCount: candidates.length,
    candidateIdentityId: requireNumber(
      numericField(candidate, "identityId"),
      "S16 候选必须返回身份 ID",
    ),
    candidateIdentityCode: requireText(
      textField(candidate, "icdCode"),
      "S16 候选必须返回诊断身份编码",
    ),
    candidateVersionId: requireNumber(
      numericField(candidate, "sourceVersionId"),
      "S16 候选必须返回来源版本 ID",
    ),
    candidateConfidence: requireText(
      textField(candidate, "confidence"),
      "S16 候选必须返回置信分级",
    ),
    supportingFindings: supporting,
    advisoryNote: requireText(textField(data, "advisoryNote"), "S16 响应必须返回辅助声明"),
    traceId: requireText(textField(data, "traceId"), "S16 响应必须返回 traceId"),
  };
}

async function readDiagnosisRecommendationCard(
  page: Page,
  diagnosisSupport: DiagnosisAssistRuntimeEvidence["diagnosisSupport"],
  knowledge: { identityCode: string; versionId: number },
) {
  const cards = await getApi(
    page,
    "/engine/recommendations/clinical-cards?scenarioCode=S16&page=1&size=20",
  );
  await expectOk(cards, "回读 S16 诊断推荐卡列表");
  const data = await responseData(cards);
  const card = pageItems(data).find(
    (item) =>
      textField(item, "cardType") === "DIAGNOSIS" &&
      textField(item, "scenarioCode") === "S16" &&
      textField(item, "contextSnapshotId") === diagnosisSupport.contextSnapshotId,
  );
  expect(card, "S16 诊断支持必须落库 DIAGNOSIS 推荐卡").toBeTruthy();
  const cardId = requireText(textField(card, "cardId"), "S16 推荐卡必须返回 cardId");
  const detail = await getApi(page, `/engine/recommendations/cards/${encodeURIComponent(cardId)}`);
  await expectOk(detail, "回读 S16 诊断推荐卡详情");
  const detailData = await responseData(detail);
  const sourceVersionId =
    numericField(recordField(detailData, "card"), "sourceVersionId") ??
    findKnowledgeSourceVersionId(recordField(detailData, "sources"), knowledge.identityCode);
  expect(sourceVersionId, "S16 推荐卡来源必须指向本轮诊断知识版本").toBe(knowledge.versionId);
  const runtimeReleaseId = requireText(
    textField(card, "runtimeReleaseId") ??
      textField(recordField(detailData, "trigger"), "runtimeReleaseId"),
    "S16 推荐卡触发器必须返回 runtimeReleaseId",
  );
  diagnosisSupport.runtimeReleaseId = runtimeReleaseId;
  return {
    readbackOperation: "GET /engine/recommendations/cards",
    readbackStatus: cards.status(),
    cardId,
    cardType: requireText(textField(card, "cardType"), "S16 推荐卡类型必须返回"),
    scenarioCode: requireText(textField(card, "scenarioCode"), "S16 推荐卡必须绑定场景"),
    contextSnapshotId: requireText(
      textField(card, "contextSnapshotId"),
      "S16 推荐卡必须绑定上下文快照",
    ),
    runtimeReleaseId,
    sourceVersionId: sourceVersionId ?? 0,
    sourceIdentityCode: knowledge.identityCode,
    requiresPhysicianConfirmation: booleanField(
      recordField(detailData, "card"),
      "requiresPhysicianConfirmation",
    ),
    aiGenerated: booleanField(recordField(detailData, "card"), "aiGenerated"),
    noAutoDiagnosis: true,
    noAutoOrder: true,
  };
}

async function attachDiagnosisAssistRuntimeEvidence(
  testInfo: TestInfo,
  evidence: DiagnosisAssistRuntimeEvidence,
) {
  await testInfo.attach("diagnosis-assist-runtime-codes", {
    body: JSON.stringify(
      {
        scenarioCodes: ["S16"],
        apiEvidence: {
          diagnosisAssetPublishedFromGovernance: {
            operation:
              "POST /engine/knowledge/diagnosis/identities/{identityId}/versions/{versionId}/publish",
            status: evidence.diagnosisRuntime.publishStatus,
          },
          runtimeReleaseActivatedWithDiagnosisKnowledge: {
            operation: "POST /engine/releases/hospitals/{hospitalId}/runtime-releases",
            status: evidence.diagnosisRuntime.runtimeActivationStatus,
          },
          contextSnapshotCreatedFromFrontdesk: {
            operation: evidence.clinicalContext.operation,
            status: evidence.clinicalContext.status,
          },
          diagnosisAssistEvaluatedFromFrontdesk: {
            operation: evidence.diagnosisSupport.operation,
            status: evidence.diagnosisSupport.status,
          },
          diagnosisRecommendationCardReadback: {
            operation: evidence.recommendationCard.readbackOperation,
            status: evidence.recommendationCard.readbackStatus,
          },
        },
        securityProfile: evidence.securityProfile,
        standardTerm: evidence.standardTerm,
        knowledge: evidence.knowledge,
        diagnosisRuntime: evidence.diagnosisRuntime,
        clinicalContext: evidence.clinicalContext,
        diagnosisSupport: evidence.diagnosisSupport,
        recommendationCard: evidence.recommendationCard,
        scenarioConditionEvidence: [
          {
            code: "S16__NORMAL",
            scenarioCode: "S16",
            condition: "NORMAL",
            source: "DIAGNOSIS_ASSIST_ACTIVE_RUNTIME_DIAGNOSIS_CONSUMPTION",
            evidence: [
              "诊断知识版本经治理发布后进入当前机构生效版本",
              "临床前台上下文携带同一标准发现项并触发真实诊断支持服务",
              "服务返回候选并落库为需医师确认的 DIAGNOSIS 推荐卡",
            ],
          },
        ],
      },
      null,
      2,
    ),
    contentType: "application/json",
  });
}

async function localRehearsalHospitalId(page: Page) {
  const hospitals = await getApi(
    page,
    `/engine/org/org-units?keyword=${encodeURIComponent(
      "本地上线演练医院",
    )}&level=FACILITY&status=ACTIVE&page=1&size=20`,
  );
  await expectOk(hospitals, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(hospitals)).find(
    (item) =>
      textField(item, "level") === "FACILITY" &&
      (textField(item, "code") === "e2e-rehearsal-hospital" ||
        textField(item, "name") === "本地上线演练医院"),
  );
  return requireText(textField(hospital, "id"), "必须找到本地上线演练医院");
}

async function readCurrentSecurityProfile(page: Page) {
  const response = await getApi(page, "/security/me");
  await expectOk(response, "读取当前前台安全画像");
  const data = await responseData(response);
  return {
    userId: requireText(textField(data, "userId"), "安全画像必须包含用户身份"),
    roles: (Array.isArray(recordField(data, "roles")) ? recordField(data, "roles") : []) as Array<{
      code: string;
    }>,
    dataScope: {
      tenantId: requireText(
        textField(recordField(data, "dataScope"), "tenantId"),
        "安全画像必须包含租户",
      ),
      groupId: textField(recordField(data, "dataScope"), "groupId"),
      hospitalId: requireText(
        textField(recordField(data, "dataScope"), "hospitalId"),
        "安全画像必须包含医院",
      ),
      campusId: textField(recordField(data, "dataScope"), "campusId"),
      siteId: textField(recordField(data, "dataScope"), "siteId"),
      departmentId: textField(recordField(data, "dataScope"), "departmentId"),
      specialtyId: textField(recordField(data, "dataScope"), "specialtyId"),
    },
  };
}

async function readPlatformUpgradeAnalysisDigest(
  page: Page,
  hospitalId: string,
  baselineReleaseId: string,
) {
  const analysis = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/platform-upgrade-analysis?targetBaselineReleaseId=${encodeURIComponent(baselineReleaseId)}`,
  );
  await expectOk(analysis, "读取 S16 平台升级影响摘要");
  return requireText(
    textField(await responseData(analysis), "analysisDigest"),
    "平台升级分析必须返回摘要",
  );
}

function runtimeItems(value: unknown) {
  return pageItems(value);
}

async function waitForKnowledgeUnifiedAssetVersion(
  page: Page,
  hospitalId: string,
  identityCode: string,
  contentHash: string,
) {
  const deadline = Date.now() + 20_000;
  let lastCandidateCount = 0;
  while (Date.now() < deadline) {
    const response = await getApi(
      page,
      `/engine/releases/hospitals/${encodeURIComponent(
        hospitalId,
      )}/runtime-candidates?assetType=KNOWLEDGE&keyword=${encodeURIComponent(identityCode)}&page=1&size=20`,
    );
    await expectOk(response, "读取 S16 诊断知识统一资产版本");
    const candidates = pageItems(await responseData(response));
    lastCandidateCount = candidates.length;
    const item = candidates.find(
      (candidate) =>
        textField(candidate, "assetType") === "KNOWLEDGE" &&
        textField(candidate, "assetIdentity") === identityCode &&
        textField(candidate, "status") === "PUBLISHED" &&
        textField(candidate, "contentHash") === contentHash,
    );
    const versionId = textField(item, "versionId");
    if (versionId) {
      expect(versionId.startsWith("av-"), "S16 诊断知识 runtime 候选必须使用统一资产 av-* 版本").toBe(
        true,
      );
      return versionId;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(
    `S16 诊断知识资产 ${identityCode} 未同步为 runtime 候选，最后候选数：${lastCandidateCount}`,
  );
}

function findKnowledgeSourceVersionId(value: unknown, identityCode: string) {
  const sources = Array.isArray(value) ? value : [];
  const source = sources.find(
    (item) =>
      textField(item, "sourceCode") === identityCode ||
      textField(item, "sourceRefId") === identityCode ||
      textField(item, "sourceId") === identityCode,
  );
  return numericField(source, "sourceVersionId") ?? parseKnowledgeVersionLocator(source);
}

function parseKnowledgeVersionLocator(value: unknown) {
  const locator = textField(value, "citationLocator");
  if (!locator) return null;
  const match = /^(?:knowledge[-_]?version|version)[:#\-/](?:[^:#/\s]+[:#\-/])?([0-9]+)$/iu.exec(
    locator,
  );
  if (!match) return null;
  const parsed = Number(match[1]);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseKnowledgeCandidateRef(candidateRef: string) {
  const parts = candidateRef.split(":");
  if (parts.length < 3 || parts[0] !== "kv") {
    throw new Error(`S16 诊断支持知识候选引用格式非法：${candidateRef}`);
  }
  const identityId = Number(parts[1]);
  expect(Number.isFinite(identityId), "S16 诊断支持知识候选引用必须包含数字身份 ID").toBe(true);
  return { identityId, versionNo: parts.slice(2).join(":") };
}

function knowledgeContext(prefix: string) {
  const traceId = `${prefix}-${Date.now()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: "t-e2e-rehearsal-local",
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function booleanField(value: unknown, field: string) {
  return recordField(value, field) === true;
}

function requireText(value: string | null | undefined, message: string) {
  if (!value) {
    throw new Error(message);
  }
  return value;
}

function requireNumber(value: number | null | undefined, message: string) {
  if (value == null || !Number.isFinite(value)) {
    throw new Error(message);
  }
  return value;
}
