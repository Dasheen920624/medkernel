import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { StatusBadge } from "./StatusBadge";
import { resolveStatusMeta, STATUS_MACHINES } from "./StatusBadge.contract";

describe("StatusBadge", () => {
  it("renders config 已发布 label", () => {
    render(<StatusBadge machine="config" status="published" />);
    expect(screen.getByText("已发布")).toBeInTheDocument();
  });

  it("renders alert 已派单 label", () => {
    render(<StatusBadge machine="alert" status="assigned" />);
    expect(screen.getByText("已派单")).toBeInTheDocument();
  });

  it("STATUS_MACHINES contains 4 machines", () => {
    expect(STATUS_MACHINES).toEqual({
      config: ["draft", "pending_review", "published", "active", "deprecated", "archived"],
      change: ["pending", "canary", "rolled_out", "rolled_back"],
      todo: ["unread", "in_progress", "done", "escalated"],
      alert: ["new", "assigned", "remediating", "closed", "waived"],
    });
  });

  it("rejects an unregistered status instead of inventing fallback copy", () => {
    expect(() =>
      resolveStatusMeta({
        machine: "config",
        // @ts-expect-error testing runtime defense for invalid API data
        status: "bogus",
      }),
    ).toThrow(/未注册状态机状态/);
  });
});
