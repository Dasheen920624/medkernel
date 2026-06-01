import type { AxiosAdapter } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "./client";

const originalAdapter = apiClient.defaults.adapter;

afterEach(() => {
  apiClient.defaults.adapter = originalAdapter;
  vi.restoreAllMocks();
});

describe("apiClient", () => {
  it("uses double-submit CSRF cookie and header names", () => {
    expect(apiClient.defaults.xsrfCookieName).toBe("XSRF-TOKEN");
    expect(apiClient.defaults.xsrfHeaderName).toBe("X-XSRF-TOKEN");
    expect(apiClient.defaults.withCredentials).toBe(true);
  });

  it("does not read token from localStorage when preparing requests", async () => {
    const getItem = vi.spyOn(window.localStorage.__proto__, "getItem").mockImplementation(() => {
      throw new Error("localStorage token read is forbidden");
    });
    apiClient.defaults.adapter = (async (config) => ({
      data: { ok: true },
      status: 200,
      statusText: "OK",
      headers: {},
      config,
    })) as AxiosAdapter;

    await expect(apiClient.get("/ping")).resolves.toMatchObject({ status: 200 });
    expect(getItem).not.toHaveBeenCalled();
  });

  it("dispatches an auth-required event for 401 without writing to console", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const listener = vi.fn();
    window.addEventListener("medkernel:auth-required", listener);
    apiClient.defaults.adapter = (async (config) =>
      Promise.reject({
        response: { status: 401 },
        config,
      })) as AxiosAdapter;

    await expect(apiClient.get("/secure")).rejects.toMatchObject({ response: { status: 401 } });

    expect(listener).toHaveBeenCalledTimes(1);
    expect(warn).not.toHaveBeenCalled();
    window.removeEventListener("medkernel:auth-required", listener);
  });
});
