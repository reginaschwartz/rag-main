package com.example.rag.cli;

import com.example.rag.config.JobAgentProperties;
import com.example.rag.jobs.FitnessReport;
import com.example.rag.jobs.FitnessReportWriter;
import com.example.rag.jobs.JobFitnessResult;
import com.example.rag.jobs.JobScanAgent;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot job-alert scan:
 * {@code java -jar rag-api.jar --rag.cli=jobs --jobs.source=eml --jobs.since=2026-08-01}.
 */
@Component
@ConditionalOnProperty(name = CliMode.PROPERTY, havingValue = CliMode.JOBS)
public class JobScanRunner implements ApplicationRunner {

    private final JobScanAgent agent;
    private final FitnessReportWriter reportWriter;
    private final JobAgentProperties properties;

    public JobScanRunner(JobScanAgent agent, FitnessReportWriter reportWriter, JobAgentProperties properties) {
        this.agent = agent;
        this.reportWriter = reportWriter;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        FitnessReport report = agent.run();
        reportWriter.printConsole(report);

        Path reportPath = Path.of(properties.reportPath());
        reportWriter.writeJson(report, reportPath);
        System.out.println();
        System.out.println("Wrote JSON report: " + reportPath.toAbsolutePath());

        int minRank = properties.notification().minRank();
        List<JobFitnessResult> highFits = report.results().stream()
                .filter(result -> result.fitnessRank() >= minRank)
                .toList();
        System.out.printf(
                "High-fit jobs (rank >= %d%%): %d — emailed to %s when SMTP is configured.%n",
                minRank,
                highFits.size(),
                properties.notification().to());
    }
}
