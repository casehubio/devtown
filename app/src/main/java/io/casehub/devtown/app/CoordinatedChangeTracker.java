package io.casehub.devtown.app;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class CoordinatedChangeTracker {

    private final ConcurrentHashMap<UUID, CoordinationState> coordinations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Entry> reviewIndex = new ConcurrentHashMap<>();

    public void register(UUID parentCaseId, String repo, UUID reviewCaseId) {
        var state = coordinations.computeIfAbsent(parentCaseId, k -> new CoordinationState());
        state.repos.put(repo, reviewCaseId);
        reviewIndex.put(reviewCaseId, new Entry(parentCaseId, repo, reviewCaseId));
    }

    public Entry findByReviewCaseId(UUID reviewCaseId) {
        return reviewIndex.get(reviewCaseId);
    }

    public Set<UUID> findReviewCaseIds(UUID parentCaseId) {
        var state = coordinations.get(parentCaseId);
        return state != null ? new HashSet<>(state.repos.values()) : Set.of();
    }

    public boolean isPartOfCoordinatedChange(UUID reviewCaseId) {
        return reviewIndex.containsKey(reviewCaseId);
    }

    public boolean markCompleted(UUID parentCaseId, String repo) {
        var state = coordinations.get(parentCaseId);
        return state != null && state.completedRepos.add(repo);
    }

    public boolean tryTransitionToAllCompleted(UUID parentCaseId) {
        var state = coordinations.get(parentCaseId);
        if (state == null) return false;
        if (state.completedRepos.size() < state.repos.size()) return false;
        return state.allCompletedFired.compareAndSet(false, true);
    }

    public void markParentTerminal(UUID parentCaseId) {
        var state = coordinations.get(parentCaseId);
        if (state != null) state.parentTerminal.set(true);
    }

    public boolean isParentTerminal(UUID parentCaseId) {
        var state = coordinations.get(parentCaseId);
        return state != null && state.parentTerminal.get();
    }

    public record Entry(UUID parentCaseId, String repo, UUID reviewCaseId) {}

    private static class CoordinationState {
        final ConcurrentHashMap<String, UUID> repos = new ConcurrentHashMap<>();
        final Set<String> completedRepos = ConcurrentHashMap.newKeySet();
        final AtomicBoolean allCompletedFired = new AtomicBoolean(false);
        final AtomicBoolean parentTerminal = new AtomicBoolean(false);
    }
}
