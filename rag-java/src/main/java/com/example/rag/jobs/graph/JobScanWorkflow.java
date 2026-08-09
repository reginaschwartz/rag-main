package com.example.rag.jobs.graph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.example.rag.config.JobAgentProperties;
import com.example.rag.jobs.FitnessReport;
import com.example.rag.jobs.HighFitJobNotifier;
import com.example.rag.jobs.JobFitnessResult;
import com.example.rag.jobs.JobFitnessTool;
import com.example.rag.jobs.JobPosting;
import com.example.rag.jobs.LinkedInJobDescriptionFetcher;
import com.example.rag.jobs.LinkedInJobParser;
import com.example.rag.jobs.Resume;
import com.example.rag.jobs.ResumeLoader;
import com.example.rag.jobs.mail.EmailMessage;
import com.example.rag.jobs.mail.MailSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LangGraph4j workflow for job scanning:
 *
 * <pre>
 * START → score_jobs (LLM fitness) ─┬─(any fitness &gt; 60%)→ notify_high_fits → END
 *                                   └─(otherwise)────────→ skip_notify ─────→ END
 * </pre>
 *
 * The conditional edge reads the LLM fitness results from graph state, so the notify action only
 * runs when a previous LLM step produced at least one result above the route threshold.
 */
@Component
public class JobScanWorkflow {

    private static final Logger log = LoggerFactory.getLogger(JobScanWorkflow.class);

    /** Node that fetches mail, enriches postings, and calls the LLM fitness tool. */
    public static final String SCORE_JOBS = "score_jobs";
    /** Node that emails high-fit matches — only reached via the conditional edge. */
    public static final String NOTIFY_HIGH_FITS = "notify_high_fits";
    /** Sink when no LLM result clears the route threshold. */
    public static final String SKIP_NOTIFY = "skip_notify";

    /**
     * Enter the notify branch when at least one LLM fitness rank is strictly greater than this
     * percent (user requirement: more than 60%).
     */
    public static final int ROUTE_NOTIFY_MIN_FITNESS_EXCLUSIVE = 60;

    private final JobAgentProperties properties;
    private final MailSource mailSource;
    private final LinkedInJobParser jobParser;
    private final LinkedInJobDescriptionFetcher jobDescriptionFetcher;
    private final ResumeLoader resumeLoader;
    private final JobFitnessTool fitnessTool;
    private final HighFitJobNotifier highFitJobNotifier;
    private final CompiledGraph<JobScanGraphState> graph;

    public JobScanWorkflow(
            JobAgentProperties properties,
            MailSource mailSource,
            LinkedInJobParser jobParser,
            LinkedInJobDescriptionFetcher jobDescriptionFetcher,
            ResumeLoader resumeLoader,
            JobFitnessTool fitnessTool,
            HighFitJobNotifier highFitJobNotifier) throws GraphStateException {
        this.properties = properties;
        this.mailSource = mailSource;
        this.jobParser = jobParser;
        this.jobDescriptionFetcher = jobDescriptionFetcher;
        this.resumeLoader = resumeLoader;
        this.fitnessTool = fitnessTool;
        this.highFitJobNotifier = highFitJobNotifier;
        this.graph = buildGraph();
    }

    public FitnessReport run(LocalDate since) {
        Objects.requireNonNull(since, "since date is required");
        JobScanGraphState finalState = graph.invoke(Map.of(JobScanGraphState.SINCE, since.toString()))
                .orElseThrow(() -> new IllegalStateException("Job scan LangGraph produced no final state"));
        return finalState.report()
                .orElseThrow(() -> new IllegalStateException("Job scan LangGraph finished without a report"));
    }

