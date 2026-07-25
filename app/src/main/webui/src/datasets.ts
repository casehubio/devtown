import { bind, restSource } from "@casehubio/pages-ui";
import type { DataSetId } from "@casehubio/pages-data";

function rest(id: string, url: string, opts?: { dataPath?: string; expression?: string }) {
  return bind(id, restSource(url, id as DataSetId, opts));
}

export const datasets = [
  rest("queue-status", "/api/governance/queue-status", { dataPath: "reviews" }),
  rest("recent-events", "/api/governance/recent-events?limit=100"),
  rest("system-health", "/api/governance/system-health", { expression: "[$]" }),
  rest("problems", "/api/governance/problems?threshold_minutes=0", { dataPath: "items" }),
  rest("reviewers", "/api/governance/reviewers", { dataPath: "items" }),
  rest("merge-queue", "/api/governance/merge-queue", { dataPath: "queuedPrs" }),
  rest("active-batches", "/api/governance/merge-queue", { dataPath: "activeBatches" }),
  rest("merge-queue-metrics", "/api/governance/merge-queue/metrics", { expression: "[$]" }),
  rest("triage", "/api/governance/triage", { dataPath: "items" }),
  rest("case-definitions", "/api/v1/case-definitions"),
  rest("plan-items", "/api/v1/cases/#{row.caseId}/plan-items"),
  rest("goal-status", "/api/v1/cases/#{row.caseId}/goals"),
  rest("case-context", "/api/v1/cases/#{row.caseId}/context"),
];
