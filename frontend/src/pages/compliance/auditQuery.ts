import type { AuditEventListQuery } from "@/shared/api/hooks";
import type { ExperienceFilterValue } from "@/shared/ui/experienceTypes";

function stringFilter(filters: readonly ExperienceFilterValue[], key: string) {
  const value = filters.find((filter) => filter.key === key)?.value;
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function dateRangeFilter(filters: readonly ExperienceFilterValue[]) {
  const value = filters.find((filter) => filter.key === "occurredAt")?.value;
  return Array.isArray(value) && value.length === 2 ? value : undefined;
}

function startOfLocalDayIso(date: string) {
  return new Date(`${date}T00:00:00`).toISOString();
}

function nextLocalDayIso(date: string) {
  const next = new Date(`${date}T00:00:00`);
  next.setDate(next.getDate() + 1);
  return next.toISOString();
}

export function buildAuditEventQuery(
  filters: readonly ExperienceFilterValue[],
): Omit<AuditEventListQuery, "cursor" | "size" | "sort"> {
  const query: Omit<AuditEventListQuery, "cursor" | "size" | "sort"> = {};
  const dateRange = dateRangeFilter(filters);
  const action = stringFilter(filters, "action");
  const actorUserId = stringFilter(filters, "actorUserId");
  const resourceType = stringFilter(filters, "resourceType");
  const outcome = stringFilter(filters, "outcome");
  const traceId = stringFilter(filters, "traceId");

  if (action) query.action = action;
  if (actorUserId) query.actorUserId = actorUserId;
  if (resourceType) query.resourceType = resourceType;
  if (outcome) query.outcome = outcome;
  if (traceId) query.traceId = traceId;
  if (dateRange) {
    query.from = startOfLocalDayIso(dateRange[0]);
    query.to = nextLocalDayIso(dateRange[1]);
  }
  return query;
}
