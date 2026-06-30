import { describe, expect, it } from "vitest";
import { resolveBrowserBasename } from "./browserBasename";

describe("resolveBrowserBasename", () => {
  it("uses /medkernel as the router basename when the app is opened from the on-premise prefix", () => {
    expect(resolveBrowserBasename("/medkernel")).toBe("/medkernel");
    expect(resolveBrowserBasename("/medkernel/cdss/fatigue")).toBe("/medkernel");
  });

  it("keeps root-hosted development and embedded pages on the default basename", () => {
    expect(resolveBrowserBasename("/dashboard")).toBeUndefined();
    expect(resolveBrowserBasename("/embed/launch")).toBeUndefined();
  });
});
