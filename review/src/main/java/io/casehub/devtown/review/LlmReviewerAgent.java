package io.casehub.devtown.review;

import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.devtown.domain.ReviewFinding;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class LlmReviewerAgent extends LlmAgentBase implements ReviewerAgent {

    private static final Logger log = Logger.getLogger(LlmReviewerAgent.class);

    private static final int BATCH_TOKEN_BUDGET = 8_000;
    private static final int MAX_BATCHES = 10;
    private static final int CHARS_PER_TOKEN = 4;

    private static final String RESPONSE_SCHEMA = """
        Respond with a JSON object matching this schema exactly:
        {"findings": [{"severity": "CRITICAL|HIGH|MEDIUM|LOW|INFO",
          "category": "<string>", "filePath": "<string>",
          "lineRange": {"startLine": <int>, "endLine": <int>} | null,
          "message": "<string>", "confidence": <0.0-1.0>}]}
        If no findings, respond with: {"findings": []}""";

    @Override
    public int priority() { return 1; }

    @Override
    public ReviewerOutcome handle(ReviewContext context) {
        PrDiff diff = context.diff();
        if (diff == null) {
            return new ReviewerOutcome.Failed("diff not available");
        }
        List<PrDiff.FileDiff> reviewable = diff.files().stream()
                                               .filter(PrDiff.FileDiff::hasReviewablePatch)
                                               .toList();

        if (reviewable.isEmpty()) {
            return new ReviewerOutcome.Declined("no reviewable files");
        }

        String              modelRef          = resolveModelRef();
        List<ReviewFinding> allFindings       = new ArrayList<>();
        List<String>        batchErrors       = new ArrayList<>();
        boolean             anyBatchSucceeded = false;
        Set<String> diffPaths = reviewable.stream()
                                          .map(PrDiff.FileDiff::path).collect(Collectors.toSet());

        var batches = batchFiles(reviewable);
        if (batches.size() > MAX_BATCHES) {
            log.warnf("PR has %d batches, capping at %d — %d files skipped",
                      batches.size(), MAX_BATCHES,
                      batches.subList(MAX_BATCHES, batches.size()).stream()
                             .mapToInt(List::size).sum());
            batches = batches.subList(0, MAX_BATCHES);
        }

        for (var batch : batches) {
            var config = AgentSessionConfig.of(
                    buildSystemPrompt(), buildUserPrompt(context.pr(), batch), INVOCATION_TIMEOUT);
            if (modelRef != null) {
                config = config.withModel(modelRef);
            }

            var result = invokeWithRetry(config, MAX_RETRIES, ReviewFindings.class);

            if (result instanceof InvocationResult.Success<?> s) {
                anyBatchSucceeded = true;
                var findings = ((ReviewFindings) s.value()).findings();
                var validated = findings.stream()
                                        .filter(f -> diffPaths.contains(f.filePath()))
                                        .toList();
                allFindings.addAll(validated);
            } else if (result instanceof InvocationResult.ParseError<?> pe) {
                batchErrors.add("batch parse error: " + pe.parseError());
            } else if (result instanceof InvocationResult.AgentError<?> ae) {
                batchErrors.add("batch agent error: " + ae.reason());
            }
        }

        if (!anyBatchSucceeded && !batchErrors.isEmpty()) {
            return new ReviewerOutcome.Failed(
                    "all batches failed: " + String.join("; ", batchErrors));
        }

        return new ReviewerOutcome.Completed(allFindings);
    }

    protected List<List<PrDiff.FileDiff>> batchFiles(List<PrDiff.FileDiff> files) {
        List<List<PrDiff.FileDiff>> batches = new ArrayList<>();
        List<PrDiff.FileDiff> currentBatch = new ArrayList<>();
        int currentTokens = 0;

        for (var file : files) {
            int fileTokens = estimateTokens(file);
            if (fileTokens > BATCH_TOKEN_BUDGET) {
                if (!currentBatch.isEmpty()) {
                    batches.add(currentBatch);
                    currentBatch = new ArrayList<>();
                    currentTokens = 0;
                }
                batches.add(List.of(file));
                continue;
            }
            if (currentTokens + fileTokens > BATCH_TOKEN_BUDGET && !currentBatch.isEmpty()) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentTokens = 0;
            }
            currentBatch.add(file);
            currentTokens += fileTokens;
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }
        return batches;
    }

    private int estimateTokens(PrDiff.FileDiff file) {
        String patch = file.patch();
        if (patch == null) return 0;
        return patch.length() / CHARS_PER_TOKEN;
    }

    private String buildSystemPrompt() {
        return systemPromptBody() + "\n\n" + RESPONSE_SCHEMA;
    }

    private String buildUserPrompt(PrPayload pr, List<PrDiff.FileDiff> batch) {
        var sb = new StringBuilder();
        sb.append("PR #").append(pr.prNumber()).append(" in ").append(pr.repo());
        sb.append(" (").append(pr.linesChanged()).append(" lines changed)\n\n");
        for (var file : batch) {
            sb.append("### ").append(file.path()).append(" (").append(file.status()).append(")\n");
            String patch = file.patch();
            if (patch != null) {
                if (patch.length() > BATCH_TOKEN_BUDGET * CHARS_PER_TOKEN) {
                    sb.append(patch, 0, BATCH_TOKEN_BUDGET * CHARS_PER_TOKEN);
                    sb.append("\n[truncated]\n");
                } else {
                    sb.append(patch);
                }
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
