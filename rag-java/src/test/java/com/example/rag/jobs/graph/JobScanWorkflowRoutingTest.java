package com.example.rag.jobs.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rag.jobs.JobFitnessResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobScanWorkflowRoutingTest {

    @Test
    void routesToNotifyWhenAnyFitnessIsStrictlyAbove60() {
        JobScanGraphState state = stateWithRanks(40, 61, 10);

        assertThat(JobScanWorkflow.routeAfterScoring(state)).isEqualTo(JobScanWorkflow.NOTIFY_HIGH_FITS);
    }

    @Test
    void skipsNotifyWhenAllFitnessAtOrBelow60() {
        JobScanGraphState state = stateWithRanks(60, 45, 0);

        assertThat(JobScanWorkflow.routeAfterScoring(state)).isEqualTo(JobScanWorkflow.SKIP_NOTIFY);
    }

    @Test
    void skipsNotifyWhenResultsEmpty() {
        JobScanGraphState state = new JobScanGraphState(Map.of(JobScanGraphState.RESULTS, List.of()));

        assertThat(JobScanWorkflow.routeAfterScoring(state)).isEqualTo(JobScanWorkflow.SKIP_NOTIFY);
    }

    private static JobScanGraphState stateWithRanks(int... ranks) {
        List<JobFitnessResult> results = new java.util.ArrayList<>();
        for (int i = 0; i < ranks.length; i++) {
            results.add(new JobFitnessResult("Job " + i, "https://example.com/" + i, ranks[i], "", "", ""));
        }
        return new JobScanGraphState(Map.of(JobScanGraphState.RESULTS, results));
    }
}
