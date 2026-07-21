import { dataset } from "@casehubio/pages-ui";

export const datasets = [
  dataset("queue-status", "/api/governance/queue-status", { dataPath: "reviews" }),
  dataset("recent-events", "/api/governance/recent-events?limit=100"),
  dataset("system-health", "/api/governance/system-health", { expression: "[$]" }),
  dataset("problems", "/api/governance/problems", { dataPath: "items" }),
  dataset("reviewers", "/api/governance/reviewers", { dataPath: "items" }),
  dataset("merge-queue", "/api/governance/merge-queue", { dataPath: "queuedPrs" }),
  dataset("active-batches", "/api/governance/merge-queue", { dataPath: "activeBatches" }),
  dataset("merge-queue-metrics", "/api/governance/merge-queue/metrics", { expression: "[$]" }),
  dataset("triage", "/api/governance/triage", { dataPath: "items" }),
  dataset("case-definitions", "/api/v1/case-definitions"),
  dataset("plan-items", "/api/v1/cases/#{row.caseId}/plan-items"),
  dataset("goal-status", "/api/v1/cases/#{row.caseId}/goals"),
  dataset("case-context", "/api/v1/cases/#{row.caseId}/context"),
];
