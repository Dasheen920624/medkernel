import { writeFile } from "node:fs/promises";

import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

import { apiBase, appPath, ensureReadySession, expectOk, patchApi, postApi } from "./support/auth";
import { attachPlatformAdminEntryCoreActionEvidence } from "./support/platformAdminEntryCoreActions";

type CreatedPersonnel = {
  userId: string;
  username: string;
  displayName: string;
};

type IdentityBindingPayload = {
  data?: {
    bindingId?: string;
    userId?: string;
    providerType?: string;
    subjectHint?: string;
    status?: string;
    version?: number;
  };
};

type IdentityPlaintextSafetyEvidence = {
  subjectHintIncludesTail: boolean;
  listOmitsExternalSubjectDigest: boolean;
  listOmitsExternalSubjectPlaintext: boolean;
  duplicateStatus: number;
  duplicateRejectedMessage: string;
};

type IdentityUnbindingEvidence = {
  bindingId: string;
  status: string;
  versionAdvanced: boolean;
};

type IdentityCleanupEvidence = {
  createdAccountDisabled: boolean;
  duplicateAccountDisabled: boolean;
  bindingUnboundOrAlreadyUnbound: boolean;
};

type IdentityBindingApiEvidence = {
  personnelCreated: boolean;
  bindingPosted: boolean;
  bindingListRead: boolean;
  plaintextNotPersisted: boolean;
  duplicateRejected: boolean;
  unbindPosted: boolean;
  cleanupCompleted: boolean;
};

type IdentityBindingScenarioEvidence = {
  scenarioCodes: ["S14"];
  productLayers: ["FOUNDATION_GOVERNANCE"];
  serviceCombinations: ["COMPLIANCE_OPERATIONS"];
  apiEvidence: IdentityBindingApiEvidence;
  createdPersonnel: CreatedPersonnel[];
  binding: NonNullable<IdentityBindingPayload["data"]> | null;
  plaintextSafety: IdentityPlaintextSafetyEvidence | null;
  unbinding: IdentityUnbindingEvidence | null;
  cleanup: IdentityCleanupEvidence;
  scenarioConditionEvidence: Array<{
    code: "S14__NORMAL";
    scenarioCode: "S14";
    condition: "NORMAL";
    source: "IDENTITY_BINDING_LIFECYCLE_PLAINTEXT_SAFETY";
    evidence: string[];
  }>;
  scenarioEvidence: Array<{ code: "S14"; observedStages: string[] }>;
};

