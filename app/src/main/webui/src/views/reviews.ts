import {
  page, tabs, rows, gridTable, dataTable, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

// List page — all reviews with status
const reviewsList = rows(
  title("Reviews", "h2"),

  dataTable({
    lookup: lookup("queue-status", groupBy("caseId",
      col("prNumber"),
      col("repo"),
      col("contributor"),
      col("status"),
      col("linesChanged"),
      col("startedAt"),
      col("lastEventAt")
    )),
    sortable: true,
    filter: { enabled: true },
  }),
);

// Detail page — case-level breakdown (engine data from selected case)
const reviewDetail = rows(
  title("Review Detail", "h2"),

  // PR header
  gridTable({
    lookup: lookup("queue-status", groupBy(null,
      col("repo"), col("prNumber"), col("contributor"),
      col("linesChanged"), col("status")
    )),
    rowHeaders: true,
    compact: true,
  }),

  // Timeline
  title("Event Timeline", "h3"),
  dataTable({
    lookup: lookup("recent-events", groupBy("timestamp",
      col("timestamp"), col("eventType"), col("actorId"), col("caseStatus")
    )),
    sortable: true,
  }),

  // Plan Items (engine)
  title("Plan Items", "h3"),
  dataTable({
    lookup: lookup("plan-items", groupBy("planItemId",
      col("bindingName"), col("targetType"),
      col("status"), col("executorName"), col("createdAt"),
      col("activationContext"),
    )),
    sortable: true,
  }),

  // Case Context (engine)
  title("Case Context", "h3"),
  dataTable({
    lookup: lookup("case-context", groupBy("key", col("key"), col("value"))),
  }),

  // Goal Progress (engine)
  title("Goal Progress", "h3"),
  dataTable({
    lookup: lookup("goal-status", groupBy("name",
      col("name"), col("kind"), col("satisfied")
    )),
  }),
);

// View with tabs for list/detail navigation
export const reviewsView = page("Reviews",
  tabs(
    ["List", reviewsList],
    ["Detail", reviewDetail],
  ),
);
