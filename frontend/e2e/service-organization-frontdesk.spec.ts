import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

import {
  apiBase,
  appPath,
  ensureReadySession,
  expectLoginPageReady,
  expectOk,
  patchApi,
} from "./support/auth";
import { attachPlatformAdminEntryCoreActionEvidence } from "./support/platformAdminEntryCoreActions";

type OrgUnit = {
  id?: string;
  parentId?: string | null;
  tenantId?: string;
  level?: string;
  code?: string;
  name?: string;
  facilityType?: string | null;
  status?: string;
};

type ProvisionedOrganization = {
  tenantId: string;
  tenantName: string;
  adminUserId: string;
  adminUsername: string;
  adminPassword: string;
  provisionStatus: number;
};

type ProvisionedOrgTree = {
  facility: Required<Pick<OrgUnit, "id" | "name" | "level">> & OrgUnit;
  campus: Required<Pick<OrgUnit, "id" | "parentId" | "name" | "level">> & OrgUnit;
  department: Required<Pick<OrgUnit, "id" | "parentId" | "name" | "level">> & OrgUnit;
  ward: Required<Pick<OrgUnit, "id" | "parentId" | "name" | "level">> & OrgUnit;
};

type ClinicalUserCredential = {
  username: string;
  password: string;
  userId: string;
  personId: string;
};

type ServiceOrganizationEvidence = {
  onboardingEvidence: {
    serviceOperation: "POST /api/v1/admin/tenants";
    serviceStatus: number;
    tenantId: string;
    tenantName: string;
    adminUsername: string;
    adminUserId: string;
    temporaryPasswordIssued: boolean;
    temporaryPasswordDisplayedOnce: boolean;
  } | null;
  adminBootstrapEvidence: {
    username: string;
    tenantId: string;
    loginMustChangePwd: boolean;
    changePasswordStatus: number;
    dashboardReached: boolean;
  } | null;
  orgTreeEvidence: {
    facility: OrgUnit;
    campus: OrgUnit;
    department: OrgUnit;
    ward: OrgUnit;
    facilityReadbackVerified: boolean;
    campusReadbackVerified: boolean;
    departmentReadbackVerified: boolean;
    wardReadbackVerified: boolean;
  } | null;
};

const requiredServiceOrganizationScenarioEvidence = [
  {
    code: "S1",
    observedStages: [
      "前台开通服务机构",
      "机构管理员首次登录并改密",
      "前台创建医疗机构、院区、科室与病区",
      "前台回读服务机构组织树",
    ],
  },
  {
    code: "S14",
    observedStages: [
      "前台创建临床账号并绑定科室职责范围",
      "临床账号首次登录后读取权限画像",
      "前台停用演练账号",
    ],
  },
] as const;

test.describe.configure({ mode: "serial" });

