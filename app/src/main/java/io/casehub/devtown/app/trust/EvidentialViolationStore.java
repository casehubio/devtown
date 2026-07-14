package io.casehub.devtown.app.trust;

import io.casehub.qhorus.runtime.audit.BenchmarkViolation;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class EvidentialViolationStore {

    public record ViolationRecord(
            String commitmentId,
            String actorId,
            String capabilityTag,
            List<BenchmarkViolation> violations,
            Instant timestamp
    ) {}

    private final ConcurrentHashMap<String, ViolationRecord> records = new ConcurrentHashMap<>();

    public void record(String commitmentId, String actorId, String capabilityTag,
                       List<BenchmarkViolation> violations) {
        records.put(commitmentId, new ViolationRecord(
                commitmentId, actorId, capabilityTag,
                List.copyOf(violations), Instant.now()));
    }

    public Optional<ViolationRecord> get(String commitmentId) {
        return Optional.ofNullable(records.get(commitmentId));
    }

    public List<ViolationRecord> all() {
        return List.copyOf(records.values());
    }

    public int size() {
        return records.size();
    }
}
