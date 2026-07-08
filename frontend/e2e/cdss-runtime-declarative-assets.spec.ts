import {
  expect,
  test,
  type APIResponse,
  type Locator,
  type Page,
  type TestInfo,
} from "@playwright/test";

import {
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  postApi,
  requiredRuntimeAssetsForRehearsal,
} from "./support/auth";

type DeclarativeAssetType = "VALUE_SET" | "FORMULA" | "ACTION_CARD";

type DeclarativeAssetCandidate = {
  assetType: DeclarativeAssetType;
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type RuleRuntimeCandidate = {
  assetType: "RULE";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
  status: string;
  sourceLayer: string;
};

type RuntimeAssetSelection = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};

type RuntimeReleaseItem = RuntimeAssetSelection & {
  versionNo?: string;
  contentHash?: string;
  entryState?: string;
};

type RuntimeReleaseDetail = {
  release?: {
    releaseId?: string;
    revisionNo?: number;
    manifestSha256?: string;
    platformBaselineReleaseId?: string;
  };
  items?: RuntimeReleaseItem[];
};

type RuntimeRollbackNegativeEvidence = {
  rollbackPosted: boolean;
  currentRuntimeReadbackVerified: boolean;
  runtimeConsumerReadbackVerified: boolean;
  consumer: string;
  consumerProbeMatchedRemovedAssets: boolean;
  removedAssets: RuntimeAssetSelection[];
  currentRuntime: {
    releaseId: string;
    revisionNo: number;
    manifestSha256: string;
    assets: RuntimeReleaseItem[];
  };
  runtimeConsumer: {
    contractVersion: "v1";
    releaseId: string;
    revisionNo: number;
    manifestSha256: string;
    assets: RuntimeReleaseItem[];
  };
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  maskedName: string;
  idLast4: string;
};

type CdssRuntimeDeclarativeApiEvidence = {
  valueSetCreatedFromFrontdesk: boolean;
  formulaCreatedFromFrontdesk: boolean;
  actionCardCreatedFromFrontdesk: boolean;
  declarativeRuntimeActivatedBeforeRuleTestCases: boolean;
  ruleTestSnapshotBoundToDeclarativeRuntime: boolean;
  ruleCreatedWithRuntimeAssetReferences: boolean;
  ruleRuntimeCandidateResolvedFromCurrentHospital: boolean;
  runtimeReleaseActivatedWithDeclarativeAssets: boolean;
  activeSnapshotBoundToRuntimeRelease: boolean;
  cdssEvaluationTriggeredFromFrontdesk: boolean;
  recommendationPersisted: boolean;
  ruleExplanationContainsRuntimeMaterialization: boolean;
};

const requiredStages = [
  "前台创建 VALUE_SET 值集资产草稿",
  "前台创建 FORMULA 公式资产草稿",
  "前台创建 ACTION_CARD 临床提示卡资产草稿",
  "临床规则引用三类运行资产",
  "当前机构生效版本包含三类本轮运行资产",
  "临床用户从真实前台触发 CDSS 推荐评估",
  "推荐卡解释证明三类资产按当前机构生效版本物化消费",
] as const;

test.describe("CDSS 声明式运行资产真实消费", () => {
  test("临床用户从真实前台触发 CDSS 推荐并消费当前机构生效版本声明式运行资产", async ({
    page,
  }, testInfo) => {
    test.setTimeout(300_000);
    const observedStages = new Set<string>();
    const apiEvidence = createApiEvidence();

    await ensureReadySession(page, "engine-operator");
    const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;
    const valueSet = await createDeclarativeAssetFromFrontdesk(page, "VALUE_SET", suffix);
    apiEvidence.valueSetCreatedFromFrontdesk = true;
    recordCdssRuntimeDeclarativeAssetStage(observedStages, "前台创建 VALUE_SET 值集资产草稿");
    const formula = await createDeclarativeAssetFromFrontdesk(page, "FORMULA", suffix);
    apiEvidence.formulaCreatedFromFrontdesk = true;
    recordCdssRuntimeDeclarativeAssetStage(observedStages, "前台创建 FORMULA 公式资产草稿");
    const actionCard = await createDeclarativeAssetFromFrontdesk(page, "ACTION_CARD", suffix);
    apiEvidence.actionCardCreatedFromFrontdesk = true;
    recordCdssRuntimeDeclarativeAssetStage(
      observedStages,
      "前台创建 ACTION_CARD 临床提示卡资产草稿",
    );

    const hospitalId = await localRehearsalHospitalId(page);
    const declarativeRuntime = await activateRuntimeWithDeclarativeAssets(page, {
      hospitalId,
      valueSet,
      formula,
      actionCard,
    });
    apiEvidence.declarativeRuntimeActivatedBeforeRuleTestCases = true;
    recordCdssRuntimeDeclarativeAssetStage(observedStages, "当前机构生效版本包含三类本轮运行资产");

    const ruleTestSnapshot = await createClinicalContextFromFrontdesk(
      page,
      suffix,
      "S5 CDSS 声明式运行资产规则发布验证用例",
    );
    const negativeRuleTestSnapshot = await createClinicalContextFromFrontdesk(
      page,
      `${suffix}-NEG`,
      "S5 CDSS 声明式运行资产规则发布验证阴性用例",
      { medicationText: `S5 CDSS 非命中用药 ${suffix}`, heightCm: "170", weightKg: "50" },
    );
    expect(
      ruleTestSnapshot.runtimeReleaseId,
      "规则发布验证快照必须在三类声明式资产激活后创建并绑定该 runtime",
    ).toBe(declarativeRuntime.releaseId);
    expect(
      negativeRuleTestSnapshot.runtimeReleaseId,
      "阴性规则发布验证快照也必须绑定声明式资产 runtime",
    ).toBe(declarativeRuntime.releaseId);
    apiEvidence.ruleTestSnapshotBoundToDeclarativeRuntime = true;

    const rule = await createAndPublishRuleReferencingDeclarativeAssets(page, {
      suffix,
      valueSet,
      formula,
      actionCard,
      testContextSnapshotId: ruleTestSnapshot.snapshotId,
      negativeContextSnapshotId: negativeRuleTestSnapshot.snapshotId,
    });
    apiEvidence.ruleCreatedWithRuntimeAssetReferences = true;
    recordCdssRuntimeDeclarativeAssetStage(observedStages, "临床规则引用三类运行资产");

    const ruleRuntimeCandidate = await readHospitalRuntimeCandidate(page, hospitalId, {
      ruleCode: rule.assetIdentity,
    });
    apiEvidence.ruleRuntimeCandidateResolvedFromCurrentHospital = true;
    const finalRuntime = await activateRuntimeWithDeclarativeAssets(page, {
      hospitalId,
      ruleRuntimeCandidate,
      valueSet,
      formula,
      actionCard,
    });
    apiEvidence.runtimeReleaseActivatedWithDeclarativeAssets = true;

    const snapshot = await createClinicalContextFromFrontdesk(
      page,
      suffix,
      "S5 CDSS 声明式运行资产临床推荐评估",
    );
    expect(snapshot.runtimeReleaseId, "临床 ACTIVE 快照必须绑定本轮激活后的机构生效版本").toBe(
      finalRuntime.releaseId,
    );
    apiEvidence.activeSnapshotBoundToRuntimeRelease = true;

    const recommendation = await triggerRecommendationFromFrontdesk(page, {
      snapshot,
      runtime: finalRuntime,
      ruleRuntimeCandidate,
    });
    apiEvidence.cdssEvaluationTriggeredFromFrontdesk = true;
    apiEvidence.recommendationPersisted = true;
    recordCdssRuntimeDeclarativeAssetStage(observedStages, "临床用户从真实前台触发 CDSS 推荐评估");

    const materializedRecommendation = assertRecommendationMaterializedDeclarativeAssets({
      recommendation,
      runtime: finalRuntime,
      ruleRuntimeCandidate,
    });
    apiEvidence.ruleExplanationContainsRuntimeMaterialization = true;
    recordCdssRuntimeDeclarativeAssetStage(
      observedStages,
      "推荐卡解释证明三类资产按当前机构生效版本物化消费",
    );
    const rollbackNegativeEvidence = await rollbackRuntimeAndAssertAssetsRemoved(page, {
      hospitalId,
      targetReleaseId: declarativeRuntime.previousReleaseId,
      consumer: "CDSS_DECLARATIVE_ASSET_EVALUATION",
      removedAssets: [
        runtimeSelection(valueSet),
        runtimeSelection(formula),
        runtimeSelection(actionCard),
      ],
    });

    await attachCdssRuntimeDeclarativeAssetEvidence(testInfo, {
      apiEvidence,
      createdAssets: [valueSet, formula, actionCard],
      rule,
      ruleRuntimeCandidate,
      declarativeRuntime,
      runtime: finalRuntime,
      activationRequest: finalRuntime.activationRequest,
      clinicalTrigger: {
        triggerId: recommendation.triggerId,
        contextSnapshotId: snapshot.snapshotId,
        runtimeReleaseId: snapshot.runtimeReleaseId,
        cardId: recommendation.cardId,
        relatedCardIds: recommendation.relatedCardIds,
      },
      recommendation: {
        ...materializedRecommendation,
        contextSnapshotId: snapshot.snapshotId,
      },
      rollbackNegativeEvidence,
      observedStages,
    });
  });
});

