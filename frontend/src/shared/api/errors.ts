import { customerSafeDisplayText } from "@/shared/config/customerLabels";

const ERROR_EVIDENCE_RECORDED_TEXT = "失败已留痕，可在审计证据中追溯";

export type ApiFieldName = string | number | Array<string | number>;

export interface ApiProblemFieldError {
  field: string;
  code?: string;
  message?: string;
}

export interface ParsedApiError {
  message: string;
  code?: string;
  traceId?: string;
  fieldErrors: ApiProblemFieldError[];
}

export interface ApiFormLike {
  setFields: unknown;
}

export interface ApiFieldErrorOptions {
  fieldNameMap?: (field: string) => ApiFieldName | undefined;
}

type ProblemDetailLike = {
  title?: unknown;
  detail?: unknown;
  message?: unknown;
  code?: unknown;
  traceId?: unknown;
  errors?: unknown;
};

type ApiErrorLike = {
  response?: {
    data?: unknown;
    headers?: Record<string, unknown>;
  };
  message?: unknown;
};

export function parseApiError(error: unknown, fallback: string): ParsedApiError {
  const problem = extractProblemDetail(error);
  const fieldErrors = normalizeFieldErrors(problem?.errors);
  const message =
    cleanText(problem?.detail) ??
    cleanText(problem?.message) ??
    cleanText(problem?.title) ??
    cleanText((error as ApiErrorLike | null)?.message) ??
    fallback;

  return {
    message,
    code: cleanText(problem?.code),
    traceId: cleanText(problem?.traceId) ?? extractHeaderTraceId(error),
    fieldErrors,
  };
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  const parsed = parseApiError(error, fallback);
  const rawMessage = parsed.traceId
    ? removeTraceIdentifier(parsed.message, parsed.traceId)
    : parsed.message;
  const message = customerSafeDisplayText(rawMessage, fallback);
  if (!parsed.traceId || message.includes(ERROR_EVIDENCE_RECORDED_TEXT)) {
    return message;
  }
  return `${message}（${ERROR_EVIDENCE_RECORDED_TEXT}）`;
}

export function apiFieldErrorsToFormFields(
  error: unknown,
  options: ApiFieldErrorOptions = {},
): Array<{ name: ApiFieldName; errors: string[] }> {
  const parsed = parseApiError(error, "字段校验失败");
  return parsed.fieldErrors
    .map((fieldError) => {
      const name = toFormFieldName(fieldError.field, options);
      if (name === undefined) return null;
      return {
        name,
        errors: [fieldError.message ?? fieldError.code ?? parsed.message],
      };
    })
    .filter((field): field is { name: ApiFieldName; errors: string[] } => field !== null);
}

export function applyApiFieldErrors(
  form: ApiFormLike,
  error: unknown,
  options: ApiFieldErrorOptions = {},
): boolean {
  const fields = apiFieldErrorsToFormFields(error, options);
  if (fields.length === 0) {
    return false;
  }
  if (typeof form.setFields !== "function") {
    return false;
  }
  form.setFields(fields);
  return true;
}

function extractProblemDetail(error: unknown): ProblemDetailLike | null {
  if (!isObject(error)) return null;
  const data = (error as ApiErrorLike).response?.data;
  if (!isObject(data)) return null;
  return data as ProblemDetailLike;
}

function normalizeFieldErrors(errors: unknown): ApiProblemFieldError[] {
  if (!Array.isArray(errors)) return [];
  const fieldErrors: ApiProblemFieldError[] = [];
  for (const entry of errors) {
    if (!isObject(entry)) continue;
    const field = cleanText(entry.field);
    if (!field) continue;
    fieldErrors.push({
      field,
      code: cleanText(entry.code),
      message: cleanText(entry.message),
    });
  }
  return fieldErrors;
}

function toFormFieldName(field: string, options: ApiFieldErrorOptions): ApiFieldName | undefined {
  if (options.fieldNameMap) {
    return options.fieldNameMap(field);
  }
  const segments = field
    .split(".")
    .map((segment) => segment.trim())
    .filter(Boolean);
  return segments.length <= 1 ? field : segments;
}

function extractHeaderTraceId(error: unknown): string | undefined {
  if (!isObject(error)) return undefined;
  const headers = (error as ApiErrorLike).response?.headers;
  if (!headers) return undefined;
  return cleanText(headers["x-trace-id"]) ?? cleanText(headers["X-Trace-Id"]);
}

function removeTraceIdentifier(message: string, traceId: string): string {
  const escapedTraceId = traceId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return message
    .replace(new RegExp(`(?:追踪号|traceId|Trace ID)?\\s*[:：]?\\s*${escapedTraceId}`, "g"), "")
    .replace(/\s{2,}/g, " ")
    .replace(/[，,；;：:\\s]+$/, "")
    .trim();
}

function cleanText(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : undefined;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
