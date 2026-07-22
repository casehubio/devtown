import {
  page, rows, metricGrid, metric, table, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const queueView = page("Merge Queue",
  rows(
    title("Merge Queue", "h2"),

    // Metrics row
    metricGrid(
      metric({ title: "Queue Depth", lookup: lookup("merge-queue-metrics", groupBy(null, col("queueDepth"))), subtype: "plain-text" }),
      metric({ title: "Active Batches", lookup: lookup("merge-queue-metrics", groupBy(null, col("activeBatches"))), subtype: "plain-text" }),
      metric({ title: "24h Throughput", lookup: lookup("merge-queue-metrics", groupBy(null, col("throughput24h"))), subtype: "plain-text" }),
      metric({ title: "Failure Rate", lookup: lookup("merge-queue-metrics", groupBy(null, col("failureRate"))), subtype: "plain-text" }),
    ),

    // Wait time metrics
    title("Wait Times", "h3"),
    metricGrid(
      metric({ title: "Oldest Wait (min)", lookup: lookup("merge-queue-metrics", groupBy(null, col("oldestWaitMinutes"))), subtype: "plain-text" }),
      metric({ title: "Avg Wait (min)", lookup: lookup("merge-queue-metrics", groupBy(null, col("avgWaitMinutes"))), subtype: "plain-text" }),
      metric({ title: "Avg Trust Score", lookup: lookup("merge-queue-metrics", groupBy(null, col("avgTrustScore"))), subtype: "plain-text" }),
    ),

    // Queued PRs table
    title("Queued PRs", "h3"),
    table({
      lookup: lookup("merge-queue", groupBy("number",
        col("number"),
        col("repository"),
        col("author"),
        col("priorityLane"),
        col("trustScore"),
        col("waitMinutes"),
        col("dependsOn")
      )),
      sortable: true,
      filter: { enabled: true },
    }),

    // Active Batches table
    title("Active Batches", "h3"),
    table({
      lookup: lookup("active-batches", groupBy("batchId",
        col("batchId"),
        col("caseId"),
        col("prCount"),
        col("riskLevel")
      )),
      sortable: true,
      filter: { enabled: true },
    }),
  ),
);
