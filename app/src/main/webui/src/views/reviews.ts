import {
  page, tabs, rows, metricGrid, metric, table, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

// List page — all reviews with status
const reviewsList = rows(
  title("Reviews", "h2"),

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
  title("Review Detail", "h2"),

  // PR header metrics (from queue-status row)
  metricGrid(
    metric({ title: "Repository", lookup: lookup("queue-status", groupBy(null, col("repo"))), subtype: "plain-text" }),
    metric({ title: "PR Number", lookup: lookup("queue-status", groupBy(null, col("prNumber"))), subtype: "plain-text" }),
    metric({ title: "Contributor", lookup: lookup("queue-status", groupBy(null, col("contributor"))), subtype: "plain-text" }),
    metric({ title: "Lines Changed", lookup: lookup("queue-status", groupBy(null, col("linesChanged"))), subtype: "plain-text" }),
    metric({ title: "Status", lookup: lookup("queue-status", groupBy(null, col("status"))), subtype: "plain-text" }),
  ),

  // Timeline
  title("Event Timeline", "h3"),
  table({
    lookup: lookup("recent-events", groupBy("timestamp",
      col("timestamp"), col("eventType"), col("actorId"), col("caseStatus")
    )),
    sortable: true,
  }),

  // Plan Items (engine)
  title("Plan Items", "h3"),
  table({
    lookup: lookup("plan-items", groupBy("planItemId",
      col("bindingName"), col("targetType"),
      col("status"), col("executorName"), col("createdAt")
    )),
    sortable: true,
  }),

  // Case Context (engine)
  title("Case Context", "h3"),
  table({
    lookup: lookup("case-context", groupBy("key", col("key"), col("value"))),
  }),

  // Goal Progress (engine)
  title("Goal Progress", "h3"),
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
