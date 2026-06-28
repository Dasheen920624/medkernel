import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "@/shared/api/client";
import { AuthoringReadablePreview } from "./AuthoringReadablePreview";

vi.mock("@/shared/api/client", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

function wrapper() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  client.setQueryData(["security", "me"], {
    userId: "author-1",
    username: "规则作者",
    roles: [
      {
        code: "engine-operator",
        displayName: "专科医生",
        source: "tenant",
        scopeLevel: "TENANT",
        scopeCode: null,
      },
    ],
    permissions: [],
    menuKeys: [],
    environmentKeys: [],
    dataScope: {
      tenantId: "tenant-A",
      groupId: null,
      hospitalId: null,
      campusId: null,
      siteId: null,
      departmentId: null,
      specialtyId: null,
    },
    mustChangePwd: false,
    mfaRequired: false,
    mfaBound: true,
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

describe("AuthoringReadablePreview", () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset();
  });

  it("renders readable text from the unified authoring preview API", async () => {
    const dsl = { when: { all: [{ fact: "patient.age", operator: "gte", value: 65 }] } };
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          previewText: "当 年龄 大于等于 65。",
          lines: ["当 年龄 大于等于 65"],
          segments: [{ kind: "condition", path: "$.when.all[0]", text: "年龄 大于等于 65" }],
          warnings: ["$.when.all[0] 使用兜底字段标签"],
          traceId: "trace-preview",
        },
      },
    });

    render(<AuthoringReadablePreview subject="RULE_CONDITION" dsl={dsl} />, { wrapper: wrapper() });

    expect(await screen.findByText("当 年龄 大于等于 65。")).toBeInTheDocument();
    expect(screen.getByText("可读预览")).toBeInTheDocument();
    expect(screen.getByText("预览证据已记录")).toBeInTheDocument();
    expect(screen.queryByText(/trace-preview/)).not.toBeInTheDocument();
    expect(screen.queryByText(/追踪号/)).not.toBeInTheDocument();
    expect(screen.getByText("部分预览内容使用通用字段标签，请核对字段含义。")).toBeInTheDocument();
    expect(screen.queryByText(/\$\.when/)).not.toBeInTheDocument();
    await waitFor(() =>
      expect(apiClient.post).toHaveBeenCalledWith(
        "/engine/authoring/preview",
        expect.objectContaining({
          subject: "RULE_CONDITION",
          dsl,
          tenant_id: "tenant-A",
          user_id: "author-1",
          role_codes: ["engine-operator"],
        }),
      ),
    );
    expect(vi.mocked(apiClient.post).mock.calls[0]?.[1]).not.toHaveProperty("package_version");
  });
});