function createApiEvidence(): CdssRuntimeDeclarativeApiEvidence {
  return {
    valueSetCreatedFromFrontdesk: false,
    formulaCreatedFromFrontdesk: false,
    actionCardCreatedFromFrontdesk: false,
    declarativeRuntimeActivatedBeforeRuleTestCases: false,
    ruleTestSnapshotBoundToDeclarativeRuntime: false,
    ruleCreatedWithRuntimeAssetReferences: false,
    ruleRuntimeCandidateResolvedFromCurrentHospital: false,
    runtimeReleaseActivatedWithDeclarativeAssets: false,
    activeSnapshotBoundToRuntimeRelease: false,
    cdssEvaluationTriggeredFromFrontdesk: false,
    recommendationPersisted: false,
    ruleExplanationContainsRuntimeMaterialization: false,
  };
}

async function createDeclarativeAssetFromFrontdesk(
  page: Page,
  assetType: DeclarativeAssetType,
  suffix: string,
): Promise<DeclarativeAssetCandidate> {
  const assetIdentity = cdssRuntimeAssetIdentity(assetType, suffix);
  await page.goto(appPath("/authoring/assets"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "统一资产库" })).toBeVisible();
  await page.getByRole("tab", { name: "字段与配置资产" }).click();
  await page.getByRole("tab", { name: declarativeAssetTabName(assetType) }).click();
  await page.getByRole("button", { name: `新建${declarativeAssetButtonName(assetType)}` }).click();

  const dialog = page.getByRole("dialog", { name: `新建${declarativeAssetButtonName(assetType)}` });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("稳定资产身份").fill(assetIdentity);
  await dialog.getByLabel("适用范围").fill("ALL");
  await dialog
    .getByLabel("来源依据")
    .fill("S5 CDSS 声明式运行资产演练：验证当前机构生效版本消费，不自动开嘱。");

  await fillDeclarativeAssetContent(dialog, assetType, suffix);

  const responsePromise = waitForPost(page, "/engine/authoring/declarative-assets");
  await dialog.getByRole("button", { name: "保存草稿" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, `前台创建 ${assetType} 声明式资产草稿`);
  const data = await responseData(response);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    assetType,
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), `${assetType} 草稿必须返回 versionId`),
    versionNo: requireText(textField(data, "versionNo"), `${assetType} 草稿必须返回 versionNo`),
    contentHash: requireText(
      textField(data, "contentHash"),
      `${assetType} 草稿必须返回 contentHash`,
    ),
  };
}

