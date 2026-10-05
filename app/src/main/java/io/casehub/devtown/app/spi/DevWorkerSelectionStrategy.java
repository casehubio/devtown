package io.casehub.devtown.app.spi;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class DevWorkerSelectionStrategy implements WorkerSelectionStrategy {

    @Override
    public String id() {
        return "least-loaded";
    }

    @Override
    public AssignmentDecision select(SelectionContext context, List<WorkerCandidate> candidates) {
        if (candidates.isEmpty()) {
            return AssignmentDecision.noChange();
        }
        return AssignmentDecision.assignTo(candidates.getFirst().id());
    }
}
