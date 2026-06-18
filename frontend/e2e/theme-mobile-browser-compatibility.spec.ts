import { expect, test, type Page } from "@playwright/test";

const THEME_LABELS = ["默认", "老年医生", "暗黑", "护眼", "跟随系统"] as const;
const MINIMUM_ELDER_BODY_PX = 16 * (96 / 72);

test.describe("T8.6 主题、移动端与浏览器兼容验收", () => {
  test.beforeEach(async ({ page }) => {
    await stubLoginPageApis(page);
    await page.addInitScript(() => {
      window.localStorage.removeItem("medkernel.theme.mode");
    });
  });

  test("五主题均可达且老年医生字号达到 16pt", async ({ page }) => {
    const browserErrors = collectBrowserErrors(page);
    await page.goto("/login");
    await expect(page.getByRole("main", { name: "登录 MedKernel 工作台" })).toBeVisible();

    for (const label of THEME_LABELS) {
      await page.getByRole("button", { name: /^主题模式：/ }).click();
      await page.getByRole("menuitem", { name: label, exact: true }).click();
      await expect(page.getByRole("button", { name: `主题模式：${label}` })).toBeVisible();
    }

    await page.getByRole("button", { name: /^主题模式：/ }).click();
    await page.getByRole("menuitem", { name: "老年医生", exact: true }).click();

    const usernameInput = page.getByLabel("工号 / 账号");
    await expect
      .poll(() =>
        usernameInput.evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize)),
      )
      .toBeGreaterThanOrEqual(MINIMUM_ELDER_BODY_PX);

    const tokenSizes = await page.evaluate(() => {
      const candidates = [
        document.documentElement,
        document.body,
        ...Array.from(document.querySelectorAll<HTMLElement>("[class*='css-var']")),
      ];
      const tokenValue = (name: string) =>
        candidates
          .map((element) => getComputedStyle(element).getPropertyValue(name).trim())
          .find(Boolean) ?? "";

      return {
        fontSizeToken: Number.parseFloat(tokenValue("--ant-font-size")),
        smallFontSizeToken: Number.parseFloat(tokenValue("--ant-font-size-sm")),
      };
    });
    const mainControlFont = await page
      .getByRole("main", { name: "登录 MedKernel 工作台" })
      .evaluate((element) =>
        Number.parseFloat(
          getComputedStyle(element).getPropertyValue("--mk-login-control-font"),
        ),
      );
    const usernameInputFont = await usernameInput.evaluate((element) =>
      Number.parseFloat(getComputedStyle(element).fontSize),
    );

    expect(tokenSizes.fontSizeToken).toBeGreaterThanOrEqual(MINIMUM_ELDER_BODY_PX);
    expect(tokenSizes.smallFontSizeToken).toBeGreaterThanOrEqual(20);
    expect(mainControlFont).toBeGreaterThanOrEqual(MINIMUM_ELDER_BODY_PX);
    expect(usernameInputFont).toBeGreaterThanOrEqual(MINIMUM_ELDER_BODY_PX);
    expect(browserErrors).toEqual([]);
  });

  test("390px 登录页无根节点横向溢出且无浏览器错误", async ({ page }) => {
    const browserErrors = collectBrowserErrors(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/login");
    await expect(page.getByRole("main", { name: "登录 MedKernel 工作台" })).toBeVisible();

    const dimensions = await page.evaluate(() => ({
      viewportWidth: document.documentElement.clientWidth,
      documentWidth: document.documentElement.scrollWidth,
    }));

    expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewportWidth);
    expect(browserErrors).toEqual([]);
  });
});

function collectBrowserErrors(page: Page) {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      errors.push(message.text());
    }
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

async function stubLoginPageApis(page: Page) {
  await page.route("**/medkernel/api/v1/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname.endsWith("/bootstrap/status")) {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ data: { initialized: true } }),
      });
      return;
    }
    if (pathname.endsWith("/auth/login-tenants")) {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            primaryTenants: [
              { tenantId: "t-hospital", name: "示例医院", kind: "CUSTOMER" },
            ],
            platformTenant: {
              tenantId: "t-platform",
              name: "平台治理空间",
              kind: "PLATFORM",
            },
            hasCustomerTenants: true,
          },
        }),
      });
      return;
    }
    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: "E2E 未声明该接口" }),
    });
  });
}
