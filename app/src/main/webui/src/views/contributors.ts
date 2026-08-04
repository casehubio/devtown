import {
  page, rows, dataTable, title, hostPanel,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const contributorsView = page("Contributors",
  rows(
    title("Contributor Fleet", "h2"),

    dataTable({
      lookup: lookup("contributors", groupBy("actorId",
        col("actorId"),
        col("trustScore"),
        col("intakeLane"),
        col("observationCount"),
        col("mergeRate"),
        col("firstAttemptQuality")
      )),
      sortable: true,
      filter: { enabled: true },
    }),

    hostPanel("contributor-workbench", {
      endpoint: "/api/governance",
      "actor-id": "#{row.actorId}",
    }),
  ),
);
