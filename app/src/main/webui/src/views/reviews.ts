import {
  page, tabs, rows, columns, metric, table, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

// List page — all reviews with status
const reviewsList = rows(
  title("Reviews", 2),

  table({
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
  title("Review Detail", 2),

  // PR header metrics (from queue-status row)
  columns([20, 20, 20, 20, 20],
    [metric({ title: "Repository", lookup: lookup("queue-status", groupBy(null, col("repo"))), subtype: "plain-text" })],
    [metric({ title: "PR Number", lookup: lookup("queue-status", groupBy(null, col("prNumber"))), subtype: "plain-text" })],
    [metric({ title: "Contributor", lookup: lookup("queue-status", groupBy(null, col("contributor"))), subtype: "plain-text" })],
    [metric({ title: "Lines Changed", lookup: lookup("queue-status", groupBy(null, col("linesChanged"))), subtype: "plain-text" })],
    [metric({ title: "Status", lookup: lookup("queue-status", groupBy(null, col("status"))), subtype: "plain-text" })],
  ),

  // Timeline
  title("Event Timeline", 3),
  table({
    lookup: lookup("recent-events", groupBy("timestamp",
      col("timestamp"), col("eventType"), col("actorId"), col("caseStatus")
    )),
    sortable: true,
  }),

  // Plan Items (engine)
  title("Plan Items", 3),
  table({
    lookup: lookup("plan-items", groupBy("planItemId",
      col("bindingName"), col("targetType"),
      col("status"), col("executorName"), col("createdAt")
    )),
    sortable: true,
  }),

  // Case Context (engine)
  title("Case Context", 3),
  table({
    lookup: lookup("case-context", groupBy("key", col("key"), col("value"))),
  }),

  // Goal Progress (engine)
  title("Goal Progress", 3),
  table({
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
