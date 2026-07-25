import {
  page, rows, gridTable, dataTable, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const queueView = page("Merge Queue",
  rows(
    title("Merge Queue", "h2"),

    gridTable({
      lookup: lookup("merge-queue-metrics", groupBy(null,
        col("queueDepth"), col("activeBatches"),
        col("throughput24h"), col("failureRate"),
        col("oldestWaitMinutes"), col("avgWaitMinutes"),
        col("avgTrustScore")
      )),
      rowHeaders: true,
      compact: true,
    }),

    // Queued PRs table
    title("Queued PRs", "h3"),
    dataTable({
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
    dataTable({
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