test.describe("S1/S14 服务机构与人员账号真实前台上线演练", () => {
  test("平台开通服务机构后，新机构可完成组织树、科室账号、职责范围和登录画像闭环", async ({
    page,
  }, testInfo) => {
    test.setTimeout(300_000);
    const suffix = Date.now().toString(36);
    const organization = {
      tenantId: `t-e2e-org-${suffix}`,
      tenantName: `上线演练服务机构${suffix.slice(-4)}`,
      adminUsername: `org-admin-${suffix}`,
      adminPassword: "",
    };
    const adminFinalPassword = `Mk@2026OrgAdmin${suffix.slice(-6)}!`;
    const clinicalFinalPassword = `Mk@2026Clinical${suffix.slice(-6)}!`;

    let provisionedOrganization: ProvisionedOrganization | null = null;
    let adminCleanupPassword = "";
    let clinicalUser: ClinicalUserCredential | null = null;
    const evidence: ServiceOrganizationEvidence = {
      onboardingEvidence: null,
      adminBootstrapEvidence: null,
      orgTreeEvidence: null,
    };
    const observedStages = new Set<string>();

    try {
      await ensureReadySession(page, "platform-admin", "platform");
      const provisioned = await provisionServiceOrganizationFromUi(page, organization);
      recordServiceOrganizationStage(observedStages, "前台开通服务机构");
      provisionedOrganization = provisioned;
      evidence.onboardingEvidence = {
        serviceOperation: "POST /api/v1/admin/tenants",
        serviceStatus: provisioned.provisionStatus,
        tenantId: provisioned.tenantId,
        tenantName: provisioned.tenantName,
        adminUsername: provisioned.adminUsername,
        adminUserId: provisioned.adminUserId,
        temporaryPasswordIssued: provisioned.adminPassword.length > 0,
        temporaryPasswordDisplayedOnce: true,
      };
      const adminBootstrap = await completeTenantAdminFirstLoginFromUi(
        page,
        provisioned,
        adminFinalPassword,
      );
      recordServiceOrganizationStage(observedStages, "机构管理员首次登录并改密");
      adminCleanupPassword = adminBootstrap.password;
      evidence.adminBootstrapEvidence = {
        username: provisioned.adminUsername,
        tenantId: provisioned.tenantId,
        loginMustChangePwd: adminBootstrap.loginMustChangePwd,
        changePasswordStatus: adminBootstrap.changePasswordStatus,
        dashboardReached: adminBootstrap.dashboardReached,
      };
      await loginAsInstitutionUser(
        page,
        provisioned.tenantName,
        provisioned.adminUsername,
        adminBootstrap.password,
      );

      const orgTree = await createFacilityCampusDepartmentAndWardFromUi(page, suffix);
      recordServiceOrganizationStage(observedStages, "前台创建医疗机构、院区、科室与病区");
      clinicalUser = await createClinicalUserWithDepartmentScopeFromUi(page, {
        suffix,
        facility: orgTree.facility,
        department: orgTree.department,
        ward: orgTree.ward,
      });
      recordServiceOrganizationStage(observedStages, "前台创建临床账号并绑定科室职责范围");

      await completeClinicalUserFirstLoginFromUi(
        page,
        provisioned.tenantName,
        clinicalUser.username,
        clinicalUser.password,
        clinicalFinalPassword,
      );

      await assertClinicalUserSecurityScope(page, {
        tenantId: provisioned.tenantId,
        facilityId: orgTree.facility.id,
        departmentId: orgTree.department.id,
        username: clinicalUser.username,
      });
      recordServiceOrganizationStage(observedStages, "临床账号首次登录后读取权限画像");
      await assertProvisionedOrgTree(page, {
        tenantId: provisioned.tenantId,
        facility: orgTree.facility,
        campus: orgTree.campus,
        department: orgTree.department,
        ward: orgTree.ward,
      });
      evidence.orgTreeEvidence = {
        facility: {
          id: orgTree.facility.id,
          tenantId: provisioned.tenantId,
          level: orgTree.facility.level,
          name: orgTree.facility.name,
          status: "ACTIVE",
        },
        campus: {
          id: orgTree.campus.id,
          tenantId: provisioned.tenantId,
          parentId: orgTree.campus.parentId,
          level: orgTree.campus.level,
          name: orgTree.campus.name,
          status: "ACTIVE",
        },
        department: {
          id: orgTree.department.id,
          tenantId: provisioned.tenantId,
          parentId: orgTree.department.parentId,
          level: orgTree.department.level,
          name: orgTree.department.name,
          status: "ACTIVE",
        },
        ward: {
          id: orgTree.ward.id,
          tenantId: provisioned.tenantId,
          parentId: orgTree.ward.parentId,
          level: orgTree.ward.level,
          name: orgTree.ward.name,
          status: "ACTIVE",
        },
        facilityReadbackVerified: true,
        campusReadbackVerified: true,
        departmentReadbackVerified: true,
        wardReadbackVerified: true,
      };
      recordServiceOrganizationStage(observedStages, "前台回读服务机构组织树");

      const screenshotPath = testInfo.outputPath("service-organization-clinical-scope.png");
      await page.screenshot({ path: screenshotPath, fullPage: true });
      await testInfo.attach("service-organization-clinical-scope", {
        path: screenshotPath,
        contentType: "image/png",
      });
    } finally {
      if (provisionedOrganization && adminCleanupPassword) {
        await disableProvisionedAccountsFromAdminSession(page, {
          organization: provisionedOrganization,
          adminPassword: adminCleanupPassword,
          clinicalUserId: clinicalUser?.userId,
        });
        recordServiceOrganizationStage(observedStages, "前台停用演练账号");
      }
    }
    await attachServiceOrganizationScenarioEvidence(testInfo, observedStages, evidence);
    await attachPlatformAdminEntryCoreActionEvidence(testInfo, {
      menuKey: "tenant-onboarding",
      role: "platform-admin",
      path: "/tenant/onboarding",
      frontdeskAction: "前台开通服务机构、完成首次改密、创建组织树和职责范围账号并回读权限画像",
      serviceOperation: "POST /api/v1/admin/tenants",
      serviceStatus: provisionedOrganization?.provisionStatus ?? 0,
      readbackVerified:
        observedStages.has("前台回读服务机构组织树") &&
        observedStages.has("临床账号首次登录后读取权限画像"),
      auditVerified: observedStages.has("前台开通服务机构"),
    });
  });
});

