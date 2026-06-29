package io.casehub.devtown.merge;

public interface MergeQueueAdmissionPort {
    AdmissionResult admit(int prNumber, String repository, String headSha, String author);
}
