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
          col("activeCases", "Active Cases"), col("fleetSize", "Fleet Size"),
          col("openCommitments", "Open Commitments"), col("pendingWorkItems", "Pending Work Items")
        )),
        rowHeaders: true,
        compact: true,
      })],
      [gridTable({
        lookup: lookup("merge-queue-metrics", groupBy(null,
          col("queueDepth", "Queue Depth"), col("oldestWaitMinutes", "Oldest Wait (min)"),
          col("avgWaitMinutes", "Avg Wait (min)"), col("failureRate", "Failure Rate")
        )),
        rowHeaders: true,
        compact: true,
      })],
    ),

    // Problems table
    title("Problems", "h3"),
    dataTable({
      lookup: lookup("problems", groupBy("category",
        col("category", "Category"), col("severity", "Severity"), col("description", "Description"),
        col("caseId", "Case ID"), col("actorId", "Actor"), col("since", "Since")
      )),
      sortable: true,
      filter: { enabled: true },
    }),

    // SLA Calibration — estimated vs configured comparison
    title("SLA Calibration", "h3"),
    dataTable({
      lookup: lookup("sla-comparison", groupBy(null,
        col("capability", "Capability"),
        col("configuredSeconds", "Configured (s)"),
        col("estimatedMedianSeconds", "Median (s)"),
        col("estimatedMinSeconds", "Min (s)"),
        col("estimatedMaxSeconds", "Max (s)"),
        col("sampleCount", "Samples"),
        col("deviationPercent", "Deviation %"),
      )),
      sortable: true,
    }),
  ),
);
