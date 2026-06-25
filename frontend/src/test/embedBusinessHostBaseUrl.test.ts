import { describe, expect, it } from "vitest";

import {
  resolveEmbedAppBase,
  resolveEmbedOrigin,
} from "../../e2e/support/embedBaseUrl.mjs";

describe("embed business host base url", () => {
  it("uses the frontend origin for iframe launch urls when the API context path is supplied", () => {
    expect(resolveEmbedAppBase("https://193.112.107.134/medkernel")).toBe(
      "https://193.112.107.134",
    );
    expect(resolveEmbedAppBase("https://193.112.107.134/medkernel/")).toBe(
      "https://193.112.107.134",
    );
  });

  it("uses origin only for postMessage validation", () => {
    expect(resolveEmbedOrigin("https://193.112.107.134/medkernel")).toBe(
      "https://193.112.107.134",
    );
    expect(resolveEmbedOrigin("http://localhost:5173")).toBe("http://localhost:5173");
  });
});
