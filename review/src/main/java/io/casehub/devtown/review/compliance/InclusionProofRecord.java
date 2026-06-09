package io.casehub.devtown.review.compliance;

import java.util.List;

public record InclusionProofRecord(
    int entryIndex,
    int treeSize,
    String leafHash,
    List<ProofStepRecord> siblings,
    String treeRoot
) {
    public record ProofStepRecord(String hash, String side) {}
}
