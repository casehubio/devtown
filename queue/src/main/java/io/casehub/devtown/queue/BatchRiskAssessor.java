package io.casehub.devtown.queue;

import java.util.List;

public interface BatchRiskAssessor {

    List<QueuedPr> assessRisk(List<QueuedPr> candidates, String repository, String tenantId);
}
