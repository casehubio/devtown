import {
  page, rows, table, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

export const triageView = page("Human Triage",
  rows(
    title("Human Triage", 2),

    table({
      lookup: lookup("triage", groupBy("workItemId",
        col("prRef"),
        col("decisionType"),
        col("candidateGroup"),
        col("expiresAt"),
        col("escalationStage"),
        col("createdAt"),
        col("caseId")
      )),
      sortable: true,
      filter: { enabled: true },
    }),
  ),
);
