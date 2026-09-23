package io.casehub.devtown.review;

import io.casehub.blocks.agent.StructuredAgentInvoker;
import io.casehub.blocks.agent.StructuredAgentInvoker.InvocationResult;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jboss.logging.Logger;

import java.time.Duration;

public abstract class LlmAgentBase {

    private static final Logger log = Logger.getLogger(LlmAgentBase.class);

    protected static final int MAX_RETRIES = 2;
    protected static final Duration INVOCATION_TIMEOUT = Duration.ofMinutes(3);

    protected abstract AgentProvider agentProvider();
    protected abstract AgentRegistry agentRegistry();

    protected abstract String agentId();
    protected abstract String systemPromptBody();

    public abstract String capability();

    protected String resolveModelRef() {
        AgentRegistry registry = agentRegistry();
        if (registry == null) {
            return null;
        }
        return registry.findById(agentId(), "default")
            .flatMap(desc -> desc.capabilities().stream()
                .filter(c -> c.name().equals(capability()))
                .findFirst()
                .map(AgentCapability::modelRef))
            .orElse(null);
    }

    protected <T> InvocationResult<T> invokeWithRetry(
            AgentSessionConfig config, int maxRetries, Class<T> responseType) {
        InvocationResult<T> result = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            result = StructuredAgentInvoker.invoke(agentProvider(), config, responseType);
            if (!(result instanceof InvocationResult.AgentError<T>)) {
                return result;
            }
            if (attempt < maxRetries) {
                log.debugf("Retry %d/%d for %s: %s", attempt + 1, maxRetries,
                    agentId(), ((InvocationResult.AgentError<T>) result).reason());
            }
        }
        return result;
    }
}
