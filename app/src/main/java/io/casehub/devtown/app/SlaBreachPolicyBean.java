package io.casehub.devtown.app;

import io.casehub.devtown.domain.sla.DefaultSlaBreachPolicy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
@ApplicationScoped
public class SlaBreachPolicyBean extends DefaultSlaBreachPolicy {}
