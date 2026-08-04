import { bind, restSource } from "@casehubio/pages-ui";
import type { DataSetId } from "@casehubio/pages-data";

function rest(id: string, url: string, opts?: { dataPath?: string; expression?: string; refreshTime?: string }) {
  const binding = bind(id, restSource(url, id as DataSetId, opts));
  return opts?.refreshTime ? { ...binding, refreshTime: opts.refreshTime } : binding;
}

export function createDatasets(prefs: Record<string, string>) {
  const opRefresh = prefs["refresh.operational"] ?? undefined;
  const metRefresh = prefs["refresh.metrics"] ?? undefined;
  const caseRefresh = prefs["refresh.caseDetail"] ?? undefined;

  return [
    rest("queue-status", "/api/governance/queue-status", { dataPath: "reviews", refreshTime: opRefresh }),
    rest("problems", "/api/governance/problems?threshold_minutes=0", { dataPath: "items", refreshTime: opRefresh }),
    rest("merge-queue", "/api/governance/merge-queue", { dataPath: "queuedPrs", refreshTime: opRefresh }),
    rest("active-batches", "/api/governance/merge-queue", { dataPath: "activeBatches", refreshTime: opRefresh }),
    rest("triage", "/api/governance/triage", { dataPath: "items", refreshTime: opRefresh }),

    rest("system-health", "/api/governance/system-health", { expression: "[$]", refreshTime: metRefresh }),
    rest("merge-queue-metrics", "/api/governance/merge-queue/metrics", { expression: "[$]", refreshTime: metRefresh }),
    rest("reviewers", "/api/governance/reviewers", { dataPath: "items", refreshTime: metRefresh }),

    rest("recent-events", "/api/governance/recent-events?limit=100", { refreshTime: opRefresh }),

    rest("case-definitions", "/api/v1/case-definitions"),

    rest("plan-items", "/api/v1/cases/#{row.caseId}/plan-items", { refreshTime: caseRefresh }),
    rest("goal-status", "/api/v1/cases/#{row.caseId}/goals", { refreshTime: caseRefresh }),
    rest("case-context", "/api/v1/cases/#{row.caseId}/context", { refreshTime: caseRefresh }),
  ];
}