async function fillDeclarativeAssetContent(
  dialog: Locator,
  assetType: DeclarativeAssetType,
  suffix: string,
) {
  if (assetType === "VALUE_SET") {
    await dialog.getByLabel("名称", { exact: true }).fill(`S5 CDSS 氨基糖苷值集 ${suffix}`);
    await dialog.getByLabel("编码体系").fill("ATC");
    await dialog.getByLabel("成员编码").fill("J01GB03");
    await dialog.getByLabel("成员名称").fill(`庆大霉素 ${suffix}`);
    return;
  }
  if (assetType === "FORMULA") {
    await dialog.getByLabel("名称", { exact: true }).fill(`S5 CDSS BMI 公式 ${suffix}`);
    await chooseDialogOption(dialog.page(), dialog, "计算公式", "体质指数（BMI）");
    await dialog.getByLabel("输出单位").fill("kg/m2");
    await dialog.getByLabel("输入名").first().fill("heightCm");
    await dialog.getByLabel("字段路径").first().fill("extensions.local.frontdeskContext.heightCm");
    await dialog.getByLabel("输入单位").first().fill("cm");
    await dialog.getByRole("button", { name: /添加输入/ }).click();
    await dialog.getByLabel("输入名").nth(1).fill("weightKg");
    await dialog.getByLabel("字段路径").nth(1).fill("extensions.local.frontdeskContext.weightKg");
    await dialog.getByLabel("输入单位").nth(1).fill("kg");
    return;
  }
  await dialog.getByLabel("标题").fill(`S5 CDSS 用药复核提示卡 ${suffix}`);
  await chooseDialogOption(dialog.page(), dialog, "命中后处理", "强提醒");
  await chooseDialogOption(dialog.page(), dialog, "风险等级", "高风险");
  await chooseDialogOption(dialog.page(), dialog, "提醒等级", "必须处理");
  await dialog.getByLabel("摘要").fill("当前用药命中需人工复核的声明式运行资产规则");
  await dialog
    .getByLabel("详细说明")
    .fill("患者当前上下文命中氨基糖苷值集和 BMI 公式条件，提示医师人工复核用药风险。");
  await dialog.getByLabel("依据名称").fill("MedKernel S5 本地上线演练");
  await dialog.getByLabel("证据类型").fill("本地上线演练");
  await dialog.getByLabel("可选操作名称").fill("打开用药复核记录");
  await chooseDialogOption(dialog.page(), dialog, "可选操作类型", "打开记录表单");
  await dialog.getByLabel("关联业务对象").fill("FORM.CDSS.RUNTIME.REVIEW");
  await dialog.getByRole("button", { name: /添加改用方案原因/ }).click();
  await dialog.getByLabel("允许改用其他方案的原因").fill("医师已完成人工复核");
  const requires = dialog.getByRole("checkbox", { name: "需医师确认" });
  if (!(await requires.isChecked())) {
    await requires.click();
  }
}

function declarativeAssetTabName(assetType: DeclarativeAssetType) {
  switch (assetType) {
    case "VALUE_SET":
      return "值集";
    case "FORMULA":
      return "公式与量表";
    case "ACTION_CARD":
      return "临床提示卡";
  }
}

function declarativeAssetButtonName(assetType: DeclarativeAssetType) {
  switch (assetType) {
    case "VALUE_SET":
      return "值集";
    case "FORMULA":
      return "公式";
    case "ACTION_CARD":
      return "临床提示卡";
  }
}

function cdssRuntimeAssetIdentity(assetType: DeclarativeAssetType, suffix: string) {
  switch (assetType) {
    case "VALUE_SET":
      return `VALUE_SET.CDSS.RUNTIME.${suffix}`;
    case "FORMULA":
      return `FORMULA.CDSS.RUNTIME.${suffix}`;
    case "ACTION_CARD":
      return `ACTION_CARD.CDSS.RUNTIME.${suffix}`;
  }
}

