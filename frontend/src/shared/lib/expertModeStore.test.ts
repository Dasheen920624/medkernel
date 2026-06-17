import { beforeEach, describe, expect, it } from "vitest";

import { useExpertModeStore } from "./expertModeStore";

describe("expertModeStore", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useExpertModeStore.setState({ enabled: false });
  });

  it("keeps expert mode disabled by default", () => {
    expect(useExpertModeStore.getState().enabled).toBe(false);
  });

  it("persists the unified expert mode preference", () => {
    useExpertModeStore.getState().setEnabled(true);

    expect(useExpertModeStore.getState().enabled).toBe(true);
    expect(window.localStorage.getItem("medkernel.expert-mode.enabled")).toBe("true");
  });
});
