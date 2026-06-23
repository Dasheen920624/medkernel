import { expect, test } from "@playwright/test";

const hostOrigin = "http://127.0.0.1:4174";

test("independent business host completes iframe launch and receives physician feedback", async ({
  page,
}) => {
  await page.route("**/medkernel/api/v1/engine/embed/launch", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          userId: "doctor-e2e",
          roleCode: "clinical-user",
          tenantId: "tenant-e2e",
          patientId: "MPI-E2E-001",
          encounterId: "ENC-E2E-001",
          triggerPoint: "result-review",
          active: true,
          traceId: "trace-embed-host-e2e",
          integrationMode: "IFRAME",
          hook: "result-review",
          hookInstance: "hook-e2e-001",
          modelStatus: "MODEL_DISABLED",
          connectionStatus: "CONNECTED",
          cdsHookVersion: "1.0",
          parentOrigin: hostOrigin,
        },
      }),
    });
  });
  await page.route("**/medkernel/api/v1/engine/embed/recommendations", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          items: [
            {
              cardId: "card-embed-e2e",
              title: "检验危急值需人工确认",
              summary: "血钾结果达到危急值，需医师复核并留痕。",
              suggestedAction: "STRONG_REMINDER",
              riskLevel: "CRITICAL",
              interruptLevel: "SOFT",
              status: "PENDING",
              requiresPhysicianConfirmation: true,
              aiGenerated: false,
              sourceSummary: "检验危急值管理制度",
              traceId: "trace-card-embed-e2e",
            },
          ],
          traceId: "trace-embed-host-e2e",
        },
      }),
    });
  });
  await page.route("**/medkernel/api/v1/engine/embed/feedback", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          token: "host-e2e-token",
          cardId: "card-embed-e2e",
          actionType: "ADOPT",
          recommendationStatus: "ACCEPTED",
          callbackStatus: "NOT_CONNECTED",
          callbackDelivered: false,
          degradationReason: "HOST_CALLBACK_NOT_CONFIGURED",
          traceId: "trace-feedback-embed-e2e",
        },
      }),
    });
  });

  await page.goto(hostOrigin);
  await expect(page.getByRole("heading", { name: "院内业务系统工作站" })).toBeVisible();

  const embed = page.frameLocator('iframe[title="MedKernel 临床建议"]');
  await expect(embed.getByText("MedKernel 临床建议已连接")).toBeVisible();
  await expect(embed.getByText("检验危急值需人工确认")).toBeVisible();
  await embed.getByRole("button", { name: /采纳建议/ }).click();

  await expect(page.getByTestId("host-feedback")).toContainText("ADOPT");
  await expect(page.getByTestId("host-feedback")).toContainText("card-embed-e2e");
  await expect(page.getByTestId("host-feedback")).toContainText("trace-embed-host-e2e");
});