async function createAndPublishRuleReferencingDeclarativeAssets(
  page: Page,
  options: {
    suffix: string;
    valueSet: DeclarativeAssetCandidate;
    formula: DeclarativeAssetCandidate;
    actionCard: DeclarativeAssetCandidate;
    testContextSnapshotId: string;
    negativeContextSnapshotId: string;
  },
) {
  await ensureReadySession(page, "engine-operator");
  const ruleCode = `RULE.CDSS.RUNTIME.${options.suffix}`;
  const create = await postApi(page, "/engine/rule/rules", {
    ...ruleApiContext(options.suffix, "create"),
    triggers: [
      {
        trigger_point: "order-sign",
        purpose: "RULE_EXECUTION",
        required_fields: [],
      },
    ],
    ruleCode,
    name: `S5 CDSS 声明式运行资产规则 ${options.suffix}`,
    ruleType: "ORDER",
    authoringMode: "DSL",
    riskLevel: "HIGH",
    priority: 900,
    suppressedBy: null,
    dedupeWindowSeconds: 0,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:cdss-runtime-declarative-assets",
    changeSummary: "S5 演练：规则引用 VALUE_SET、FORMULA、ACTION_CARD 三类运行资产。",
    dsl: cdssRuleDsl(options),
    explanation: {
      summary: "本地上线演练规则解释：三类声明式运行资产必须由机构生效版本物化。",
    },
    parameterBindings: {},
  });
  await expectOk(create, "创建 S5 CDSS 声明式运行资产规则");
  const created = await responseData(create);
  const ruleId = requireText(textField(created, "ruleId"), "规则创建响应必须返回 ruleId");
  const ruleVersionId = requireText(
    textField(created, "versionId"),
    "规则创建响应必须返回规则领域 versionId",
  );
  await addRuleReleaseTestCases(page, ruleId, {
    positiveContextSnapshotId: options.testContextSnapshotId,
    negativeContextSnapshotId: options.negativeContextSnapshotId,
  });
  const testRun = await postApi(
    page,
    `/engine/rule/rules/${encodeURIComponent(ruleId)}/test`,
    ruleApiContext(ruleId, "release-test-run"),
  );
  await expectOk(testRun, "执行 S5 声明式运行资产规则发布验证用例");
  assertRuleReleaseTestRunPassed(await responseData(testRun));
  for (const targetState of ["REVIEWED", "SHADOW", "CANARY", "FULL"]) {
    const impact = await getApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/impact`);
    await expectOk(impact, `读取规则 ${targetState} 影响摘要`);
    const impactDigest = requireText(
      textField(await responseData(impact), "impactDigest"),
      `${targetState} 推进前必须返回 impactDigest`,
    );
    const transition = await postApi(
      page,
      `/engine/rule/rules/${encodeURIComponent(ruleId)}/governance/transitions`,
      {
        ...ruleApiContext(ruleId, `governance-${targetState}`),
        targetState,
        impactDigest,
        reason: `S5 声明式运行资产演练推进至 ${targetState}`,
        publishEvidence: ruleGovernancePublishEvidence(targetState),
      },
    );
    await expectOk(transition, `规则治理推进至 ${targetState}`);
  }
  return {
    assetType: "RULE",
    assetIdentity: ruleCode,
    ruleId,
    ruleVersionId,
  };
}

function cdssRuleDsl(options: {
  valueSet: DeclarativeAssetCandidate;
  formula: DeclarativeAssetCandidate;
  actionCard: DeclarativeAssetCandidate;
}) {
  return {
    applicability: {
      population: {},
      orgScope: {},
      settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
      effective: { rolloutPercent: 100 },
    },
    when: {
      all: [
        {
          fact: "medications[].code",
          operator: "in",
          value: { valueSet: options.valueSet.assetIdentity },
        },
        {
          fact: "patient.age",
          operator: "gte",
          value: 18,
        },
        {
          fact: "patient.age",
          operator: "derived",
          value: {
            formula: options.formula.assetIdentity,
            parameters: {
              heightCm: "extensions.local.frontdeskContext.heightCm",
              weightKg: "extensions.local.frontdeskContext.weightKg",
            },
            comparison: "gte",
            value: 20,
            unit: "kg/m2",
          },
        },
      ],
    },
    then: [
      {
        actionCardRef: options.actionCard.assetIdentity,
        actionCode: "STRONG_REMINDER",
        atSeverity: "HIGH",
        indicator: "critical",
        summary: "声明式运行资产用药复核",
        detail: "发布验证阶段使用静态提示卡字段；真实临床运行由机构生效版本 ACTION_CARD 物化覆盖。",
        source: { label: "MedKernel S5 本地上线演练" },
        suggestions: [
          { label: "记录人工复核", actionType: "OPEN_FORM", payload: { target: "cdss-review" } },
        ],
        overrideReasons: ["医师已完成人工复核"],
        requiresPhysicianConfirmation: true,
      },
    ],
    explain: {
      title: "S5 CDSS 声明式运行资产推荐",
      reason: "用药编码、公式计算和临床提示卡均来自当前机构生效版本。",
      sourceRef: "local-e2e:cdss-runtime-declarative-assets",
    },
  };
}

async function addRuleReleaseTestCases(
  page: Page,
  ruleId: string,
  options: { positiveContextSnapshotId: string; negativeContextSnapshotId: string },
) {
  const cases = [
    {
      caseType: "POSITIVE",
      expectedHit: true,
      expectedSeverity: "HIGH",
      expectedActionCode: "STRONG_REMINDER",
      contextSnapshotId: options.positiveContextSnapshotId,
    },
    {
      caseType: "NEGATIVE",
      expectedHit: false,
      expectedSeverity: null,
      expectedActionCode: null,
      contextSnapshotId: options.negativeContextSnapshotId,
    },
    {
      caseType: "BOUNDARY",
      expectedHit: true,
      expectedSeverity: "HIGH",
      expectedActionCode: "STRONG_REMINDER",
      contextSnapshotId: options.positiveContextSnapshotId,
    },
    {
      caseType: "CONFLICT",
      expectedHit: true,
      expectedSeverity: "HIGH",
      expectedActionCode: "STRONG_REMINDER",
      contextSnapshotId: options.positiveContextSnapshotId,
    },
  ];
  for (const testCase of cases) {
    const response = await postApi(
      page,
      `/engine/rule/rules/${encodeURIComponent(ruleId)}/test-cases`,
      {
        ...ruleApiContext(ruleId, `test-${testCase.caseType}`),
        ...testCase,
      },
    );
    await expectOk(response, `新增规则发布验证用例 ${testCase.caseType}`);
  }
}

function assertRuleReleaseTestRunPassed(testRun: unknown) {
  const failures = arrayField(testRun, "results").filter(
    (result) => textField(result, "status") !== "PASS",
  );
  expect(
    booleanField(testRun, "allPassed"),
    `S5 规则发布验证用例必须全部通过：${JSON.stringify(failures)}`,
  ).toBe(true);
}

function ruleApiContext(subject: string, step: string) {
  return {
    request_id: `req-cdss-runtime-${step}-${subject}`,
    trace_id: `trace-cdss-runtime-${step}-${subject}`,
    tenant_id: "t-e2e-rehearsal-local",
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function ruleGovernancePublishEvidence(targetState: string) {
  return {
    qualityGate: {
      schemaValid: true,
      terminologyBindingComplete: true,
      dependencyIntegrityVerified: true,
      safetyMonotonicityVerified: true,
      impactSimulationPassed: true,
      summary: `S5 声明式运行资产演练 ${targetState} 推进质量门已通过`,
    },
  };
}

async function activateRuntimeWithDeclarativeAssets(
  page: Page,
  options: {
    hospitalId: string;
    ruleRuntimeCandidate?: RuleRuntimeCandidate;
    valueSet: DeclarativeAssetCandidate;
    formula: DeclarativeAssetCandidate;
    actionCard: DeclarativeAssetCandidate;
  },
) {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取当前平台标准版本");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  expect(baselineAssets.baselineReleaseId, "当前平台标准版本必须存在").toBeTruthy();
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
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "读取当前医院生效版本");
  const currentRuntime = await responseData(current);
  const currentReleaseId = textField(currentRuntime, "release.releaseId");
  const currentPlatformBaselineReleaseId = textField(
    currentRuntime,
    "release.platformBaselineReleaseId",
  );
  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    ...(options.ruleRuntimeCandidate
      ? [
          {
            assetType: options.ruleRuntimeCandidate.assetType,
            assetIdentity: options.ruleRuntimeCandidate.assetIdentity,
            versionId: options.ruleRuntimeCandidate.versionId,
          },
        ]
      : []),
    {
      assetType: options.valueSet.assetType,
      assetIdentity: options.valueSet.assetIdentity,
      versionId: options.valueSet.versionId,
    },
    {
      assetType: options.formula.assetType,
      assetIdentity: options.formula.assetIdentity,
      versionId: options.formula.versionId,
    },
    {
      assetType: options.actionCard.assetType,
      assetIdentity: options.actionCard.assetIdentity,
      versionId: options.actionCard.versionId,
    },
  ]);
  const activationRequest = {
    platformBaselineReleaseId: baselineAssets.baselineReleaseId,
    expectedCurrentReleaseId: currentReleaseId,
    confirmedPlatformUpgradeDigest:
      currentReleaseId &&
      currentPlatformBaselineReleaseId &&
      currentPlatformBaselineReleaseId !== baselineAssets.baselineReleaseId
        ? await readPlatformUpgradeAnalysisDigest(
            page,
            options.hospitalId,
            baselineAssets.baselineReleaseId,
          )
        : null,
    activeAssets,
  };
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases`,
    activationRequest,
  );
  await expectOk(activated, "激活包含三类声明式资产的医院生效版本");
  const activatedRelease = await responseData(activated);
  const releaseId = requireText(
    textField(activatedRelease, "releaseId"),
    "激活机构生效版本必须返回 releaseId",
  );
  const currentAfterActivation = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfterActivation, "回读包含三类声明式资产的医院生效版本");
  const detail = (await responseData(currentAfterActivation)) as RuntimeReleaseDetail;
  expect(textField(detail, "release.releaseId"), "当前医院生效版本必须指向本次激活").toBe(
    releaseId,
  );
  const assets = [
    assertRuntimeContainsDeclarativeAsset(detail, options.valueSet),
    assertRuntimeContainsDeclarativeAsset(detail, options.formula),
    assertRuntimeContainsDeclarativeAsset(detail, options.actionCard),
  ];
  const ruleAsset = options.ruleRuntimeCandidate
    ? assertRuntimeContainsRuleAsset(detail, options.ruleRuntimeCandidate)
    : null;
  return {
    releaseId,
    revisionNo: numberField(detail, "release.revisionNo") ?? 0,
    manifestSha256: requireText(
      textField(detail, "release.manifestSha256"),
      "机构生效版本必须返回 manifestSha256",
    ),
    assets,
    ruleAsset,
    previousReleaseId: currentReleaseId,
    activationRequest,
  };
}