    private CompiledGraph<JobScanGraphState> buildGraph() throws GraphStateException {
        return new StateGraph<>(JobScanGraphState.SCHEMA, JobScanGraphState::new)
                .addNode(SCORE_JOBS, node_async(this::scoreJobs))
                .addNode(NOTIFY_HIGH_FITS, node_async(this::notifyHighFits))
                .addNode(SKIP_NOTIFY, node_async(this::skipNotify))
                .addEdge(START, SCORE_JOBS)
                .addConditionalEdges(
                        SCORE_JOBS,
                        edge_async(JobScanWorkflow::routeAfterScoring),
                        Map.of(
                                NOTIFY_HIGH_FITS, NOTIFY_HIGH_FITS,
                                SKIP_NOTIFY, SKIP_NOTIFY))
                .addEdge(NOTIFY_HIGH_FITS, END)
                .addEdge(SKIP_NOTIFY, END)
                .compile();
    }

    /**
     * Conditional edge after the LLM scoring node: branch to notify when any fitness rank is
     * {@code >} {@link #ROUTE_NOTIFY_MIN_FITNESS_EXCLUSIVE}.
     */
    static String routeAfterScoring(JobScanGraphState state) {
        boolean hasHighEnoughFit = state.results().stream()
                .anyMatch(result -> result.fitnessRank() > ROUTE_NOTIFY_MIN_FITNESS_EXCLUSIVE);
        String route = hasHighEnoughFit ? NOTIFY_HIGH_FITS : SKIP_NOTIFY;
        log.info(
                "LangGraph route after LLM scoring: {} (threshold: fitness > {}%; results={})",
                route,
                ROUTE_NOTIFY_MIN_FITNESS_EXCLUSIVE,
                state.results().size());
        return route;
    }

    private Map<String, Object> scoreJobs(JobScanGraphState state) {
        LocalDate since = LocalDate.parse(state.since());
        Resume resume = resumeLoader.load(properties.resumePath());
        Instant sinceInstant = since.atStartOfDay(ZoneOffset.UTC).toInstant();

        log.info("Scanning {} for mail from {} since {}", mailSource.describe(), properties.sender(), since);
        List<EmailMessage> emails = mailSource.fetch(properties.sender(), sinceInstant, properties.maxEmails());

        Map<String, JobPosting> jobsById = new LinkedHashMap<>();
        for (EmailMessage email : emails) {
            for (JobPosting job : jobParser.parse(email)) {
                jobsById.putIfAbsent(job.id(), job);
                if (jobsById.size() >= properties.maxJobs()) {
                    break;
                }
            }
            if (jobsById.size() >= properties.maxJobs()) {
                break;
            }
        }

        List<JobFitnessResult> results = new ArrayList<>();
        for (JobPosting job : List.copyOf(jobsById.values())) {
            JobPosting enriched = jobDescriptionFetcher.enrich(job);
            jobsById.put(enriched.id(), enriched);
            log.info("Scoring fitness for: {} ({})", enriched.title(), enriched.applyUrl());
            results.add(fitnessTool.checkFitness(resume, enriched));
        }

        FitnessReport report = new FitnessReport(
                Instant.now(),
                resume.source(),
                mailSource.describe(),
                emails.size(),
                jobsById.size(),
                results);
        // ArrayList so LangGraph's ObjectStream state clone can serialize results.
        List<JobFitnessResult> rankedResults = new ArrayList<>(report.results());
        String route = routeAfterScoring(new JobScanGraphState(Map.of(JobScanGraphState.RESULTS, rankedResults)));

        return Map.of(
                JobScanGraphState.REPORT, report,
                JobScanGraphState.RESULTS, rankedResults,
                JobScanGraphState.ROUTE, route);
    }

    private Map<String, Object> notifyHighFits(JobScanGraphState state) {
        FitnessReport report = state.report()
                .orElseThrow(() -> new IllegalStateException("notify_high_fits reached without a report"));
        int sent = highFitJobNotifier.notifyHighFits(report);
        return Map.of(JobScanGraphState.NOTIFIED_COUNT, sent);
    }

    private Map<String, Object> skipNotify(JobScanGraphState state) {
        log.info(
                "Skipping high-fit notification: no LLM fitness result above {}%.",
                ROUTE_NOTIFY_MIN_FITNESS_EXCLUSIVE);
        return Map.of(JobScanGraphState.NOTIFIED_COUNT, 0);
    }
}
