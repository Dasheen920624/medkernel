import { writeFile } from "node:fs/promises";

import type { TestInfo } from "@playwright/test";

export type PlatformAdminEntryCoreActionMenuKey =
  | "tenant-onboarding"
  | "identity-bindings"
  | "adapter-hub"
  | "system-providers";

export type PlatformAdminEntryCoreActionEvidence = {
  menuKey: PlatformAdminEntryCoreActionMenuKey;
  role: "platform-admin";
  path: string;
  frontdeskAction: string;
  serviceOperation: string;
  serviceStatus: number;
  readbackVerified: boolean;
  auditVerified: boolean;
};

const pathByMenuKey: Record<PlatformAdminEntryCoreActionMenuKey, string> = {
  "tenant-onboarding": "/tenant/onboarding",
  "identity-bindings": "/security/identity-binding",
  "adapter-hub": "/adapter/hub",
  "system-providers": "/system/providers",
};

export const platformAdminEntryCoreActionScopeStatement =
  "平台管理员 P0 入口核心动作代表矩阵：围绕服务机构、身份来源、系统接入和服务运行保障四个入口完成真实前台核心动作、服务回读与审计证据；不代表 6 个平台管理员入口全部闭环，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。";

export async function attachPlatformAdminEntryCoreActionEvidence(
  testInfo: TestInfo,
  evidence: PlatformAdminEntryCoreActionEvidence | PlatformAdminEntryCoreActionEvidence[],
) {
  const entryActions = Array.isArray(evidence) ? evidence : [evidence];
  for (const action of entryActions) {
    assertPlatformAdminEntryCoreAction(action);
  }
  const recordPath = testInfo.outputPath("platform-admin-entry-core-actions-codes.json");
  await writeFile(
    recordPath,
    `${JSON.stringify(
      {
        matrixCode: "PLATFORM_ADMIN_P0_ENTRY_CORE_ACTIONS",
        scopeStatement: platformAdminEntryCoreActionScopeStatement,
        entryActions,
      },
      null,
      2,
    )}\n`,
    "utf8",
  );
  await testInfo.attach("platform-admin-entry-core-actions-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}

function assertPlatformAdminEntryCoreAction(action: PlatformAdminEntryCoreActionEvidence) {
  if (action.role !== "platform-admin") {
    throw new Error(`${action.menuKey} 平台管理员入口动作必须由 platform-admin 执行`);
  }
  if (action.path !== pathByMenuKey[action.menuKey]) {
    throw new Error(`${action.menuKey} 平台管理员入口动作路径不匹配：${action.path}`);
  }
  if (
    !action.frontdeskAction ||
    !action.serviceOperation ||
    action.serviceStatus < 200 ||
    action.serviceStatus >= 300 ||
    !action.readbackVerified ||
    !action.auditVerified
  ) {
    throw new Error(`${action.menuKey} 平台管理员入口动作证据不完整`);
  }
}