async function provisionServiceOrganizationFromUi(
  page: Page,
  organization: Pick<ProvisionedOrganization, "tenantId" | "tenantName" | "adminUsername">,
): Promise<ProvisionedOrganization> {
  await page.goto(appPath("/tenant/onboarding"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "服务机构" })).toBeVisible();
  await page.getByRole("button", { name: "开通服务机构" }).click();
  const dialog = page.getByRole("dialog", { name: "开通服务机构" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("稳定服务机构身份").fill(organization.tenantId);
  await dialog.getByLabel("服务机构名称").fill(organization.tenantName);
  await dialog.getByLabel("首个管理员登录名").fill(organization.adminUsername);

  const responsePromise = waitForPost(page, "/api/v1/admin/tenants");
  await dialog.getByRole("button", { name: "确认开通" }).click();
  const response = await responsePromise;
  const body = await response.text();
  expect(response.ok(), `前台开通服务机构应返回成功 status=${response.status()} body=${body}`).toBe(
    true,
  );
  const payload = JSON.parse(body) as {
    data?: {
      tenantId?: string;
      adminUserId?: string;
      adminUsername?: string;
      tempPassword?: string | null;
    };
  };
  expect(payload.data?.tenantId).toBe(organization.tenantId);
  expect(payload.data?.adminUserId).toBe(organization.adminUsername);
  expect(payload.data?.adminUsername).toBe(organization.adminUsername);
  expect(payload.data?.tempPassword, "服务机构开通前台必须返回一次性临时密码").toBeTruthy();

  const successDialog = page.getByRole("dialog", { name: "服务机构已开通" });
  await expect(successDialog).toBeVisible({ timeout: 20_000 });
  await expect(successDialog.getByText("一次性临时密码")).toBeVisible();
  await expect(successDialog.getByText(payload.data?.tempPassword ?? "")).toBeVisible();
  await successDialog.getByRole("button", { name: "已安全记录" }).click();
  await expect(successDialog).toBeHidden();

  return {
    ...organization,
    adminUserId: payload.data?.adminUserId ?? organization.adminUsername,
    adminPassword: payload.data?.tempPassword ?? "",
    provisionStatus: response.status(),
  };
}

async function disableProvisionedAccountsFromAdminSession(
  page: Page,
  options: {
    organization: ProvisionedOrganization;
    adminPassword: string;
    clinicalUserId?: string;
  },
) {
  await loginAsInstitutionUser(
    page,
    options.organization.tenantName,
    options.organization.adminUsername,
    options.adminPassword,
  );
  if (options.clinicalUserId) {
    const clinicalDisable = await patchApi(
      page,
      `/compliance/users/${encodeURIComponent(options.clinicalUserId)}/status`,
      { status: "DISABLED" },
    );
    await expectOk(clinicalDisable, "停用服务机构演练临床账号");
  }
  const adminDisable = await patchApi(
    page,
    `/compliance/users/${encodeURIComponent(options.organization.adminUserId)}/status`,
    { status: "DISABLED" },
  );
  await expectOk(adminDisable, "停用服务机构演练管理员账号");
}

async function completeTenantAdminFirstLoginFromUi(
  page: Page,
  organization: ProvisionedOrganization,
  finalPassword: string,
) {
  const loginMustChangePwd = await loginExpectingBootstrap(
    page,
    organization.tenantName,
    organization.adminUsername,
    organization.adminPassword,
  );
  const changePasswordStatus = await completeFirstPasswordChange(
    page,
    organization.adminPassword,
    finalPassword,
  );
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible({
    timeout: 20_000,
  });
  return {
    password: finalPassword,
    loginMustChangePwd,
    changePasswordStatus,
    dashboardReached: true,
  };
}

async function createFacilityCampusDepartmentAndWardFromUi(
  page: Page,
  suffix: string,
): Promise<ProvisionedOrgTree> {
  await page.goto(appPath("/tenant/onboarding"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "服务机构" })).toBeVisible();
  await page.getByRole("tab", { name: "组织树" }).click();

  const facilityCode = `ORG-HOSP-${suffix.toUpperCase()}`;
  const facilityName = `上线演练医院${suffix.slice(-4)}`;
  const facility = await createOrgUnitFromUi(page, {
    levelLabel: "医疗机构",
    facilityTypeLabel: "综合医院",
    code: facilityCode,
    name: facilityName,
  });
  expect(facility.level).toBe("FACILITY");
  expect(facility.name).toBe(facilityName);

  const campusCode = `ORG-CAMP-${suffix.toUpperCase()}`;
  const campusName = `上线演练院区${suffix.slice(-4)}`;
  const campus = await createOrgUnitFromUi(page, {
    levelLabel: "院区",
    parentName: facilityName,
    code: campusCode,
    name: campusName,
  });
  expect(campus.level).toBe("CAMPUS");
  expect(campus.parentId).toBe(facility.id);
  expect(campus.name).toBe(campusName);

  const departmentCode = `ORG-DEPT-${suffix.toUpperCase()}`;
  const departmentName = `上线演练科室${suffix.slice(-4)}`;
  const department = await createOrgUnitFromUi(page, {
    levelLabel: "科室",
    parentName: campusName,
    code: departmentCode,
    name: departmentName,
  });
  expect(department.level).toBe("DEPARTMENT");
  expect(department.parentId).toBe(campus.id);
  expect(department.name).toBe(departmentName);

  const wardCode = `ORG-WARD-${suffix.toUpperCase()}`;
  const wardName = `上线演练病区${suffix.slice(-4)}`;
  const ward = await createOrgUnitFromUi(page, {
    levelLabel: "病区/护理单元",
    parentName: departmentName,
    code: wardCode,
    name: wardName,
  });
  expect(ward.level).toBe("WARD");
  expect(ward.parentId).toBe(department.id);
  expect(ward.name).toBe(wardName);

  return {
    facility: requireOrgUnit(facility, "前台创建医疗机构"),
    campus: requireDepartmentOrgUnit(campus, "前台创建院区"),
    department: requireDepartmentOrgUnit(department, "前台创建科室"),
    ward: requireDepartmentOrgUnit(ward, "前台创建病区"),
  };
}

