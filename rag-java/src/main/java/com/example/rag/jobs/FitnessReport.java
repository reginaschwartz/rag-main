package com.example.rag.jobs;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Ranked report of job fitness results (highest rank first). */
public record FitnessReport(
        Instant generatedAt,
        String resumePath,
        String mailSource,
        int emailsScanned,
        int jobsScanned,
        List<JobFitnessResult> results) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public FitnessReport {
        results = results == null
                ? List.of()
                : results.stream()
                        .sorted(Comparator.comparingInt(JobFitnessResult::fitnessRank).reversed()
                                .thenComparing(JobFitnessResult::jobTitle))
                        .toList();
    }
}
