package com.example.rag.jobs.graph;

import com.example.rag.jobs.FitnessReport;
import com.example.rag.jobs.JobFitnessResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/**
 * Shared LangGraph state for the job-scan workflow. Nodes write updates into these channels; the
 * conditional edge after LLM scoring reads {@link #RESULTS} to choose the next action.
 */
public class JobScanGraphState extends AgentState {

    public static final String SINCE = "since";
    public static final String REPORT = "report";
    public static final String RESULTS = "results";
    public static final String NOTIFIED_COUNT = "notifiedCount";
    public static final String ROUTE = "route";

    /**
     * Defaults must be non-null: LangGraph builds initial state with {@code Collectors.toMap},
     * which rejects null values ({@code REPORT} is therefore reducer-only, set by {@code score_jobs}).
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            SINCE, Channels.base((Supplier<String>) () -> ""),
            REPORT, Channels.<FitnessReport>base((oldValue, newValue) -> newValue),
            RESULTS, Channels.base((Supplier<List<JobFitnessResult>>) ArrayList::new),
            NOTIFIED_COUNT, Channels.base((Supplier<Integer>) () -> 0),
            ROUTE, Channels.base((Supplier<String>) () -> ""));

    public JobScanGraphState(Map<String, Object> initData) {
        super(initData);
    }

    public String since() {
        return this.<String>value(SINCE).orElse("");
    }

    public Optional<FitnessReport> report() {
        return this.value(REPORT);
    }

    public List<JobFitnessResult> results() {
        return this.<List<JobFitnessResult>>value(RESULTS).orElse(List.of());
    }

    public int notifiedCount() {
        return this.<Integer>value(NOTIFIED_COUNT).orElse(0);
    }

    public String route() {
        return this.<String>value(ROUTE).orElse("");
    }
}
