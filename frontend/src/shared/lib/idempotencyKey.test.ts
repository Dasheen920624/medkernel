import { describe, expect, it } from "vitest";

import { buildStableIdempotencyKey } from "./idempotencyKey";

describe("buildStableIdempotencyKey", () => {
  it("keeps user-entered evidence text out of the backend 128 character limit", () => {
    const key = buildStableIdempotencyKey(
      "qc-alert-submit",
      "rct-finding-p1",
      "责任科室填写了很长的整改说明，需要包含复盘、病历补录、复核意见和真实前台证据。",
      "EMR-20260705-risk-assessment-with-extra-long-real-frontdesk-evidence-reference",
    );

    expect(key).toMatch(/^qc-alert-submit-rct-finding-p1-/);
    expect(key).toMatch(/[a-z0-9]{14}$/);
    expect(key.length).toBeLessThanOrEqual(128);
  });

  it("changes the stable digest when the request facts change", () => {
    const first = buildStableIdempotencyKey("qc-alert-review", "rct-1", "APPROVED", "证据充分");
    const second = buildStableIdempotencyKey("qc-alert-review", "rct-1", "RETURNED", "证据不足");

    expect(first).not.toBe(second);
    expect(second).toMatch(/^qc-alert-review-rct-1-/);
  });
});
