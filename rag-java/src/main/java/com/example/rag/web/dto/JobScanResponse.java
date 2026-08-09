package com.example.rag.web.dto;

import com.example.rag.jobs.FitnessReport;
import com.example.rag.jobs.JobFitnessResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** HTTP response for {@code POST /jobs/scan}. */
public record JobScanResponse(
        LocalDate since,
        Instant generatedAt,
        String resumePath,
        String mailSource,
        int emailsScanned,
        int jobsScanned,
        List<JobFitnessResult> results) {

    public static JobScanResponse from(LocalDate since, FitnessReport report) {
        return new JobScanResponse(
                since,
                report.generatedAt(),
                report.resumePath(),
                report.mailSource(),
                report.emailsScanned(),
                report.jobsScanned(),
                report.results());
    }
}