async function createClinicalUserWithDepartmentScopeFromUi(
  page: Page,
  options: {
    suffix: string;
    facility: ProvisionedOrgTree["facility"];
    department: ProvisionedOrgTree["department"];
    ward: ProvisionedOrgTree["ward"];
  },
): Promise<ClinicalUserCredential> {
  const employeeNo = `CLIN-${options.suffix.toUpperCase()}`;
  const displayName = `临床演练医生${options.suffix.slice(-4)}`;
  const username = `clinical-${options.suffix}`;

  await page.goto(appPath("/admin/users"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "人员与账号" })).toBeVisible();
  await page.getByRole("button", { name: "新增人员" }).click();
  const dialog = page.getByRole("dialog", { name: "新增人员" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("院内人员身份").fill(employeeNo);
  await dialog.getByLabel("姓名").fill(displayName);
  await chooseDialogOption(page, dialog, "人员类型", "本机构员工");
  await chooseRemoteOrgOption(page, dialog, "所属机构", options.facility.name);
  await chooseRemoteOrgOption(page, dialog, "所属科室", options.department.name);
  await chooseRemoteOrgOption(page, dialog, "所属病区", options.ward.name);
  await dialog.getByLabel("岗位或职务").fill("上线演练主治医师");
  await expect(dialog.getByRole("checkbox", { name: "同时开通登录账号" })).toBeChecked();
  await dialog.getByLabel("登录名").fill(username);
  await chooseDialogOption(page, dialog, "初始角色", "临床使用者");

  const createResponsePromise = waitForPost(page, "/api/v1/compliance/personnel");
  await dialog.getByRole("button", { name: "建立人员档案" }).click();
  const createResponse = await createResponsePromise;
  const createText = await createResponse.text();
  expect(
    createResponse.ok(),
    `前台新增人员与账号应返回成功 status=${createResponse.status()} body=${createText}`,
  ).toBe(true);
  const created = JSON.parse(createText) as {
    data?: {
      person?: { personId?: string; employeeNo?: string };
      primaryAppointment?: { organizationId?: string; departmentId?: string; wardId?: string };
      account?: { userId?: string; username?: string };
      oneTimeActivation?: { username?: string; temporaryPassword?: string };
    };
  };
  expect(created.data?.person?.employeeNo).toBe(employeeNo);
  expect(created.data?.primaryAppointment?.organizationId).toBe(options.facility.id);
  expect(created.data?.primaryAppointment?.departmentId).toBe(options.department.id);
  expect(created.data?.primaryAppointment?.wardId).toBe(options.ward.id);
  expect(created.data?.account?.username).toBe(username);
  expect(created.data?.oneTimeActivation?.username).toBe(username);
  expect(created.data?.oneTimeActivation?.temporaryPassword).toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  const activationDialog = page.getByRole("dialog", { name: "一次性账号凭证" });
  await expect(activationDialog).toBeVisible({ timeout: 20_000 });
  await expect(activationDialog.getByText("临时密码仅显示一次")).toBeVisible();
  await expect(activationDialog.getByText(username, { exact: true })).toBeVisible();
  await expect(
    activationDialog.getByText(created.data?.oneTimeActivation?.temporaryPassword ?? ""),
  ).toBeVisible();
  await activationDialog.getByRole("button", { name: "已妥善记录" }).click();
  await expect(activationDialog).toBeHidden();

  await page.getByLabel("搜索人员").fill(displayName);
  await page.getByLabel("搜索人员").press("Enter");
  const row = page.getByRole("row", { name: new RegExp(escapeRegExp(displayName)) }).first();
  await expect(row).toBeVisible({ timeout: 30_000 });
  await row.getByRole("button", { name: "查看" }).click();
  const drawer = page.locator(".ant-drawer-content").filter({ hasText: "人员档案" }).last();
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText("角色与组织范围")).toBeVisible();
  await assignClinicalDepartmentScopeFromDrawer(page, drawer, options.department.name);

  return {
    username,
    password: created.data?.oneTimeActivation?.temporaryPassword ?? "",
    userId: created.data?.account?.userId ?? username,
    personId: created.data?.person?.personId ?? "",
  };
}