test.describe("身份来源真实前台上线演练", () => {
  test("平台管理员可前台绑定和解绑院内身份来源且身份原文不落库", async ({ page }, testInfo) => {
    test.setTimeout(180_000);
    const suffix = Date.now().toString(36);
    const externalSubject = `EMP-FRONT-${suffix.toUpperCase()}`;
    const observedStages = new Set<string>();
    const apiEvidence: IdentityBindingApiEvidence = {
      personnelCreated: false,
      bindingPosted: false,
      bindingListRead: false,
      plaintextNotPersisted: false,
      duplicateRejected: false,
      unbindPosted: false,
      cleanupCompleted: false,
    };
    const createdPersonnel: CreatedPersonnel[] = [];
    const cleanupEvidence: IdentityCleanupEvidence = {
      createdAccountDisabled: false,
      duplicateAccountDisabled: false,
      bindingUnboundOrAlreadyUnbound: false,
    };
    let created: CreatedPersonnel | null = null;
    let duplicateCandidate: CreatedPersonnel | null = null;
    let binding: NonNullable<IdentityBindingPayload["data"]> | null = null;
    let plaintextSafety: IdentityPlaintextSafetyEvidence | null = null;
    let unbinding: IdentityUnbindingEvidence | null = null;

    try {
      created = await createPersonnelAccountFromUi(page, suffix);
      duplicateCandidate = await createPersonnelAccountFromUi(page, `${suffix}b`);
      createdPersonnel.push(created, duplicateCandidate);
      apiEvidence.personnelCreated = createdPersonnel.length === 2;
      observedStages.add("前台创建身份来源演练人员账号");

      binding = await bindIdentitySourceFromUi(page, created, externalSubject);
      apiEvidence.bindingPosted = binding.providerType === "EMPLOYEE_NO" && binding.status === "ACTIVE";
      observedStages.add("前台绑定院内身份来源");

      plaintextSafety = await assertIdentityPlaintextIsNotPersisted(
        page,
        binding.bindingId ?? "",
        externalSubject,
        duplicateCandidate.userId,
      );
      apiEvidence.bindingListRead = true;
      apiEvidence.plaintextNotPersisted =
        plaintextSafety.subjectHintIncludesTail &&
        plaintextSafety.listOmitsExternalSubjectDigest &&
        plaintextSafety.listOmitsExternalSubjectPlaintext;
      apiEvidence.duplicateRejected = plaintextSafety.duplicateStatus === 409;
      observedStages.add("列表回读只展示脱敏身份提示");
      observedStages.add("后端拒绝重复外部身份绑定");

      unbinding = await unbindIdentitySourceFromUi(page, binding.bindingId ?? "", binding.version ?? 1);
      apiEvidence.unbindPosted = unbinding.status === "UNBOUND" && unbinding.versionAdvanced;
      observedStages.add("前台解绑身份来源并保留历史证据");
    } finally {
      try {
        await cleanupIdentityBindingRehearsal(
          page,
          binding?.bindingId,
          created,
          duplicateCandidate,
          cleanupEvidence,
        );
        apiEvidence.cleanupCompleted =
          cleanupEvidence.createdAccountDisabled &&
          cleanupEvidence.duplicateAccountDisabled &&
          cleanupEvidence.bindingUnboundOrAlreadyUnbound;
        if (apiEvidence.cleanupCompleted) {
          observedStages.add("停用身份来源演练账号");
        }
      } finally {
        await attachIdentityBindingScenarioEvidence(testInfo, {
          scenarioCodes: ["S14"],
          productLayers: ["FOUNDATION_GOVERNANCE"],
          serviceCombinations: ["COMPLIANCE_OPERATIONS"],
          apiEvidence,
          createdPersonnel,
          binding,
          plaintextSafety,
          unbinding,
          cleanup: cleanupEvidence,
          scenarioConditionEvidence: [
            {
              code: "S14__NORMAL",
              scenarioCode: "S14",
              condition: "NORMAL",
              source: "IDENTITY_BINDING_LIFECYCLE_PLAINTEXT_SAFETY",
              evidence: [
                "平台管理员前台绑定院内身份来源并列表脱敏回读",
                "重复外部身份被后端拒绝，解绑后保留历史证据并清理演练账号",
              ],
            },
          ],
          scenarioEvidence: [{ code: "S14", observedStages: Array.from(observedStages) }],
        });
        await attachPlatformAdminEntryCoreActionEvidence(testInfo, {
          menuKey: "identity-bindings",
          role: "platform-admin",
          path: "/security/identity-binding",
          frontdeskAction: "前台绑定院内身份来源、列表脱敏回读、重复身份拒绝并解绑留痕",
          serviceOperation: "POST /api/v1/compliance/identity-bindings",
          serviceStatus: apiEvidence.bindingPosted ? 200 : 0,
          readbackVerified:
            apiEvidence.bindingListRead &&
            apiEvidence.plaintextNotPersisted &&
            apiEvidence.duplicateRejected &&
            apiEvidence.unbindPosted,
          auditVerified: apiEvidence.bindingPosted && apiEvidence.unbindPosted,
        });
      }
    }
  });
});