async function rollbackRuntimeAndAssertAssetsRemoved(
  page: Page,
  options: {
    hospitalId: string;
    targetReleaseId: string | null;
    consumer: string;
    removedAssets: RuntimeAssetSelection[];
  },
): Promise<RuntimeRollbackNegativeEvidence> {
  const targetReleaseId = requireText(
    options.targetReleaseId,
    "专项资产回滚负向证据必须有演练前机构生效版本",
  );
  await ensureReadySession(page, "engine-operator");
  const rollback = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases:rollback`,
    { targetReleaseId },
  );
  await expectOk(rollback, "回滚 S5 CDSS 声明式资产机构生效版本");
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "回读 S5 CDSS 声明式资产回滚后机构生效版本");
  const currentRuntime = runtimeReadbackEvidence(await responseData(current));
  assertAssetsRemoved(currentRuntime.assets, options.removedAssets, "回滚后 current runtime");

  const consumer = await getApi(
    page,
    "/engine/integration/knowledge-runtime/runtime-release/current",
  );
  await expectOk(consumer, "读取 S5 CDSS 声明式资产回滚后第三方运行契约");
  const runtimeConsumer = runtimeConsumerReadbackEvidence(await responseData(consumer));
  assertAssetsRemoved(runtimeConsumer.assets, options.removedAssets, "回滚后第三方运行契约");
  expect(runtimeConsumer.releaseId, "第三方运行契约 releaseId 必须与 current 一致").toBe(
    currentRuntime.releaseId,
  );
  expect(runtimeConsumer.revisionNo, "第三方运行契约 revisionNo 必须与 current 一致").toBe(
    currentRuntime.revisionNo,
  );
  expect(runtimeConsumer.manifestSha256, "第三方运行契约 manifestSha256 必须与 current 一致").toBe(
    currentRuntime.manifestSha256,
  );

  return {
    rollbackPosted: true,
    currentRuntimeReadbackVerified: true,
    runtimeConsumerReadbackVerified: true,
    consumer: options.consumer,
    consumerProbeMatchedRemovedAssets: false,
    removedAssets: options.removedAssets,
    currentRuntime,
    runtimeConsumer,
  };
}

async function readHospitalRuntimeCandidate(
  page: Page,
  hospitalId: string,
  options: { ruleCode: string },
): Promise<RuleRuntimeCandidate> {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-candidates?assetType=RULE&keyword=${encodeURIComponent(
      options.ruleCode,
    )}&page=1&size=20`,
  );
  await expectOk(response, "读取本轮 RULE runtime 候选");
  const candidate = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "assetType") === "RULE" &&
      textField(item, "assetIdentity") === options.ruleCode &&
      textField(item, "status") === "PUBLISHED",
  );
  const versionId = requireText(
    textField(candidate, "versionId"),
    `本轮 RULE 候选 ${options.ruleCode} 必须存在并返回统一资产版本 ID`,
  );
  expect(versionId.startsWith("av-"), "RULE runtime 候选必须使用统一资产 av-* 版本").toBe(true);
  return {
    assetType: "RULE",
    assetIdentity: options.ruleCode,
    versionId,
    versionNo: requireText(textField(candidate, "versionNo"), "RULE runtime 候选必须返回版本号"),
    contentHash: requireText(
      textField(candidate, "contentHash"),
      "RULE runtime 候选必须返回正文 hash",
    ),
    status: requireText(textField(candidate, "status"), "RULE runtime 候选必须返回状态"),
    sourceLayer: requireText(
      textField(candidate, "sourceLayer"),
      "RULE runtime 候选必须返回来源层级",
    ),
  };
}

