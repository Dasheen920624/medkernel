import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { message } from "antd";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useApiMutation } from "./mutation";

vi.mock("antd", () => ({
  message: {
    error: vi.fn(),
  },
}));

function problemError(fieldErrors = true) {
  return {
    response: {
      data: {
        title: "请求参数校验失败",
        detail: "请修正表单字段后重试",
        code: "ENG-API-002",
        traceId: "trace-mutation-1",
        errors: fieldErrors
          ? [{ field: "username", code: "NotBlank", message: "用户名不能为空" }]
          : [],
      },
    },
  };
}

function renderWithClient(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("useApiMutation", () => {
  it("applies service field errors to form fields instead of showing only a toast", async () => {
    const setFields = vi.fn();

    function Subject() {
      const mutation = useApiMutation({
        mutationFn: async () => {
          throw problemError();
        },
        feedback: {
          form: { setFields },
          fallbackErrorMessage: "保存失败",
        },
      });
      return <button onClick={() => mutation.mutate(undefined)}>提交</button>;
    }

    renderWithClient(<Subject />);
    fireEvent.click(screen.getByRole("button", { name: "提交" }));

    await waitFor(() => {
      expect(setFields).toHaveBeenCalledWith([{ name: "username", errors: ["用户名不能为空"] }]);
    });
    expect(message.error).not.toHaveBeenCalled();
  });

  it("shows a unified Chinese toast with business evidence text for non-field errors and preserves custom onError", async () => {
    const onError = vi.fn();

    function Subject() {
      const mutation = useApiMutation({
        mutationFn: async () => {
          throw problemError(false);
        },
        feedback: {
          fallbackErrorMessage: "保存失败",
        },
        onError,
      });
      return <button onClick={() => mutation.mutate(undefined)}>提交</button>;
    }

    renderWithClient(<Subject />);
    fireEvent.click(screen.getByRole("button", { name: "提交" }));

    await waitFor(() => {
      expect(message.error).toHaveBeenCalledWith(
        "请修正表单字段后重试（失败已留痕，可在审计证据中追溯）",
      );
    });
    expect(message.error).not.toHaveBeenCalledWith(expect.stringContaining("trace-mutation-1"));
    expect(onError).toHaveBeenCalledTimes(1);
  });
});
