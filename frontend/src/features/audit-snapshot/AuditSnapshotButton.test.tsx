import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AuditSnapshotButton } from "./AuditSnapshotButton";

const auditState = vi.hoisted(() => ({
  mutate: vi.fn(),
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
    renderButton();

    fireEvent.click(screen.getByRole("button", { name: /审计快照/ }));

    expect(auditState.mutate).toHaveBeenCalledWith("page:/qc/dashboard", expect.any(Object));
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
