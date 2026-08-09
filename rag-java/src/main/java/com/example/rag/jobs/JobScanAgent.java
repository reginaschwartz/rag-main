package com.example.rag.jobs;

import com.example.rag.config.JobAgentProperties;
import com.example.rag.jobs.graph.JobScanWorkflow;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Agent that reads LinkedIn job-alert emails from a configured date onward, scores each job with an
 * LLM, and — via a LangGraph4j conditional edge — notifies only when at least one fitness result is
 * above 60%.
 */
@Component
public class JobScanAgent {

    private final JobAgentProperties properties;
    private final JobScanWorkflow workflow;

    public JobScanAgent(JobAgentProperties properties, JobScanWorkflow workflow) {
        this.properties = properties;
        this.workflow = workflow;
    }

    /** Uses {@code jobs.since} from configuration. */
    public FitnessReport run() {
        return run(properties.sinceDate());
    }

    /**
     * Runs the LangGraph job-scan workflow from {@code since}: score jobs with the LLM, then
     * conditionally notify on high fits.
     */
    public FitnessReport run(LocalDate since) {
        Objects.requireNonNull(since, "since date is required");
        return workflow.run(since);
    }
}
