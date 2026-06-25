import { expect, test, type Page } from "@playwright/test";

import { ensureReadySession } from "./support/auth";

test.describe("线2路径图编辑器真实验收", () => {
  test("桌面端完成连线、删除、拖拽与布局持久化", async ({ page }) => {
    await ensureReadySession(page, "engine-operator");
    await page.goto("/pathway/templates");

    const dialog = await openCreatePathwayDialog(page);
    await openNodeCanvas(dialog);
    await dialog.getByRole("button", { name: "添加节点" }).click();
    await dialog.getByRole("button", { name: "添加节点" }).click();

    const graph = dialog.getByLabel("路径图编辑器");
    const firstNode = graph.getByLabel("路径节点 N1", { exact: true });
    await expect(firstNode).toBeVisible();

    const graphBox = await graph.boundingBox();
    const nodeBox = await firstNode.boundingBox();
    expect(graphBox).not.toBeNull();
    expect(nodeBox).not.toBeNull();
    expect(nodeBox!.x).toBeGreaterThanOrEqual(graphBox!.x);
    expect(nodeBox!.y).toBeGreaterThanOrEqual(graphBox!.y);
    expect(nodeBox!.x + nodeBox!.width).toBeLessThanOrEqual(graphBox!.x + graphBox!.width);
    expect(nodeBox!.y + nodeBox!.height).toBeLessThanOrEqual(graphBox!.y + graphBox!.height);

    const sourceHandle = graph.locator('.react-flow__handle.source[data-nodeid="pathway-node-0"]');
    const targetHandle = graph.locator('.react-flow__handle.target[data-nodeid="pathway-node-1"]');
    await sourceHandle.dragTo(targetHandle);
    await expect(dialog.getByText("流转边 1", { exact: true })).toBeVisible();
    await expect(graph.locator(".react-flow__edge")).toHaveCount(1);

    const createdEdge = graph.getByRole("group", {
      name: "流转边 E1：N1 到 N2",
    });
    await expect(createdEdge).toBeVisible();
    await createdEdge.click();
    await page.keyboard.press("Delete");
    await expect(graph.locator(".react-flow__edge")).toHaveCount(0);
    await expect(dialog.getByText("流转边 1", { exact: true })).toHaveCount(0);

    const beforeDrag = await firstNode.boundingBox();
    expect(beforeDrag).not.toBeNull();
    await page.mouse.move(
      beforeDrag!.x + beforeDrag!.width / 2,
      beforeDrag!.y + beforeDrag!.height / 2,
    );
    await page.mouse.down();
    await page.mouse.move(
      beforeDrag!.x + beforeDrag!.width / 2 + 80,
      beforeDrag!.y + beforeDrag!.height / 2 + 48,
      { steps: 5 },
    );
    await page.mouse.up();

    await firstNode.press("Escape");
    await expect(dialog).toBeVisible();
    await dialog.getByRole("button", { name: "同步到技术配置" }).click();
    await enableExpertMode(dialog);
    await dialog.getByRole("tab", { name: "L3 技术配置" }).click();
    const dslValue = await dialog.getByLabel("路径配置文本").inputValue();
    const dsl = JSON.parse(dslValue) as {
      nodes: Array<{ config?: { authoringLayout?: { x: number; y: number } } }>;
    };
    expect(dsl.nodes[0].config?.authoringLayout).toEqual({
      x: expect.any(Number),
      y: expect.any(Number),
    });
    expect(dsl.nodes[0].config?.authoringLayout).not.toEqual({ x: 0, y: 0 });
    await expectNoRootOverflow(page);
  });

  test("390px 窄屏仍可阅读画布且页面不横向溢出", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await ensureReadySession(page, "engine-operator");
    await page.goto("/pathway/templates");

    const dialog = await openCreatePathwayDialog(page);
    await openNodeCanvas(dialog);
    await dialog.getByRole("button", { name: "添加节点" }).click();

    await expect(dialog.getByLabel("路径图编辑器")).toBeVisible();
    await expect(dialog.getByLabel("路径节点 N1", { exact: true })).toBeVisible();
    const toolbarTitleBox = await dialog.getByText("结构化节点画布", { exact: true }).boundingBox();
    const nodeCodeInputBox = await dialog.getByPlaceholder("如 N1，可改为 ASSESS").boundingBox();
    expect(toolbarTitleBox).not.toBeNull();
    expect(nodeCodeInputBox).not.toBeNull();
    expect(toolbarTitleBox!.width).toBeGreaterThan(120);
    expect(nodeCodeInputBox!.width).toBeGreaterThan(250);
    await expectNoRootOverflow(page);
  });

  test("关键时钟节点显示并填写临床时钟 SLA 字段", async ({ page }) => {
    await ensureReadySession(page, "engine-operator");
    await page.goto("/pathway/templates");

    const dialog = await openCreatePathwayDialog(page);
    await openNodeCanvas(dialog);
    await dialog.getByRole("button", { name: "添加节点" }).click();

    await dialog.getByLabel("节点编码").fill("CLOCK");
    await dialog.getByLabel("节点名称").fill("关键时钟");
    await dialog.getByLabel("时窗分钟").fill("60");

    await expect(dialog.getByLabel("SLA基准")).toBeVisible();
    await expect(dialog.getByLabel("最早分钟")).toBeVisible();
    await expect(dialog.getByLabel("目标分钟")).toBeVisible();
    await expect(dialog.getByLabel("最晚分钟")).toBeVisible();
    await expect(dialog.getByLabel("上报分钟")).toBeVisible();

    await dialog.getByLabel("目标分钟").fill("90");
    await dialog.getByLabel("最晚分钟").fill("120");
    await dialog.getByLabel("上报分钟").fill("105");

    await expect(dialog.getByLabel("目标分钟")).toHaveValue("90");
    await expect(dialog.getByLabel("最晚分钟")).toHaveValue("120");
    await expect(dialog.getByLabel("上报分钟")).toHaveValue("105");
    await expectNoRootOverflow(page);
  });
});

async function openCreatePathwayDialog(page: Page) {
  await page.getByRole("button", { name: "新建路径模板" }).click();
  const dialog = page.getByRole("dialog", { name: "新建路径模板模型" });
  await expect(dialog).toBeVisible();
  return dialog;
}

async function openNodeCanvas(dialog: ReturnType<Page["getByRole"]>) {
  await dialog.getByRole("tab", { name: "节点画布" }).click();
  await expect(dialog.getByText("结构化节点画布", { exact: true })).toBeVisible();
}

async function enableExpertMode(dialog: ReturnType<Page["getByRole"]>) {
  const expertSwitch = dialog.getByRole("switch", { name: "L3 技术配置模式" });
  if ((await expertSwitch.getAttribute("aria-checked")) !== "true") {
    await expertSwitch.click();
  }
  await expect(dialog.getByRole("tab", { name: "L3 技术配置" })).toBeVisible();
}

async function expectNoRootOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewportWidth);
}
