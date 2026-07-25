import {
  page, rows, columns, gridTable, dataTable, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const systemView = page("System",
  rows(
    title("System Health", "h2"),

    columns([50, 50],
      [gridTable({
        lookup: lookup("system-health", groupBy(null,
          col("activeCases"), col("fleetSize"),
          col("openCommitments"), col("pendingWorkItems")
        )),
        rowHeaders: true,
        compact: true,
      })],
      [gridTable({
        lookup: lookup("merge-queue-metrics", groupBy(null,
          col("queueDepth"), col("oldestWaitMinutes"),
          col("avgWaitMinutes"), col("failureRate")
        )),
        rowHeaders: true,
        compact: true,
      })],
    ),

    // Problems table
    title("Problems", "h3"),
    dataTable({
      lookup: lookup("problems", groupBy("category",
        col("category"), col("severity"), col("description"),
        col("caseId"), col("actorId"), col("since")
      )),
      sortable: true,
      filter: { enabled: true },
    }),
  ),
);
