package io.casehub.devtown.review;

public interface ReviewerAgent {
    String capability();

    ReviewerOutcome handle(ReviewContext context);
}
