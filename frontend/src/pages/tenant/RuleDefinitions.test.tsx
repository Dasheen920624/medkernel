import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { describe, expect, it, vi } from "vitest";

import RuleDefinitions from "./RuleDefinitions";

vi.mock("@/shared/api/hooks", () => ({
  useRuleDefinitions: () => ({
    data: { items: [], total: 0 },
    isLoading: false,
    refetch: vi.fn(),
  }),
  useRuleDetail: () => ({
    data: null,
    isLoading: false,
    refetch: vi.fn(),
  }),
  useCreateRule: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useAddTestCase: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useSimulateRule: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  usePublishRule: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}));

function renderRuleDefinitions() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <RuleDefinitions />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("RuleDefinitions 三层规则编辑体验", () => {
  it("创建规则时提供 L1 模板、L2 条件树与 L3 DSL，并能从 L2 同步到 L3", async () => {
    const user = userEvent.setup();
    renderRuleDefinitions();

    await user.click(screen.getByRole("button", { name: /新建规则模板/ }));

    const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
    expect(within(dialog).getByRole("tab", { name: /L1 模板/ })).toBeInTheDocument();
    expect(within(dialog).getByRole("tab", { name: /L2 条件树/ })).toBeInTheDocument();
    expect(within(dialog).getByRole("tab", { name: /L3 DSL/ })).toBeInTheDocument();

    await user.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
    await user.clear(within(dialog).getByLabelText("上下文字段路径"));
    await user.type(within(dialog).getByLabelText("上下文字段路径"), "observations.0.value");
    await user.clear(within(dialog).getByLabelText("比较值"));
    await user.type(within(dialog).getByLabelText("比较值"), "6");
    await user.click(within(dialog).getByRole("button", { name: "同步到 DSL" }));

    await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
    const dslEditor = within(dialog).getByLabelText("规则 DSL JSON");
    expect((dslEditor as HTMLTextAreaElement).value).toContain('"fact": "observations.0.value"');
    expect((dslEditor as HTMLTextAreaElement).value).toContain('"value": 6');
  });
});
