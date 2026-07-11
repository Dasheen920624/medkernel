const CLINICAL_TIME_ZONE = "Asia/Shanghai";
const CLINICAL_TIME_ZONE_OFFSET_MINUTES = 8 * 60;

const clinicalDateFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: CLINICAL_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const clinicalDateTimeFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: CLINICAL_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

const clinicalDateTimeSecondFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: CLINICAL_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

function parseDate(value?: string | null) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatterParts(formatter: Intl.DateTimeFormat, date: Date) {
  const parts: Record<string, string> = {};
  for (const part of formatter.formatToParts(date)) {
    if (part.type !== "literal") parts[part.type] = part.value;
  }
  return parts;
}

export function formatClinicalDate(value?: string | null, fallback = "日期待确认") {
  const date = parseDate(value);
  if (!date) return fallback;
  const parts = formatterParts(clinicalDateFormatter, date);
  return `${parts.year}年${parts.month}月${parts.day}日`;
}

export function formatClinicalDateTime(value?: string | null, fallback = "未记录") {
  const date = parseDate(value);
  if (!date) return fallback;
  const parts = formatterParts(clinicalDateTimeFormatter, date);
  return `${parts.year}年${parts.month}月${parts.day}日 ${parts.hour}:${parts.minute}`;
}

export function formatClinicalDateTimeWithSeconds(value?: string | null, fallback = "未记录") {
  const date = parseDate(value);
  if (!date) return fallback;
  const parts = formatterParts(clinicalDateTimeSecondFormatter, date);
  return `${parts.year}年${parts.month}月${parts.day}日 ${parts.hour}:${parts.minute}:${parts.second}`;
}

export function formatClinicalDateTimeInputValue(value?: string | null) {
  return formatClinicalDateTime(value, "");
}

export function clinicalDateTimeInputToIso(value?: string | null) {
  const trimmed = value?.trim();
  if (!trimmed) return "";
  const match =
    /^(\d{4})年(\d{2})月(\d{2})日\s+(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(trimmed) ??
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(trimmed);
  if (!match) return trimmed;
  const [, year, month, day, hour, minute, second = "00"] = match;
  return clinicalDateTimePartsToIso(year, month, day, hour, minute, second) ?? trimmed;
}

export function isClinicalDateTimeInputValue(value?: string | null) {
  const trimmed = value?.trim();
  if (!trimmed) return false;
  const match = /^(\d{4})年(\d{2})月(\d{2})日\s+(\d{2}):(\d{2})$/.exec(trimmed);
  if (!match) return false;
  const [, year, month, day, hour, minute] = match;
  return clinicalDateTimePartsToIso(year, month, day, hour, minute, "00") !== null;
}

function clinicalDateTimePartsToIso(
  yearText: string,
  monthText: string,
  dayText: string,
  hourText: string,
  minuteText: string,
  secondText: string,
) {
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  if (
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > 31 ||
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59 ||
    second < 0 ||
    second > 59
  ) {
    return null;
  }
  const utcTime =
    Date.UTC(year, month - 1, day, hour, minute, second) -
    CLINICAL_TIME_ZONE_OFFSET_MINUTES * 60_000;
  const clinicalTime = new Date(utcTime + CLINICAL_TIME_ZONE_OFFSET_MINUTES * 60_000);
  if (
    clinicalTime.getUTCFullYear() !== year ||
    clinicalTime.getUTCMonth() + 1 !== month ||
    clinicalTime.getUTCDate() !== day ||
    clinicalTime.getUTCHours() !== hour ||
    clinicalTime.getUTCMinutes() !== minute ||
    clinicalTime.getUTCSeconds() !== second
  ) {
    return null;
  }
  return new Date(utcTime).toISOString();
}
