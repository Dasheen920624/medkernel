import { beforeEach, describe, expect, it } from "vitest";

import { useEvidenceDetailsStore } from "./evidenceDetailsStore";

describe("evidenceDetailsStore", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
  });

  it("keeps evidence details disabled by default", () => {
    expect(useEvidenceDetailsStore.getState().enabled).toBe(false);
  });

  it("persists the unified evidence details preference", () => {
    useEvidenceDetailsStore.getState().setEnabled(true);

    expect(useEvidenceDetailsStore.getState().enabled).toBe(true);
    expect(window.localStorage.getItem("medkernel.evidence-details.enabled")).toBe("true");
  });
});
