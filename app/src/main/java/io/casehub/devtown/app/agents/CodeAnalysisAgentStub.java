package io.casehub.devtown.app.agents;

import io.casehub.devtown.review.CodeAnalysisAgent;
import io.casehub.devtown.review.CodeAnalysisResult;
import io.casehub.devtown.review.ReviewContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class CodeAnalysisAgentStub implements CodeAnalysisAgent {

    @Override
    public CodeAnalysisResult analyse(ReviewContext context) {
        return new CodeAnalysisResult(true, false, false,
            "unknown", List.of(), List.of());
    }
}
