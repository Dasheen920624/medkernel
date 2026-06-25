import { describe, expect, it } from "vitest";

import {
  resolveEmbedAppBase,
  resolveEmbedOrigin,
} from "../../e2e/support/embedBaseUrl.mjs";

describe("embed business host base url", () => {
  it("keeps the deployed context path for iframe launch urls", () => {
    expect(resolveEmbedAppBase("https://193.112.107.134/medkernel")).toBe(
      "https://193.112.107.134/medkernel",
    );
    expect(resolveEmbedAppBase("https://193.112.107.134/medkernel/")).toBe(
      "https://193.112.107.134/medkernel",
    );
  });

  it("uses origin only for postMessage validation", () => {
    expect(resolveEmbedOrigin("https://193.112.107.134/medkernel")).toBe(
      "https://193.112.107.134",
    );
    expect(resolveEmbedOrigin("http://localhost:5173")).toBe("http://localhost:5173");
  });
});
