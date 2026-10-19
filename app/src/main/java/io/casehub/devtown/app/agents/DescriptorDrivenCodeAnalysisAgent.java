package io.casehub.devtown.app.agents;

import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.devtown.review.CodeAnalysisAgent;
import io.casehub.devtown.review.CodeAnalysisResult;
import io.casehub.devtown.review.LlmAgentBase;
import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class DescriptorDrivenCodeAnalysisAgent extends LlmAgentBase implements CodeAnalysisAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.CODE_ANALYSIS; }
    @Override protected String agentId() { return "code-analyzer"; }

    @Override
    public CodeAnalysisResult analyse(ReviewContext context) {
        if (context.diff() == null) {
            return conservativeDefault();
        }
        String modelRef = resolveModelRef();
        String fileList = context.diff().files().stream()
            .map(PrDiff.FileDiff::path)
            .collect(Collectors.joining("\n  - ", "  - ", ""));

        var config = AgentSessionConfig.of(
            systemPromptBody(), buildUserPrompt(context, fileList), INVOCATION_TIMEOUT);
        if (modelRef != null) {
            config = config.withModel(modelRef);
        }

        var result = invokeWithRetry(config, MAX_RETRIES, CodeAnalysisResult.class);

        if (result instanceof InvocationResult.Success<?> s) {
            return (CodeAnalysisResult) s.value();
        }
        return conservativeDefault();
    }

    private static CodeAnalysisResult conservativeDefault() {
        return new CodeAnalysisResult(true, true, true, "unknown", List.of(), List.of());
    }

    @Override
    protected String systemPromptBody() {
        String prompt = resolveSystemPrompt();
        if (prompt == null) {
            throw new IllegalStateException(
                "No systemPrompt in descriptor extensionData for code-analyzer");
        }
        return prompt;
    }

    private String buildUserPrompt(ReviewContext context, String fileList) {
        return "PR #" + context.pr().prNumber() + " in " + context.pr().repo()
            + " (" + context.pr().linesChanged() + " lines changed)\n\nChanged files:\n"
            + fileList;
    }
}
