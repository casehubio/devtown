package io.casehub.devtown.review;

import java.util.List;
import java.util.UUID;

public record PrReviewOutcome(String verdict, List<String> findings, UUID caseId) {}