function assertRuntimeContainsDeclarativeAsset(
  runtime: RuntimeReleaseDetail,
  candidate: DeclarativeAssetCandidate,
) {
  const asset = (runtime.items ?? []).find(
    (item) =>
      item.assetType === candidate.assetType &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(
    asset,
    `机构生效版本必须包含本轮 ${candidate.assetType} ${candidate.assetIdentity}`,
  ).toBeTruthy();
  expect(asset?.versionNo, `${candidate.assetType} runtime 清单必须返回版本号`).toBe(
    candidate.versionNo,
  );
  expect(asset?.contentHash, `${candidate.assetType} runtime 清单必须返回正文 hash`).toBe(
    candidate.contentHash,
  );
  return asset as RuntimeReleaseItem;
}

function assertRuntimeContainsRuleAsset(
  runtime: RuntimeReleaseDetail,
  candidate: RuleRuntimeCandidate,
) {
  const asset = (runtime.items ?? []).find(
    (item) =>
      item.assetType === candidate.assetType &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(asset, `机构生效版本必须包含本轮 RULE ${candidate.assetIdentity}`).toBeTruthy();
  expect(asset?.versionNo, "RULE runtime 清单必须返回版本号").toBe(candidate.versionNo);
  expect(asset?.contentHash, "RULE runtime 清单必须返回正文 hash").toBe(candidate.contentHash);
  return asset as RuntimeReleaseItem;
}

async function createClinicalContextFromFrontdesk(
  page: Page,
  suffix: string,
  reason: string,
  overrides: {
    medicationText?: string;
    heightCm?: string;
    weightKg?: string;
  } = {},
): Promise<ContextSnapshotSummary> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `推*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = patientDialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("64");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectHttpOk(patientResponse, "S5 演练创建脱敏患者");
  const patientId = requireText(
    textField(await responseData(patientResponse), "mpiId"),
    "S5 演练患者创建响应必须返回 MPI",
  );
  await expect(patientDialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(maskedName);
  await page.getByRole("button", { name: /检索过滤/ }).click();
  const row = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(maskedName)}.*${idLast4}`) })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/ }).click();
  await expect(page.getByRole("button", { name: "建立当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();
  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible();
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("S5 CDSS 用药复核演练");
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog
    .getByLabel("当前用药")
    .fill(overrides.medicationText ?? `J01GB03，S5 CDSS 演练 ${suffix}`);
  await contextDialog.getByLabel("身高 cm").fill(overrides.heightCm ?? "170");
  await contextDialog.getByLabel("体重 kg").fill(overrides.weightKg ?? "82");
  await contextDialog.getByLabel("医技报告项目").fill("身高体重评估");
  await contextDialog.getByLabel("报告结论").fill("身高 170cm，体重 82kg，需结合用药人工复核");
  await contextDialog.getByLabel("异常重点").fill("氨基糖苷类用药复核");
  await contextDialog.getByLabel("建立原因").fill(reason);
  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectHttpOk(contextResponse, "S5 演练建立 ACTIVE 快照");
  const context = await responseData(contextResponse);
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId,
    snapshotId: requireText(textField(context, "snapshotId"), "上下文响应必须返回 snapshotId"),
    runtimeReleaseId: requireText(
      textField(context, "runtimeReleaseId"),
      "上下文响应必须锁定 runtimeReleaseId",
    ),
    encounterId: textField(context, "resources.encounters[0].encounterId"),
    maskedName,
    idLast4,
  };
}

async function triggerRecommendationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      assets: RuntimeReleaseItem[];
    };
    ruleRuntimeCandidate: RuleRuntimeCandidate;
  },
) {
  const { snapshot } = options;
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "提醒与推荐" })).toBeVisible();
  await page.getByRole("button", { name: "登记触发评估" }).click();
  const dialog = page.getByRole("dialog", { name: "登记一次推荐触发评估" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  const snapshotButton = dialog.locator(`button[data-snapshot-id="${snapshot.snapshotId}"]`);
  await expect(snapshotButton, `提醒推荐页必须展示本轮临床快照 ${snapshot.snapshotId}`).toBeVisible(
    {
      timeout: 20_000,
    },
  );
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "触发时点", "签署医嘱");
  const evaluateResponsePromise = waitForRecommendationEvaluateResponse(page, snapshot);
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  await expectHttpOk(evaluateResponse, "临床用户从真实前台触发 S5 推荐评估");
  const evaluation = await responseData(evaluateResponse);
  expect(textField(evaluation, "status"), "推荐触发状态应为已评估").toBe("EVALUATED");
  expect(
    numberField(evaluation, "visibleCardCount") ?? 0,
    "应新增至少一张可见推荐卡",
  ).toBeGreaterThan(0);
  const triggerId = requireText(textField(evaluation, "triggerId"), "推荐评估响应必须返回触发 ID");
  const responseCardIds = arrayField(evaluation, "cards")
    .map((card) => textField(card, "cardId"))
    .filter((cardId): cardId is string => cardId !== null);
  expect(responseCardIds.length, "推荐评估响应必须返回本次可见推荐卡 ID").toBeGreaterThan(0);
  const relatedCardIds = await readRecommendationTriggerDiagnose(page, triggerId);
  expect(relatedCardIds, "推荐触发诊断必须关联本次评估响应中的可见推荐卡").toEqual(
    expect.arrayContaining(responseCardIds),
  );
  const recommendation = await findMaterializedRecommendationCard(page, relatedCardIds, {
    snapshot,
    runtime: options.runtime,
    ruleRuntimeCandidate: options.ruleRuntimeCandidate,
  });
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await page.getByLabel("患者或证据线索").fill(recommendation.cardId);
  await expect(
    page.getByRole("row", { name: new RegExp(escapeRegExp(recommendation.cardTitle)) }),
  ).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("共 1 张临床协同提醒卡")).toBeVisible({ timeout: 20_000 });
  return {
    triggerId,
    relatedCardIds,
    cardId: recommendation.cardId,
    triggerRuntimeReleaseId: recommendation.triggerRuntimeReleaseId,
    explanation: recommendation.explanation,
  };
}

async function readRecommendationTriggerDiagnose(page: Page, triggerId: string) {
  const diagnoseResponse = await getApi(
    page,
    `/engine/recommendations/triggers/${encodeURIComponent(triggerId)}/diagnose`,
  );
  await expectOk(diagnoseResponse, "推荐触发诊断应可由真实服务读取");
  const diagnose = await responseData(diagnoseResponse);
  const relatedCardIds = arrayField(diagnose, "relatedEntities.cards").filter(
    (value): value is string => typeof value === "string" && value.trim().length > 0,
  );
  expect(relatedCardIds.length, "推荐触发诊断必须返回本次触发关联推荐卡").toBeGreaterThan(0);
  return relatedCardIds;
}

