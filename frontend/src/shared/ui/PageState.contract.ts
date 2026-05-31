export const PAGE_STATE_KINDS = [
  "loading",
  "empty",
  "error",
  "forbidden",
  "partial",
  "ready",
] as const;

export type PageStateKind = (typeof PAGE_STATE_KINDS)[number];
export type NonReadyPageStateKind = Exclude<PageStateKind, "ready">;

export interface FailureDetail {
  key: string;
  reason: string;
  retryable?: boolean;
}
