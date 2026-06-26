import { describe, expect, it, vi } from "vitest";

import {
  apiFieldErrorsToFormFields,
  applyApiFieldErrors,
  getApiErrorMessage,
  parseApiError,
} from "./errors";

function problemError() {
  return {
    response: {
      data: {
        title: "请求参数校验失败",
        detail: "请修正表单字段后重试",
        code: "ENG-API-002",
        traceId: "trace-form-1",
        errors: [
          { field: "username", code: "NotBlank", message: "用户名不能为空" },
          { field: "profile.email", code: "Email", message: "邮箱格式不合法" },
        ],
      },
    },
  };
}

describe("api error helpers", () => {
  it("parses ProblemDetail into Chinese message, code, traceId and field errors", () => {
    expect(parseApiError(problemError(), "保存失败")).toEqual({
      message: "请修正表单字段后重试",
      code: "ENG-API-002",
      traceId: "trace-form-1",
      fieldErrors: [
        { field: "username", code: "NotBlank", message: "用户名不能为空" },
        { field: "profile.email", code: "Email", message: "邮箱格式不合法" },
      ],
    });

    expect(getApiErrorMessage(problemError(), "保存失败")).toBe(
      "请修正表单字段后重试（追踪号：trace-form-1）",
    );
  });

  it("maps service field errors to Ant Design Form fields", () => {
    expect(apiFieldErrorsToFormFields(problemError())).toEqual([
      { name: "username", errors: ["用户名不能为空"] },
      { name: ["profile", "email"], errors: ["邮箱格式不合法"] },
    ]);
  });

  it("supports field name mapping for pages whose form names differ from service fields", () => {
    expect(
      apiFieldErrorsToFormFields(problemError(), {
        fieldNameMap: (field) => (field === "username" ? "account" : undefined),
      }),
    ).toEqual([{ name: "account", errors: ["用户名不能为空"] }]);
  });

  it("applies field errors to a Form instance and reports whether anything was applied", () => {
    const form = { setFields: vi.fn() };

    expect(applyApiFieldErrors(form, problemError())).toBe(true);
    expect(form.setFields).toHaveBeenCalledWith([
      { name: "username", errors: ["用户名不能为空"] },
      { name: ["profile", "email"], errors: ["邮箱格式不合法"] },
    ]);

    expect(applyApiFieldErrors(form, new Error("网络断开"))).toBe(false);
  });

  it("falls back to Error.message or provided fallback when ProblemDetail is absent", () => {
    expect(getApiErrorMessage(new Error("网络断开"), "操作失败")).toBe("网络断开");
    expect(getApiErrorMessage(null, "操作失败")).toBe("操作失败");
  });

  it("does not expose raw endpoint or connection details from generic errors", () => {
    expect(
      getApiErrorMessage(
        new Error("GET /api/v1/authoring-batch failed: ECONNREFUSED 127.0.0.1:8080"),
        "批量任务提交失败",
      ),
    ).toBe("批量任务提交失败");
  });
});
