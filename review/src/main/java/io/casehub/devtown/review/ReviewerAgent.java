package io.casehub.devtown.review;

public interface ReviewerAgent {
    String capability();

    ReviewerOutcome handle(ReviewContext context);

    default int priority() {return 0;}
}
