package io.casehub.devtown.app.agents;

import io.casehub.devtown.review.CodeAnalysisAgent;
import io.casehub.devtown.review.CodeAnalysisResult;
import io.casehub.devtown.review.LlmAgentBase;
import io.casehub.devtown.review.PrDiff;
import io.casehub.devtown.review.ReviewContext;
import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.devtown.domain.ReviewDomain;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.stream.Collectors;

@ApplicationScoped
public class LlmCodeAnalysisAgent extends LlmAgentBase implements CodeAnalysisAgent {

    @Inject AgentProvider agentProvider;
    @Inject AgentRegistry agentRegistry;

    @Override protected AgentProvider agentProvider() { return agentProvider; }
    @Override protected AgentRegistry agentRegistry() { return agentRegistry; }
    @Override public String capability() { return ReviewDomain.CODE_ANALYSIS; }
    @Override protected String agentId() { return "code-analyzer"; }

    @Override
    public int priority() { return 1; }

    @Override
    public CodeAnalysisResult analyse(ReviewContext context) {
        if (context.diff() == null) {
            return conservativeDefault();
        }
        String modelRef = resolveModelRef();
        String fileList = context.diff().files().stream()
            .map(PrDiff.FileDiff::path)
            .collect(Collectors.joining("\n  - ", "  - ", ""));

        var config = AgentSessionConfig.of(systemPromptBody(), buildUserPrompt(context, fileList), INVOCATION_TIMEOUT);
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
        return new CodeAnalysisResult(true, true, true, "unknown", java.util.List.of(), java.util.List.of());
    }

    @Override
    protected String systemPromptBody() {
        return """
            You are a code analysis classifier. Analyze the PR diff and classify it:
            - Is the change security-sensitive? (auth, crypto, input validation, secrets)
            - Does it cross architecture boundaries? (module boundaries, API surface changes)
            - What is the scope? (single-module, cross-module, infrastructure)
            - Flag specific files that need specialist review.

            Respond with a JSON object:
            {"complete": true, "securitySensitive": <bool>, "architectureCrossing": <bool>,
             "scope": "<string>", "flaggedFiles": ["<path>", ...], "crossingPoints": ["<description>", ...]}""";
    }

    private String buildUserPrompt(ReviewContext context, String fileList) {
        return "PR #" + context.pr().prNumber() + " in " + context.pr().repo()
            + " (" + context.pr().linesChanged() + " lines changed)\n\nChanged files:\n" + fileList;
    }
}
