import {
  page, metric, metricGrid, rows, table, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const systemView = page("System",
  rows(
    title("System Health", "h2"),

    // Health metrics
    metricGrid(
      metric({ title: "Active Cases", lookup: lookup("system-health", groupBy(null, col("activeCases"))), subtype: "plain-text" }),
      metric({ title: "Fleet Size", lookup: lookup("system-health", groupBy(null, col("fleetSize"))), subtype: "plain-text" }),
      metric({ title: "Open Commitments", lookup: lookup("system-health", groupBy(null, col("openCommitments"))), subtype: "plain-text" }),
      metric({ title: "Pending Work Items", lookup: lookup("system-health", groupBy(null, col("pendingWorkItems"))), subtype: "plain-text" }),
    ),

    // Problems table
    title("Problems", "h3"),
    table({
      lookup: lookup("problems", groupBy("category",
        col("category"), col("severity"), col("description"),
        col("caseId"), col("actorId"), col("since")
      )),
      sortable: true,
      filter: { enabled: true },
    }),

    // Queue health
    title("Queue Health", "h3"),
    metricGrid(
      metric({ title: "Queue Depth", lookup: lookup("merge-queue-metrics", groupBy(null, col("queueDepth"))), subtype: "plain-text" }),
      metric({ title: "Oldest Wait (min)", lookup: lookup("merge-queue-metrics", groupBy(null, col("oldestWaitMinutes"))), subtype: "plain-text" }),
      metric({ title: "Avg Wait (min)", lookup: lookup("merge-queue-metrics", groupBy(null, col("avgWaitMinutes"))), subtype: "plain-text" }),
      metric({ title: "Failure Rate", lookup: lookup("merge-queue-metrics", groupBy(null, col("failureRate"))), subtype: "plain-text" }),
    ),
  ),
);
