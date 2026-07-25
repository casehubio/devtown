import {
  page, rows, columns, gridTable, dataTable, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const operationsView = page("Operations",
  rows(
    // Top row — problems + system vitals side by side
    columns([85, 15],
      [rows(
        title("Problems", "h3"),
        gridTable({
          lookup: lookup("problems", groupBy("category",
            col("severity"), col("description"), col("actorId"), col("since")
          )),
          compact: true,
          stripe: "rows",
        }),
      )],
      [gridTable({
        lookup: lookup("system-health", groupBy(null,
          col("activeCases"), col("fleetSize"),
          col("openCommitments"), col("pendingWorkItems")
        )),
        transpose: true,
        compact: true,
      })],
    ),

    // Active Reviews — main content, full width
    title("Active Reviews", "h3"),
    dataTable({
      lookup: lookup("queue-status", groupBy("caseId",
        col("prNumber"), col("repo"), col("contributor"),
        col("status"), col("linesChanged"),
        col("startedAt"), col("lastEventAt")
      )),
      sortable: true,
      filter: { enabled: true },
    }),

    // Event Stream — recent history
    title("Event Stream", "h3"),
    dataTable({
      lookup: lookup("recent-events", groupBy("timestamp",
        col("timestamp"), col("caseId"), col("eventType"),
        col("actorId"), col("caseStatus")
      )),
      sortable: true,
      filter: { enabled: true },
    }),
  ),
);
