package io.casehub.devtown.domain;

public record ReviewFinding(
        Severity severity,
        String category,
        String filePath,
        LineRange lineRange,
        String message,
        double confidence
) {
    public ReviewFinding {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public enum Severity {CRITICAL, HIGH, MEDIUM, LOW, INFO}

    public record LineRange(int startLine, int endLine) {}
}