async function findMaterializedRecommendationCard(
  page: Page,
  relatedCardIds: string[],
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      assets: RuntimeReleaseItem[];
    };
    ruleRuntimeCandidate: RuleRuntimeCandidate;
  },
) {
  const matchedCards: Array<{
    cardId: string;
    cardTitle: string;
    triggerRuntimeReleaseId: string | null;
    explanation: Record<string, unknown>;
  }> = [];
  for (const cardId of Array.from(new Set(relatedCardIds))) {
    const detailResponse = await getApi(
      page,
      `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
    );
    await expectOk(detailResponse, `推荐卡 ${cardId} 详情应可由真实服务读取`);
    const detail = await responseData(detailResponse);
    const explanationJson = requireText(
      textField(detail, "card.explanationJson"),
      `推荐卡 ${cardId} 详情必须返回解释 JSON`,
    );
    const explanation = JSON.parse(explanationJson) as Record<string, unknown>;
    const runtimeRelease = recordField(explanation, "runtimeRelease");
    const ruleExplanation = recordField(explanation, "ruleExplanation");
    const runtimeAssetEvidence = arrayField(ruleExplanation, "runtimeAssetEvidence");
    const matches =
      textField(detail, "trigger.contextSnapshotId") === options.snapshot.snapshotId &&
      textField(detail, "trigger.runtimeReleaseId") === options.runtime.releaseId &&
      textField(runtimeRelease, "assetVersionId") === options.ruleRuntimeCandidate.versionId &&
      textField(runtimeRelease, "assetVersionNo") === options.ruleRuntimeCandidate.versionNo &&
      textField(runtimeRelease, "contentHash") === options.ruleRuntimeCandidate.contentHash &&
      options.runtime.assets.every((asset) =>
        runtimeAssetEvidence.some(
          (item) =>
            textField(item, "assetType") === asset.assetType &&
            textField(item, "assetIdentity") === asset.assetIdentity &&
            textField(item, "assetVersion") === asset.versionNo &&
            textField(item, "contentHash") === asset.contentHash,
        ),
      );
    if (!matches) continue;
    matchedCards.push({
      cardId,
      cardTitle: requireText(textField(detail, "card.title"), `推荐卡 ${cardId} 必须返回业务标题`),
      triggerRuntimeReleaseId: textField(detail, "trigger.runtimeReleaseId"),
      explanation,
    });
  }
  expect(
    matchedCards.map((card) => card.cardId),
    "本次触发诊断关联卡中必须唯一定位本轮 S5 runtime 物化推荐卡",
  ).toHaveLength(1);
  return matchedCards[0];
}

function waitForRecommendationEvaluateResponse(page: Page, snapshot: ContextSnapshotSummary) {
  return page.waitForResponse(
    (response) => {
      if (response.request().method() !== "POST") return false;
      const pathname = new URL(response.url()).pathname;
      if (!pathname.endsWith("/engine/recommendations:evaluate")) return false;
      try {
        const payload = response.request().postDataJSON() as Record<string, unknown>;
        return (
          payload.contextSnapshotId === snapshot.snapshotId &&
          payload.patientId === snapshot.patientId &&
          payload.triggerType === "order-sign"
        );
      } catch {
        return false;
      }
    },
    { timeout: 30_000 },
  );
}

function assertRecommendationMaterializedDeclarativeAssets(options: {
  recommendation: {
    cardId: string;
    triggerId: string;
    relatedCardIds: string[];
    triggerRuntimeReleaseId: string | null;
    explanation: Record<string, unknown>;
  };
  runtime: {
    releaseId: string;
    assets: RuntimeReleaseItem[];
    ruleAsset: RuntimeReleaseItem | null;
  };
  ruleRuntimeCandidate: RuleRuntimeCandidate;
}) {
  expect(options.recommendation.triggerRuntimeReleaseId, "推荐触发必须绑定本轮 runtime").toBe(
    options.runtime.releaseId,
  );
  expect(
    options.recommendation.relatedCardIds,
    "推荐解释所用卡片必须来自本次触发诊断关联卡",
  ).toContain(options.recommendation.cardId);
  const runtimeRelease = recordField(options.recommendation.explanation, "runtimeRelease");
  expect(
    textField(runtimeRelease, "runtimeReleaseId"),
    "推荐解释必须记录本轮 runtimeReleaseId",
  ).toBe(options.runtime.releaseId);
  expect(textField(runtimeRelease, "assetVersionId"), "推荐解释必须记录 RULE 统一资产版本").toBe(
    options.ruleRuntimeCandidate.versionId,
  );
  expect(textField(runtimeRelease, "assetVersionNo"), "推荐解释必须记录 RULE 版本号").toBe(
    options.ruleRuntimeCandidate.versionNo,
  );
  expect(textField(runtimeRelease, "contentHash"), "推荐解释必须记录 RULE 正文 hash").toBe(
    options.ruleRuntimeCandidate.contentHash,
  );
  expect(
    options.runtime.ruleAsset?.versionId,
    "最终 runtime 必须包含推荐解释所指向的 RULE 统一资产版本",
  ).toBe(options.ruleRuntimeCandidate.versionId);
  const ruleExplanation = recordField(options.recommendation.explanation, "ruleExplanation");
  const runtimeAssetEvidence = arrayField(ruleExplanation, "runtimeAssetEvidence");
  expect(
    arrayField(ruleExplanation, "conditionEvidence").length,
    "推荐规则解释必须包含条件证据",
  ).toBeGreaterThan(0);
  for (const asset of options.runtime.assets) {
    const evidence = runtimeAssetEvidence.find(
      (item) =>
        textField(item, "assetType") === asset.assetType &&
        textField(item, "assetIdentity") === asset.assetIdentity &&
        textField(item, "assetVersion") === asset.versionNo &&
        textField(item, "contentHash") === asset.contentHash,
    );
    expect(
      evidence,
      `推荐解释必须包含 ${asset.assetType} ${asset.assetIdentity} 的 runtime 物化证据`,
    ).toBeTruthy();
    if (asset.assetType === "VALUE_SET") {
      expect(numberField(evidence, "expandedCount") ?? 0, "值集必须展开真实成员").toBeGreaterThan(
        0,
      );
    }
    if (asset.assetType === "FORMULA") {
      expect(textField(evidence, "runtimeFunction"), "公式必须解析为受控运行函数").toBe("BMI");
    }
    if (asset.assetType === "ACTION_CARD") {
      expect(textField(evidence, "actionCardRef"), "提示卡引用必须回指本轮资产").toBe(
        asset.assetIdentity,
      );
      expect(textField(evidence, "resolvedActionCardVersion")).toBe(asset.versionNo);
      expect(textField(evidence, "resolvedActionCardHash")).toBe(asset.contentHash);
      expect(booleanField(evidence, "requiresPhysicianConfirmation")).toBe(true);
    }
  }
  return {
    triggerId: options.recommendation.triggerId,
    relatedCardIds: options.recommendation.relatedCardIds,
    cardId: options.recommendation.cardId,
    triggerRuntimeReleaseId: options.recommendation.triggerRuntimeReleaseId,
    explanation: options.recommendation.explanation,
  };
}

async function attachCdssRuntimeDeclarativeAssetEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: CdssRuntimeDeclarativeApiEvidence;
    createdAssets: DeclarativeAssetCandidate[];
    rule: {
      assetType: "RULE";
      assetIdentity: string;
      ruleId: string;
      ruleVersionId: string;
    };
    ruleRuntimeCandidate: RuleRuntimeCandidate;
    declarativeRuntime: {
      releaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      activationRequest: unknown;
    };
    runtime: {
      releaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      ruleAsset: RuntimeReleaseItem | null;
      previousReleaseId: string | null;
    };
    activationRequest: unknown;
    clinicalTrigger: {
      triggerId: string;
      contextSnapshotId: string;
      runtimeReleaseId: string;
      cardId: string;
      relatedCardIds: string[];
    };
    recommendation: unknown;
    rollbackNegativeEvidence: RuntimeRollbackNegativeEvidence;
    observedStages: Set<string>;
  },
) {
  for (const stage of requiredStages) {
    expect(evidence.observedStages.has(stage), `缺少 S5 演练阶段：${stage}`).toBe(true);
  }
  await testInfo.attach("cdss-runtime-declarative-assets-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S5"],
        productLayers: ["CLINICAL_EXECUTION"],
        versionedAssets: ["VALUE_SET", "FORMULA", "ACTION_CARD"],
        serviceCombinations: ["CLINICAL_RUNTIME"],
        apiEvidence: evidence.apiEvidence,
        createdAssets: evidence.createdAssets,
        rule: evidence.rule,
        ruleRuntimeCandidate: evidence.ruleRuntimeCandidate,
        declarativeRuntime: {
          releaseId: evidence.declarativeRuntime.releaseId,
          revisionNo: evidence.declarativeRuntime.revisionNo,
          manifestSha256: evidence.declarativeRuntime.manifestSha256,
          assets: evidence.declarativeRuntime.assets,
          activationRequest: evidence.declarativeRuntime.activationRequest,
        },
        runtime: {
          releaseId: evidence.runtime.releaseId,
          revisionNo: evidence.runtime.revisionNo,
          manifestSha256: evidence.runtime.manifestSha256,
          assets: evidence.runtime.assets,
          ruleAsset: evidence.runtime.ruleAsset,
        },
        activationRequest: evidence.activationRequest,
        clinicalTrigger: evidence.clinicalTrigger,
        recommendation: evidence.recommendation,
        rollbackNegativeEvidence: evidence.rollbackNegativeEvidence,
        scenarioEvidence: [
          {
            code: "S5",
            observedStages: Array.from(evidence.observedStages),
          },
        ],
      },
      null,
      2,
    ),
  });
}

function recordCdssRuntimeDeclarativeAssetStage(stages: Set<string>, stage: string) {
  stages.add(stage);
}

async function localRehearsalHospitalId(page: Page) {
  const hospitals = await getApi(
    page,
    "/engine/org/org-units?keyword=本地上线演练医院&page=1&size=20",
  );
  await expectOk(hospitals, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(hospitals)).find(
    (item) =>
      textField(item, "name") === "本地上线演练医院" ||
      textField(item, "code") === "e2e-rehearsal-hospital",
  );
  return requireText(textField(hospital, "id"), "必须找到本地上线演练医院");
}

async function readPlatformUpgradeAnalysisDigest(
  page: Page,
  hospitalId: string,
  targetBaselineReleaseId: string,
) {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/platform-upgrade-analysis?targetBaselineReleaseId=${encodeURIComponent(
      targetBaselineReleaseId,
    )}`,
  );
  await expectOk(response, "读取 S5 演练平台升级分析");
  return requireText(
    textField(await responseData(response), "analysisDigest"),
    "S5 演练平台升级分析必须返回摘要",
  );
}

function resolveBaselineRuntimeAssets(value: unknown) {
  const baselineReleaseId = textField(value, "release.baselineReleaseId");
  const activeAssets = pageItems(value)
    .filter((item) => textField(item, "entryState") === "ACTIVE")
    .map((item): RuntimeAssetSelection | null => {
      const assetType = textField(item, "assetType");
      const assetIdentity = textField(item, "assetIdentity");
      if (!assetType || !assetIdentity) return null;
      return { assetType, assetIdentity, versionId: null };
    })
    .filter((item): item is RuntimeAssetSelection => item !== null);
  return {
    baselineReleaseId,
    activeAssets: uniqueRuntimeAssets(activeAssets),
  };
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const byKey = new Map<string, RuntimeAssetSelection>();
  for (const asset of assets) {
    byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);
  }
  return Array.from(byKey.values());
}

