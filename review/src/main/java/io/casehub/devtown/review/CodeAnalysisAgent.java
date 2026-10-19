package io.casehub.devtown.review;

public interface CodeAnalysisAgent {
    CodeAnalysisResult analyse(ReviewContext context);
}
