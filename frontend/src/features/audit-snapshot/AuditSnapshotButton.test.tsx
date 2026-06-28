import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AuditSnapshotButton } from "./AuditSnapshotButton";
import type * as Antd from "antd";

const auditState = vi.hoisted(() => ({
  mutate: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
  permissions: [
    {
      code: "audit.export",
      dimension: "ACTION",
      target: "audit",
      displayName: "导出审计",
      risk: "HIGH",
    },
  ],
}));

vi.mock("antd", async () => {
  const actual = await vi.importActual<typeof Antd>("antd");
  return {
    ...actual,
    message: {
      ...actual.message,
      success: auditState.messageSuccess,
      error: auditState.messageError,
    },
  };
});

vi.mock("@/shared/api/hooks", () => ({
  useAuditSnapshot: () => ({ mutate: auditState.mutate, isPending: false }),
  useSecurityProfile: () => ({
    data: { permissions: auditState.permissions },
  }),
}));

function renderButton() {
  return render(
    <MemoryRouter
      initialEntries={["/qc/dashboard"]}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <AuditSnapshotButton />
    </MemoryRouter>,
  );
}

describe("AuditSnapshotButton", () => {
  it("requests a persisted audit snapshot through the protected API", () => {
    auditState.permissions = [
      {
        code: "audit.export",
        dimension: "ACTION",
        target: "audit",
        displayName: "导出审计",
        risk: "HIGH",
      },
    ];
    auditState.mutate.mockClear();
    auditState.messageSuccess.mockClear();
    renderButton();

    fireEvent.click(screen.getByRole("button", { name: /审计快照/ }));

    expect(auditState.mutate).toHaveBeenCalledWith("page:/qc/dashboard", expect.any(Object));
  });

  it("confirms snapshot creation without exposing the technical signature in the toast", () => {
    auditState.permissions = [
      {
        code: "audit.export",
        dimension: "ACTION",
        target: "audit",
        displayName: "导出审计",
        risk: "HIGH",
      },
    ];
    auditState.messageSuccess.mockClear();
    auditState.mutate.mockImplementation((_scope, options) => {
      options.onSuccess({ id: "snapshot-raw-1", signature: "signature-raw-1" });
    });
    renderButton();

    fireEvent.click(screen.getByRole("button", { name: /审计快照/ }));

    expect(auditState.messageSuccess).toHaveBeenCalledWith(
      "审计快照已生成，可在审计证据中查看",
    );
    expect(auditState.messageSuccess).not.toHaveBeenCalledWith(
      expect.stringContaining("signature-raw-1"),
    );
    expect(auditState.messageSuccess).not.toHaveBeenCalledWith(
      expect.stringContaining("snapshot-raw-1"),
    );
  });

  it("fails closed when the current profile lacks audit export permission", () => {
    auditState.permissions = [
      {
        code: "audit.read",
        dimension: "ACTION",
        target: "audit",
        displayName: "查看审计",
        risk: "LOW",
      },
    ];
    renderButton();

    expect(screen.getByRole("button", { name: /审计快照/ })).toBeDisabled();
  });
});
