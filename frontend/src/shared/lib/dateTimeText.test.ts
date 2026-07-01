import { describe, expect, it } from "vitest";

import { clinicalDateTimeInputToIso, formatClinicalDateTimeInputValue } from "./dateTimeText";

describe("dateTimeText", () => {
  it("formats an instant as a Chinese院内时间 input value without browser locale drift", () => {
    expect(formatClinicalDateTimeInputValue("2026-06-08T00:00:00Z")).toBe("2026年06月08日 08:00");
  });

  it("converts an院内时间 input value back to a stable ISO instant", () => {
    expect(clinicalDateTimeInputToIso("2026年06月09日 08:00")).toBe("2026-06-09T00:00:00.000Z");
  });
});
