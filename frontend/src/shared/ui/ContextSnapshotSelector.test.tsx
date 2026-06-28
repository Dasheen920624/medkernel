import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { ContextSnapshotSummary } from "@/shared/api/hooks";
import { ContextSnapshotSelector } from "./ContextSnapshotSelector";

const snapshots: ContextSnapshotSummary[] = [
  {
    snapshotId: "snapshot-secret-1",
    patientId: "patient-secret-1",
    encounterId: "encounter-secret-1",
    status: "ACTIVE",
    qualityStatus: "VALID",
  },
];

describe("ContextSnapshotSelector", () => {
  it("defaults to the business view without exposing patient or encounter identifiers", () => {
    render(
      <ContextSnapshotSelector
        enabled
        loading={false}
        error={false}
        snapshots={snapshots}
        selectedSnapshotId=""
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByText("已关联患者与就诊")).toBeInTheDocument();
    expect(screen.getByText("临床快照已生效")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "选择第 1 个临床快照" })).toBeInTheDocument();
    expect(screen.queryByText(/patient-secret-1/)).not.toBeInTheDocument();
    expect(screen.queryByText(/encounter-secret-1/)).not.toBeInTheDocument();
    expect(screen.queryByText(/snapshot-secret-1/)).not.toBeInTheDocument();
  });

  it("reveals raw snapshot evidence only when evidence details are enabled", () => {
    render(
      <ContextSnapshotSelector
        enabled
        loading={false}
        error={false}
        snapshots={snapshots}
        selectedSnapshotId=""
        onSelect={vi.fn()}
        evidenceDetailsEnabled
      />,
    );

    expect(screen.getByText("患者 patient-secret-1 · 就诊 encounter-secret-1")).toBeInTheDocument();
    expect(screen.getByText("快照 snapshot-secret-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "选择 snapshot-secret-1" })).toBeInTheDocument();
  });
});