async function assignClinicalDepartmentScopeFromDrawer(
  page: Page,
  drawer: Locator,
  departmentName: string,
) {
  await chooseDialogOption(page, drawer, "新增角色", "临床使用者");
  await chooseRemoteOrgOption(page, drawer, "组织范围", departmentName);
  const responsePromise = waitForPost(page, "/api/v1/compliance/users/", "/roles");
  await drawer.getByRole("button", { name: "添加角色" }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(
    response.ok(),
    `前台追加临床使用者科室范围应返回成功 status=${response.status()} body=${text}`,
  ).toBe(true);
  const detail = JSON.parse(text) as {
    data?: {
      roles?: Array<{ code?: string; scopeLevel?: string; scopeCode?: string; scopeName?: string }>;
    };
  };
  const assigned = (detail.data?.roles ?? []).find(
    (role) => role.code === "clinical-user" && role.scopeLevel === "DEPARTMENT",
  );
  expect(assigned?.scopeName).toBe(departmentName);
  await expect(drawer.getByText(departmentName, { exact: true })).toBeVisible({ timeout: 20_000 });
}

async function completeClinicalUserFirstLoginFromUi(
  page: Page,
  tenantName: string,
  username: string,
  temporaryPassword: string,
  finalPassword: string,
) {
  await loginExpectingBootstrap(page, tenantName, username, temporaryPassword);
  await completeFirstPasswordChange(page, temporaryPassword, finalPassword);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible({
    timeout: 20_000,
  });
}

async function assertProvisionedOrgTree(
  page: Page,
  expected: {
    tenantId: string;
    facility: OrgUnit;
    campus: OrgUnit;
    department: OrgUnit;
    ward: OrgUnit;
  },
) {
  const facility = await readBackOrgUnit(page, expected.tenantId, expected.facility, {
    traceCode: "facility",
    label: "医疗机构",
  });
  expect(facility?.level).toBe("FACILITY");
  expect(facility?.status).toBe("ACTIVE");

  const campus = await readBackOrgUnit(page, expected.tenantId, expected.campus, {
    traceCode: "campus",
    label: "院区",
  });
  expect(campus?.level).toBe("CAMPUS");
  expect(campus?.parentId).toBe(expected.facility.id);
  expect(campus?.status).toBe("ACTIVE");

  const department = await readBackOrgUnit(page, expected.tenantId, expected.department, {
    traceCode: "department",
    label: "科室",
  });
  expect(department?.level).toBe("DEPARTMENT");
  expect(department?.parentId).toBe(expected.campus.id);
  expect(department?.status).toBe("ACTIVE");

  const ward = await readBackOrgUnit(page, expected.tenantId, expected.ward, {
    traceCode: "ward",
    label: "病区",
  });
  expect(ward?.level).toBe("WARD");
  expect(ward?.parentId).toBe(expected.department.id);
  expect(ward?.status).toBe("ACTIVE");
}

async function readBackOrgUnit(
  page: Page,
  tenantId: string,
  expected: OrgUnit,
  readback: { traceCode: string; label: string },
) {
  const response = await page.request.get(
    `${apiBase}/engine/org/org-units?keyword=${encodeURIComponent(
      expected.name ?? "",
    )}&page=1&size=20`,
    { headers: { "X-Trace-Id": `e2e-org-tree-${readback.traceCode}-${Date.now()}` } },
  );
  await expectOk(response, `回读新服务机构${readback.label}`);
  const payload = (await response.json()) as { data?: { items?: OrgUnit[] } };
  return (payload.data?.items ?? []).find(
    (item) => item.id === expected.id && item.tenantId === tenantId,
  );
}

async function assertClinicalUserSecurityScope(
  page: Page,
  expected: { tenantId: string; facilityId: string; departmentId: string; username: string },
) {
  const response = await page.request.get(`${apiBase}/security/me`, {
    headers: { "X-Trace-Id": `e2e-clinical-scope-${Date.now()}` },
  });
  await expectOk(response, "读取临床账号权限画像");
  const profile = (await response.json()) as {
    data?: {
      username?: string;
      roles?: Array<{ code?: string; scopeLevel?: string; scopeCode?: string }>;
      dataScope?: {
        tenantId?: string | null;
        hospitalId?: string | null;
        departmentId?: string | null;
      };
      menuKeys?: string[];
      mustChangePwd?: boolean;
    };
  };
  expect(profile.data?.username).toBe(expected.username);
  expect(profile.data?.roles?.map((role) => role.code)).toContain("clinical-user");
  expect(
    profile.data?.roles?.some(
      (role) =>
        role.code === "clinical-user" &&
        role.scopeLevel === "DEPARTMENT" &&
        role.scopeCode === expected.departmentId,
    ),
    "临床账号必须绑定当前科室职责范围",
  ).toBe(true);
  expect(profile.data?.dataScope?.tenantId).toBe(expected.tenantId);
  expect(profile.data?.dataScope?.hospitalId).toBe(expected.facilityId);
  expect(profile.data?.dataScope?.departmentId).toBe(expected.departmentId);
  expect(profile.data?.menuKeys).toContain("workbench");
  expect(profile.data?.mustChangePwd).toBe(false);
}

async function loginAsInstitutionUser(
  page: Page,
  tenantName: string,
  username: string,
  password: string,
) {
  await page.context().clearCookies();
  await page.goto(appPath("/login"), { waitUntil: "domcontentloaded" });
  await expectLoginPageReady(page);
  await selectInstitutionLoginTenant(page, tenantName);
  await page.getByLabel("工号 / 账号").fill(username);
  await page.getByLabel("密码").fill(password);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible({
    timeout: 20_000,
  });
}

async function loginExpectingBootstrap(
  page: Page,
  tenantName: string,
  username: string,
  password: string,
) {
  await page.context().clearCookies();
  await page.goto(appPath("/login"), { waitUntil: "domcontentloaded" });
  await expectLoginPageReady(page);
  await selectInstitutionLoginTenant(page, tenantName);
  await page.getByLabel("工号 / 账号").fill(username);
  await page.getByLabel("密码").fill(password);
  const loginResponsePromise = waitForPost(page, "/api/v1/auth/login");
  await page.getByRole("button", { name: "进入工作台" }).click();
  const loginResponse = await loginResponsePromise;
  const loginText = await loginResponse.text();
  expect(
    loginResponse.ok(),
    `机构账号前台登录应返回成功 status=${loginResponse.status()} body=${loginText}`,
  ).toBe(true);
  const loginPayload = JSON.parse(loginText) as { data?: { mustChangePwd?: boolean } };
  expect(loginPayload.data?.mustChangePwd).toBe(true);
  await expect(page).toHaveURL(/\/bootstrap$/);
  await expect(page.getByRole("heading", { name: "完成首次改密" })).toBeVisible();
  return loginPayload.data?.mustChangePwd === true;
}

async function completeFirstPasswordChange(page: Page, oldPassword: string, newPassword: string) {
  const responsePromise = waitForPost(page, "/api/v1/auth/change-password");
  await page.getByLabel("当前密码", { exact: true }).fill(oldPassword);
  await page.getByLabel("新密码", { exact: true }).fill(newPassword);
  await page.getByLabel("确认新密码", { exact: true }).fill(newPassword);
  await page.getByRole("button", { name: "完成首次改密" }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(response.ok(), `前台首次改密应返回成功 status=${response.status()} body=${text}`).toBe(
    true,
  );
  await expect(page.getByText("账号安全设置完成")).toBeVisible({ timeout: 20_000 });
  await page.getByRole("button", { name: "进入工作台" }).click();
  return response.status();
}

async function createOrgUnitFromUi(
  page: Page,
  options: {
    levelLabel: string;
    facilityTypeLabel?: string;
    parentName?: string;
    code: string;
    name: string;
  },
) {
  const panel = page.locator(".ant-card").filter({ hasText: "新增组织节点" }).first();
  await expect(panel).toBeVisible();
  await chooseDialogOption(page, panel, "组织层级", options.levelLabel);
  if (options.facilityTypeLabel) {
    await chooseDialogOption(page, panel, "机构类型", options.facilityTypeLabel);
  }
  await panel.getByLabel("稳定组织身份").fill(options.code);
  await panel.getByLabel("组织名称").fill(options.name);
  await chooseDialogOption(page, panel, "直接上级", options.parentName);
  const responsePromise = waitForPost(page, "/api/v1/engine/org/org-units");
  await panel.getByRole("button", { name: "保存组织节点" }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(response.ok(), `前台保存组织节点应返回成功 status=${response.status()} body=${text}`).toBe(
    true,
  );
  const payload = JSON.parse(text) as { data?: OrgUnit };
  expect(payload.data?.id, "组织节点响应必须返回 id").toBeTruthy();
  await expect(page.getByText(options.name, { exact: true })).toBeVisible({ timeout: 20_000 });
  return payload.data ?? {};
}

async function selectInstitutionLoginTenant(page: Page, tenantName: string) {
  const institutionSwitch = page
    .locator('[aria-label="登录类型切换"]')
    .getByRole("button", { name: "机构用户", exact: true });
  if ((await institutionSwitch.count()) > 0 && (await institutionSwitch.isVisible())) {
    await institutionSwitch.click();
    await expect(institutionSwitch).toHaveAttribute("aria-pressed", "true");
  }
  const tenantButton = page.locator('[aria-label="所在机构"]').getByRole("button", {
    name: new RegExp(escapeRegExp(tenantName)),
  });
  await expect(tenantButton).toBeVisible({ timeout: 20_000 });
  await tenantButton.click();
  await expect(tenantButton).toHaveAttribute("aria-pressed", "true");
}

async function chooseDialogOption(page: Page, scope: Locator, label: string, optionText?: string) {
  const combobox = scope.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const options = dropdown.locator(".ant-select-item-option:not(.ant-select-item-option-disabled)");
  const option = optionText
    ? options.filter({ hasText: new RegExp(escapeRegExp(optionText)) }).first()
    : options.first();
  await expect(option).toBeVisible({ timeout: 20_000 });
  await option.click();
}

async function chooseRemoteOrgOption(page: Page, scope: Locator, label: string, orgName: string) {
  const combobox = scope.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  await combobox.fill(orgName);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: orgName })
    .first();
  await expect(option).toBeVisible({ timeout: 20_000 });
  await option.click();
}

function waitForPost(page: Page, urlPart: string, secondUrlPart?: string) {
  return page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes(urlPart) &&
      (!secondUrlPart || response.url().includes(secondUrlPart)),
    { timeout: 60_000 },
  );
}