async function createPersonnelAccountFromUi(page: Page, suffix: string): Promise<CreatedPersonnel> {
  await ensureReadySession(page, "platform-admin");
  await page.goto(appPath("/admin/users"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "人员与账号" })).toBeVisible();

  const employeeNo = `IDB-${suffix.toUpperCase()}`;
  const displayName = `身份绑定演练人员${suffix.slice(-4)}`;
  const username = `idb-${suffix}`;

  await page.getByRole("button", { name: "新增人员" }).click();
  const dialog = page.getByRole("dialog", { name: "新增人员" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("院内人员身份").fill(employeeNo);
  await dialog.getByLabel("姓名").fill(displayName);
  await chooseDialogOption(page, dialog, "人员类型", "本机构员工");
  await chooseFirstRemoteOption(page, dialog, "所属机构");
  await dialog.getByLabel("岗位或职务").fill("身份来源上线演练");
  await expect(dialog.getByRole("checkbox", { name: "同时开通登录账号" })).toBeChecked();
  await dialog.getByLabel("登录名").fill(username);
  await chooseDialogOption(page, dialog, "初始角色", "临床使用者");

  const createResponsePromise = waitForPost(page, "/api/v1/compliance/personnel");
  await dialog.getByRole("button", { name: "建立人员档案" }).click();
  const createResponse = await createResponsePromise;
  const createText = await createResponse.text();
  expect(
    createResponse.ok(),
    `前台创建身份来源演练人员应返回成功 status=${createResponse.status()} body=${createText}`,
  ).toBe(true);
  const created = JSON.parse(createText) as {
    data?: {
      person?: { employeeNo?: string };
      account?: { userId?: string; username?: string };
      identities?: unknown[];
    };
  };
  expect(created.data?.person?.employeeNo).toBe(employeeNo);
  expect(created.data?.account?.username).toBe(username);
  expect(created.data?.identities ?? [], "身份来源应在专属页面前台绑定，不在建档时预置").toEqual(
    [],
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  const activationDialog = page.getByRole("dialog", { name: "一次性账号凭证" });
  await expect(activationDialog).toBeVisible({ timeout: 20_000 });
  await activationDialog.getByRole("button", { name: "已妥善记录" }).click();
  await expect(activationDialog).toBeHidden({ timeout: 20_000 });

  return {
    userId: created.data?.account?.userId ?? "",
    username,
    displayName,
  };
}

async function bindIdentitySourceFromUi(
  page: Page,
  person: CreatedPersonnel,
  externalSubject: string,
): Promise<NonNullable<IdentityBindingPayload["data"]>> {
  await page.goto(appPath("/security/identity-binding"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "身份来源" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);

  await page.getByRole("button", { name: "单个绑定" }).click();
  const dialog = page.getByRole("dialog", { name: "单个绑定身份来源" });
  await expect(dialog).toBeVisible();
  await searchDialogOption(page, dialog, "人员账号", person.displayName, person.displayName);
  await chooseDialogOption(page, dialog, "身份来源", "院内工号");
  await dialog.getByLabel("院内人员身份").fill(externalSubject);
  await dialog
    .getByLabel("绑定原因")
    .fill("真实前台演练：平台管理员核验人员档案后绑定院内身份来源。");

  const bindResponsePromise = waitForPost(page, "/api/v1/compliance/identity-bindings");
  await dialog.getByRole("button", { name: "确认绑定" }).click();
  const bindResponse = await bindResponsePromise;
  const bindText = await bindResponse.text();
  expect(
    bindResponse.ok(),
    `前台绑定身份来源应返回成功 status=${bindResponse.status()} body=${bindText}`,
  ).toBe(true);
  const payload = JSON.parse(bindText) as IdentityBindingPayload;
  expect(payload.data?.userId).toBe(person.userId);
  expect(payload.data?.providerType).toBe("EMPLOYEE_NO");
  expect(payload.data?.status).toBe("ACTIVE");
  expect(payload.data?.subjectHint).toContain(externalSubject.slice(-4));
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await expect(page.getByText(person.displayName, { exact: true })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText("身份已绑定").first()).toBeVisible({ timeout: 20_000 });
  return payload.data ?? {};
}

async function assertIdentityPlaintextIsNotPersisted(
  page: Page,
  bindingId: string,
  externalSubject: string,
  duplicateUserId: string,
): Promise<IdentityPlaintextSafetyEvidence> {
  expect(bindingId, "身份来源绑定必须返回 bindingId").toBeTruthy();
  expect(duplicateUserId, "重复身份校验必须使用真实已开通人员账号").toBeTruthy();
  const response = await page.request.get(`${apiBase}/compliance/identity-bindings`, {
    params: { page: "1", size: "20" },
    headers: { "X-Trace-Id": `e2e-identity-binding-${Date.now()}` },
  });
  await expectOk(response, "读取身份来源绑定列表");
  const payload = (await response.json()) as {
    data?: {
      items?: Array<{ bindingId?: string; subjectHint?: string; externalSubjectDigest?: string }>;
    };
  };
  const binding = (payload.data?.items ?? []).find((item) => item.bindingId === bindingId);
  expect(binding?.subjectHint, "列表只应返回脱敏身份提示").toContain(externalSubject.slice(-4));
  expect(binding?.externalSubjectDigest, "身份来源前台列表不得返回身份摘要字段").toBeUndefined();
  const serializedBinding = JSON.stringify(binding);
  expect(serializedBinding, "身份来源前台列表不得返回身份原文").not.toContain(externalSubject);

  const duplicateResponse = await postApi(page, "/compliance/identity-bindings", {
    userId: duplicateUserId,
    providerType: "EMPLOYEE_NO",
    externalSubject: externalSubject,
    reason: "真实前台演练：重复外部身份安全检查",
  });
  const duplicateText = await duplicateResponse.text();
  expect(
    duplicateResponse.status(),
    `同一外部身份已绑定其他真实用户时必须由后端拒绝 body=${duplicateText}`,
  ).toBe(409);
  expect(duplicateText).toContain("该外部身份已绑定其他用户");
  return {
    subjectHintIncludesTail: binding?.subjectHint?.includes(externalSubject.slice(-4)) === true,
    listOmitsExternalSubjectDigest: binding?.externalSubjectDigest === undefined,
    listOmitsExternalSubjectPlaintext: !serializedBinding.includes(externalSubject),
    duplicateStatus: duplicateResponse.status(),
    duplicateRejectedMessage: duplicateText,
  };
}

async function unbindIdentitySourceFromUi(
  page: Page,
  bindingId: string,
  expectedVersion: number,
): Promise<IdentityUnbindingEvidence> {
  const row = identityBindingRowById(page, bindingId);
  await expect(row, "身份来源绑定行应保留到前台列表").toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: "解绑" }).click();
  const dialog = page.getByRole("dialog", { name: "解除身份来源" });
  await expect(dialog).toBeVisible();
  await dialog
    .getByLabel("解绑原因")
    .fill("真实前台演练：人员身份来源切换，解除旧绑定并保留历史证据。");

  const unbindResponsePromise = waitForPost(
    page,
    `/api/v1/compliance/identity-bindings/${bindingId}:unbind`,
  );
  await dialog.getByRole("button", { name: "确认解绑" }).click();
  const unbindResponse = await unbindResponsePromise;
  const unbindText = await unbindResponse.text();
  expect(
    unbindResponse.ok(),
    `前台解绑身份来源应返回成功 status=${unbindResponse.status()} body=${unbindText}`,
  ).toBe(true);
  const payload = JSON.parse(unbindText) as IdentityBindingPayload;
  expect(payload.data?.bindingId).toBe(bindingId);
  expect(payload.data?.status).toBe("UNBOUND");
  expect(payload.data?.version).toBe(expectedVersion + 1);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await expect(identityBindingRowById(page, bindingId).getByText("已解绑")).toBeVisible({
    timeout: 20_000,
  });
  return {
    bindingId,
    status: payload.data?.status ?? "",
    versionAdvanced: payload.data?.version === expectedVersion + 1,
  };
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, option: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(option)}\\s*$`) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function searchDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  searchText: string,
  optionText: string,
) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  await combobox.fill(searchText);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(escapeRegExp(optionText)) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function chooseFirstRemoteOption(page: Page, dialog: Locator, label: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

function identityBindingRowById(page: Page, bindingId: string) {
  return page.locator(`tr[data-row-key="${bindingId}"]`).first();
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
  );
}

async function disableCreatedAccount(page: Page, userId: string, label: string) {
  if (!userId) {
    return false;
  }
  const disabled = await patchApi(page, `/compliance/users/${encodeURIComponent(userId)}/status`, {
    status: "DISABLED",
  });
  await expectOk(disabled, label);
  return true;
}

async function cleanupIdentityBindingRehearsal(
  page: Page,
  bindingId: string | undefined,
  created: CreatedPersonnel | null,
  duplicateCandidate: CreatedPersonnel | null,
  evidence: IdentityCleanupEvidence,
) {
  const cleanupTasks = [
    async () => {
      evidence.bindingUnboundOrAlreadyUnbound = await unbindCreatedIdentityIfNeeded(page, bindingId);
    },
    async () => {
      evidence.createdAccountDisabled = await disableCreatedAccount(
        page,
        created?.userId ?? "",
        "停用身份来源前台演练账号",
      );
    },
    async () => {
      evidence.duplicateAccountDisabled = await disableCreatedAccount(
        page,
        duplicateCandidate?.userId ?? "",
        "停用身份来源重复校验账号",
      );
    },
  ];
  const results = await Promise.allSettled(cleanupTasks.map((cleanup) => cleanup()));
  const failures = results
    .filter((result): result is PromiseRejectedResult => result.status === "rejected")
    .map((result) => String(result.reason));
  if (failures.length > 0) {
    throw new Error(`身份来源前台演练清理失败：${failures.join("；")}`);
  }
}

async function unbindCreatedIdentityIfNeeded(page: Page, bindingId: string | undefined) {
  if (!bindingId) {
    return true;
  }
  const response = await page.request.get(`${apiBase}/compliance/identity-bindings`, {
    params: { page: "1", size: "20" },
    headers: { "X-Trace-Id": `e2e-identity-cleanup-${Date.now()}` },
  });
  await expectOk(response, "清理前读取身份来源绑定");
  const payload = (await response.json()) as {
    data?: {
      items?: Array<{ bindingId?: string; status?: string; version?: number }>;
    };
  };
  const binding = (payload.data?.items ?? []).find((item) => item.bindingId === bindingId);
  if (!binding || binding.status === "UNBOUND") {
    return true;
  }
  const unbound = await postApi(page, `/compliance/identity-bindings/${bindingId}:unbind`, {
    reason: "真实前台演练清理：失败路径解除临时身份来源绑定。",
    expectedVersion: binding.version ?? 1,
  });
  await expectOk(unbound, "清理身份来源前台演练绑定");
  return true;
}

async function attachIdentityBindingScenarioEvidence(
  testInfo: TestInfo,
  evidence: IdentityBindingScenarioEvidence,
) {
  const recordPath = testInfo.outputPath("identity-binding-scenario-codes.json");
  await writeFile(recordPath, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
  await testInfo.attach("identity-binding-scenario-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
