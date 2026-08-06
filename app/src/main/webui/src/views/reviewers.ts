import { page, hostPanel } from "@casehubio/pages-ui";

export const reviewersView = page("Reviewers",
  hostPanel("reviewer-workbench", { endpoint: "/api/governance" }),
);
