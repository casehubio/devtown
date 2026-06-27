package io.casehub.devtown.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DependencyResolver {

    private DependencyResolver() {}

    public static List<QueuedPr> resolve(List<QueuedPr> prs) {
        Map<Integer, QueuedPr> byNumber = new LinkedHashMap<>();
        for (QueuedPr pr : prs) byNumber.put(pr.number(), pr);

        Map<Integer, Integer> inDegree = new LinkedHashMap<>();
        Map<Integer, List<Integer>> dependents = new LinkedHashMap<>();
        for (QueuedPr pr : prs) {
            inDegree.put(pr.number(), 0);
            dependents.put(pr.number(), new ArrayList<>());
        }
        for (QueuedPr pr : prs) {
            for (int dep : pr.dependsOn()) {
                if (byNumber.containsKey(dep)) {
                    dependents.get(dep).add(pr.number());
                    inDegree.merge(pr.number(), 1, Integer::sum);
                }
            }
        }

        Deque<Integer> ready = new ArrayDeque<>();
        for (var e : inDegree.entrySet()) {
            if (e.getValue() == 0) ready.add(e.getKey());
        }

        List<QueuedPr> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            int n = ready.poll();
            result.add(byNumber.get(n));
            for (int dep : dependents.get(n)) {
                int newDegree = inDegree.merge(dep, -1, Integer::sum);
                if (newDegree == 0) ready.add(dep);
            }
        }

        if (result.size() != prs.size()) {
            throw new IllegalStateException("Dependency cycle detected among queued PRs");
        }
        return result;
    }
}
