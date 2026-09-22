package io.casehub.devtown.review;

import io.casehub.devtown.domain.ReviewFinding;
import java.util.List;
import java.util.UUID;

public record PrReviewOutcome(String verdict, List<ReviewFinding> findings, UUID caseId) {}
