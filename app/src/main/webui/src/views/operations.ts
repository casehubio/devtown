import {
  page, rows, columns, metric, table, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const operationsView = page("Operations",
  rows(
    columns([30, 40, 30],
      // Left — Active Reviews + Merge Queue
      [rows(
        title("Active Reviews", 3),
        table({
          lookup: lookup("queue-status", groupBy("caseId",
            col("prNumber"), col("repo"), col("contributor"),
            col("status"), col("linesChanged"),
            col("startedAt"), col("lastEventAt")
          )),
          sortable: true,
          filter: { enabled: true },
        }),

        title("Merge Queue", 3),
        table({
          lookup: lookup("merge-queue", groupBy("number",
            col("number"), col("priorityLane"), col("author"),
            col("trustScore"), col("waitMinutes")
          )),
          sortable: true,
          filter: { enabled: true },
        }),
      )],

      // Center — metrics
      [rows(
        title("Workbench", 2),
        columns([50, 50],
          [metric({ title: "Active Cases", lookup: lookup("system-health", groupBy(null, col("activeCases"))), subtype: "plain-text" })],
          [metric({ title: "Pending Work Items", lookup: lookup("system-health", groupBy(null, col("pendingWorkItems"))), subtype: "plain-text" })],
        ),
        columns([50, 50],
          [metric({ title: "Fleet Size", lookup: lookup("system-health", groupBy(null, col("fleetSize"))), subtype: "plain-text" })],
          [metric({ title: "Open Commitments", lookup: lookup("system-health", groupBy(null, col("openCommitments"))), subtype: "plain-text" })],
        ),
      )],

      // Right — problems
      [rows(
        title("Problems", 3),
        table({
          lookup: lookup("problems", groupBy("category",
            col("category"), col("severity"), col("description"), col("since")
          )),
          sortable: true,
          filter: { enabled: true },
        }),
      )],
    ),

    // Bottom — Event Stream
    title("Event Stream", 3),
    table({
      lookup: lookup("recent-events", groupBy("timestamp",
        col("timestamp"), col("caseId"), col("eventType"),
        col("actorId"), col("caseStatus")
      )),
      sortable: true,
      filter: { enabled: true },
    }),
  ),
);
