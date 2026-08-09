package com.example.rag.jobs;

import java.io.Serial;
import java.io.Serializable;

/**
 * One row of the fitness report: job title, apply URL, and the LLM fitness rank (0–100).
 */
public record JobFitnessResult(
        String jobTitle,
        String applyUrl,
        int fitnessRank,
        String company,
        String location,
        String rationale) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public JobFitnessResult {
        jobTitle = jobTitle != null ? jobTitle : "";
        applyUrl = applyUrl != null ? applyUrl : "";
        fitnessRank = Math.max(0, Math.min(100, fitnessRank));
        company = company != null ? company : "";
        location = location != null ? location : "";
        rationale = rationale != null ? rationale : "";
    }

    static JobFitnessResult from(JobPosting job, int fitnessRank, String rationale) {
        return new JobFitnessResult(
                job.title(),
                job.applyUrl(),
                fitnessRank,
                job.company(),
                job.location(),
                rationale);
    }
}