function runtimeSelection(asset: DeclarativeAssetCandidate): RuntimeAssetSelection {
  return {
    assetType: asset.assetType,
    assetIdentity: asset.assetIdentity,
    versionId: asset.versionId,
  };
}

function runtimeReadbackEvidence(value: unknown) {
  const evidence = {
    releaseId: requireText(
      textField(value, "release.releaseId"),
      "current runtime 必须返回 releaseId",
    ),
    revisionNo: numberField(value, "release.revisionNo") ?? 0,
    manifestSha256: requireText(
      textField(value, "release.manifestSha256"),
      "current runtime 必须返回 manifestSha256",
    ),
    assets: pageItems(value) as RuntimeReleaseItem[],
  };
  expect(evidence.revisionNo, "current runtime 必须返回 revisionNo").toBeGreaterThan(0);
  expect(evidence.assets.length, "current runtime 必须返回资产清单").toBeGreaterThan(0);
  return evidence;
}

function runtimeConsumerReadbackEvidence(value: unknown) {
  const evidence = {
    contractVersion: "v1" as const,
    releaseId: requireText(textField(value, "releaseId"), "runtime consumer 必须返回 releaseId"),
    revisionNo: numberField(value, "revisionNo") ?? 0,
    manifestSha256: requireText(
      textField(value, "manifestSha256"),
      "runtime consumer 必须返回 manifestSha256",
    ),
    assets: arrayField(value, "assets") as RuntimeReleaseItem[],
  };
  expect(textField(value, "contractVersion"), "runtime consumer 必须返回 v1 契约").toBe("v1");
  expect(evidence.revisionNo, "runtime consumer 必须返回 revisionNo").toBeGreaterThan(0);
  expect(evidence.assets.length, "runtime consumer 必须返回资产清单").toBeGreaterThan(0);
  return evidence;
}

function assertAssetsRemoved(
  assets: RuntimeReleaseItem[],
  removedAssets: RuntimeAssetSelection[],
  label: string,
) {
  for (const removed of removedAssets) {
    expect(
      assets.some(
        (asset) =>
          asset.assetType === removed.assetType &&
          asset.assetIdentity === removed.assetIdentity &&
          asset.versionId === removed.versionId,
      ),
      `${label} 不应继续包含本轮 ${removed.assetType}:${removed.assetIdentity}`,
    ).toBe(false);
  }
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  if (
    await dialog
      .getByText(optionText, { exact: true })
      .isVisible()
      .catch(() => false)
  ) {
    return;
  }
  const field = dialog.getByLabel(label);
  const selectSelector = field
    .locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')]",
    )
    .first()
    .locator(".ant-select-selector")
    .first();
  if (await selectSelector.isVisible().catch(() => false)) {
    await selectSelector.click();
  } else {
    await field.click();
  }
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown, `${label} 下拉应展开`).toBeVisible({ timeout: 5_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}\\s*$`, "u") })
    .first();
  await expect(option, `${label} 应存在选项 ${optionText}`).toBeVisible({ timeout: 10_000 });
  await option.click();
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

async function responseData(response: APIResponse) {
  const body = (await response.json()) as { data?: unknown };
  return body.data ?? null;
}

async function expectHttpOk(response: APIResponse, label: string) {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`${label} 失败：${response.status()} ${body}`);
  }
}

function pageItems(value: unknown) {
  const record = recordValue(value);
  const items = record ? record.items : undefined;
  return Array.isArray(items) ? items : [];
}

function arrayField(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return Array.isArray(raw) ? raw : [];
}

function recordField(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return recordValue(raw);
}

function textField(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberField(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function booleanField(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "boolean" ? raw : null;
}

function valueAtPath(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, part) => {
    if (current == null) return undefined;
    const match = /^([^[\]]+)(?:\[(\d+)\])?$/u.exec(part);
    if (!match) return undefined;
    const record = recordValue(current);
    if (!record) return undefined;
    let next: unknown = record[match[1]];
    if (match[2] !== undefined) {
      if (!Array.isArray(next)) return undefined;
      next = next[Number(match[2])];
    }
    return next;
  }, value);
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function requireText(value: string | null, message: string) {
  expect(value, message).toBeTruthy();
  return value ?? "";
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
