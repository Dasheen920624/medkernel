import { expect, test, type TestInfo } from "@playwright/test";

import { appPath, ensureReadySession } from "./support/auth";

type DomainFacadeEngineEvidence = {
  engine: string;
  sharedHandlerClass: string;
  b0Route: string;
  b0Assertion: string;
  deterministic: boolean;
  handlerPresent: boolean;
  clinicalContentSeeded: boolean;
};

type DomainFacadeB0Evidence = {
  code: string;
  kind: string;
  status: string;
  evidenceId: string;
  b0Executable: boolean;
  modelRequired: boolean;
  clinicalContentSeeded: boolean;
  newBusinessEngineRequired: boolean;
  honestEmptyWhenAssetsMissing: boolean;
  serviceCombinationMembersResolvable: boolean;
  assetSeedPolicy: string;
  b0Workflows: string[];
  engineEvidence: DomainFacadeEngineEvidence[];
  memberFacadeCodes: string[];
  verifiedMemberFacadeCodes: string[];
};

const B0_EVIDENCE_OPERATION = "GET /engine/domain-facades/b0-evidence";
const B0_EVIDENCE_PATH = "/medkernel/api/v1/engine/domain-facades/b0-evidence";

test.describe("领域门面无模型证据真实前台落点", () => {
  test("运营员从前台回读全专业领域门面 B0 复用链路证据", async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width: 1440, height: 960 });
    await ensureReadySession(page, "engine-operator");

    const evidenceResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        response.url().includes("/engine/domain-facades/b0-evidence"),
    );
    await page.goto(appPath("/knowledge/domain-facades/b0-evidence"), {
      waitUntil: "domcontentloaded",
    });
    const evidenceResponse = await evidenceResponsePromise;
    expect(evidenceResponse.ok(), "领域门面 B0 证据必须来自真实 GET").toBe(true);
    const evidenceRows = await responseRows(evidenceResponse);

    await expect(page.getByRole("heading", { name: "领域门面无模型证据" })).toBeVisible();
    await expect(page.getByText("17 张领域门面")).toBeVisible();
    await expect(page.locator(".ant-statistic-title", { hasText: "无模型 B0 主链路" })).toBeVisible();
    await expect(
      page.getByText("不预置真实医学内容/不新增专属业务引擎/不声明完整专业领域上线"),
    ).toBeVisible();
    const firstFacadeCode = evidenceRows[0]?.code ?? "SPECIALTY-EXT-01";
    await expect(
      page.getByRole("row", { name: new RegExp(`\\b${firstFacadeCode}\\b`) }),
    ).toBeVisible();

    await testInfo.attach("domain-facade-b0-evidence-codes", {
      contentType: "application/json",
      body: JSON.stringify(
        {
          domainFacadeCodes: evidenceRows.map((row) => row.code),
          domainFacadeB0Coverage: ["CLINICAL_SPECIALTY_DOMAIN_B0_FACADE_CATALOG"],
          apiEvidence: {
            b0EvidenceReadFromFrontdesk: {
              operation: B0_EVIDENCE_OPERATION,
              status: evidenceResponse.status(),
            },
          },
          scopeStatement: {
            provesOnly:
              "仅证明 17 张专业领域门面复用同一 B0 引擎链路、模型非必需、无临床内容预置和缺资产诚实空态；不声明完整专业领域、完整 S28/S29/S30/S37/S38/S39、真实消费者、业务闭环、完整 S0-S40 或完整上线验收",
            notFullSpecialtyDomainCoverage: true,
            notScenarioConditionRows: true,
            notFullS0S40Coverage: true,
            notFullLaunchReadiness: true,
            notCompleteScenarioCodes: ["S28", "S29", "S30", "S37", "S38", "S39"],
          },
          facadeEvidence: evidenceRows,
        },
        null,
        2,
      ),
    });

    expect(evidenceRows).toHaveLength(17);
    expect(evidenceRows.every((row) => row.b0Executable && row.modelRequired === false)).toBe(
      true,
    );
  });
});

async function responseRows(response: {
  json(): Promise<unknown>;
}): Promise<DomainFacadeB0Evidence[]> {
  const body = (await response.json()) as { data?: unknown };
  expect(Array.isArray(body.data), B0_EVIDENCE_PATH).toBe(true);
  return body.data as DomainFacadeB0Evidence[];
}
