const CLINICAL_TIME_ZONE = "Asia/Shanghai";

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
