import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import StandardTermValueAutoComplete from "./StandardTermValueAutoComplete";

const apiMocks = vi.hoisted(() => ({
  terms: { items: [], total: 0 } as unknown,
  coverage: [] as unknown[],
}));

vi.mock("@/shared/api/hooks", () => ({
  useStandardTerms: () => ({ data: apiMocks.terms, isLoading: false }),
  useMappingCoverage: () => ({ data: apiMocks.coverage, isLoading: false }),
}));

function renderWith(value?: string) {
  return render(
    <ConfigProvider>
      <StandardTermValueAutoComplete codeSystem="ICD-10" value={value} onChange={() => {}} />
    </ConfigProvider>,
  );
}

describe("StandardTermValueAutoComplete 对照覆盖提示（P5）", () => {
  beforeEach(() => {
    apiMocks.terms = { items: [], total: 0 };
    apiMocks.coverage = [];
  });

  it("已覆盖编码不显示警示", () => {
    apiMocks.coverage = [{ code: "I48", status: "COVERED", mappedLocalCount: 2 }];
    renderWith("I48");
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("未对照编码显示院内对照警示", () => {
    apiMocks.coverage = [{ code: "I48", status: "UNMAPPED", mappedLocalCount: 0 }];
    renderWith("I48");
    expect(screen.getByRole("alert").textContent).toContain("尚无院内→标准对照");
  });

  it("不在标准字典内的编码显示对应警示", () => {
    apiMocks.coverage = [{ code: "ZZZ", status: "NO_STANDARD_TERM", mappedLocalCount: 0 }];
    renderWith("ZZZ");
    expect(screen.getByRole("alert").textContent).toContain("不在 ICD-10 标准字典内");
  });
});
