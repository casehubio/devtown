package io.casehub.devtown.merge;

public interface MergeQueuePort {
    AdmissionResult admit(int prNumber, String repository, String headSha, String author);

    boolean dequeue(int prNumber, String repository);
}