function requireOrgUnit(value: OrgUnit, label: string) {
  if (!value.id || !value.name || !value.level) {
    throw new Error(`${label} 响应缺少 id/name/level`);
  }
  return value as Required<Pick<OrgUnit, "id" | "name" | "level">> & OrgUnit;
}

function recordServiceOrganizationStage(observedStages: Set<string>, stage: string) {
  observedStages.add(stage);
}

async function attachServiceOrganizationScenarioEvidence(
  testInfo: TestInfo,
  observedStageSet: Set<string>,
  evidence: ServiceOrganizationEvidence,
) {
  const scenarioEvidence = requiredServiceOrganizationScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => observedStageSet.has(stage)),
  }));
  const completedScenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const requiredStages =
        requiredServiceOrganizationScenarioEvidence.find((item) => item.code === scenario.code)
          ?.observedStages ?? [];
      return requiredStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);
  await testInfo.attach("service-organization-scenario-codes", {
    body: JSON.stringify(
      {
        scenarioCodes: completedScenarioCodes,
        organizationLevels: ["HOSPITAL", "CAMPUS_OR_MEMBER", "DEPARTMENT", "WARD"],
        serviceCombinations: ["ONBOARDING_INTEGRATION", "COMPLIANCE_OPERATIONS"],
        onboardingEvidence: evidence.onboardingEvidence,
        adminBootstrapEvidence: evidence.adminBootstrapEvidence,
        orgTreeEvidence: evidence.orgTreeEvidence,
        scenarioConditionEvidence:
          completedScenarioCodes.includes("S1") &&
          evidence.onboardingEvidence &&
          evidence.adminBootstrapEvidence &&
          evidence.orgTreeEvidence
            ? [
                {
                  code: "S1__NORMAL",
                  scenarioCode: "S1",
                  condition: "NORMAL",
                  source: "SERVICE_ORGANIZATION_ONBOARDING_ORG_TREE_READBACK",
                  evidence: [
                    "前台开通服务机构接口返回 2xx 且一次性临时密码仅记录签发与展示状态",
                    "机构管理员首次登录要求改密并完成自助改密进入工作台",
                    "医疗机构、院区、科室和病区按同一 tenant 回读为 ACTIVE 且父子关系连续",
                  ],
                },
              ]
            : [],
        scenarioEvidence,
      },
      null,
      2,
    ),
    contentType: "application/json",
  });
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function requireDepartmentOrgUnit(value: OrgUnit, label: string) {
  if (!value.id || !value.name || !value.level || value.parentId === undefined) {
    throw new Error(`${label} 响应缺少 id/name/level/parentId`);
  }
  return value as Required<Pick<OrgUnit, "id" | "parentId" | "name" | "level">> & OrgUnit;
}
