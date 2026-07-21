import {
  page, rows, table, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const reviewersView = page("Reviewers",
  rows(
    title("Reviewer Fleet", 2),

    table({
      lookup: lookup("reviewers", groupBy("actorId",
        col("actorId"),
        col("maturityPhase"),
        col("openCommitments"),
        col("totalDecisions")
      )),
      sortable: true,
      filter: { enabled: true },
    }),
  ),
);
