import {
  page, tabs, rows, gridTable, dataTable, title,
} from "@casehubio/pages-ui";
import { lookup, groupBy, col } from "@casehubio/pages-ui";

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

const reviewDetail = rows(
  title("Review Detail", "h2"),

  gridTable({
    lookup: lookup("queue-status", groupBy(null,
      col("repo"), col("prNumber"), col("contributor"),
      col("linesChanged"), col("status")
    )),
    rowHeaders: true,
    compact: true,
  }),

  title("Event Timeline", "h3"),
  dataTable({
    lookup: lookup("recent-events", groupBy("timestamp",
      col("timestamp"), col("eventType"), col("actorId"), col("caseStatus")
    )),
    sortable: true,
  }),

  title("Plan Items", "h3"),
  dataTable({
    lookup: lookup("plan-items", groupBy("planItemId",
      col("bindingName"), col("targetType"),
      col("status"), col("executorName"), col("createdAt"),
      col("activationContext"),
    )),
    sortable: true,
  }),

  title("Goal Progress", "h3"),
  dataTable({
    lookup: lookup("goal-status", groupBy("name",
      col("name"), col("kind"), col("satisfied")
    )),
  }),
);

export const reviewsView = page("Reviews",
  tabs(
    ["List", reviewsList],
    ["Detail", reviewDetail],
  ),
);
