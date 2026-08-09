package com.example.rag.jobs.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.junit.jupiter.api.Test;

class JobScanGraphStateTest {

    @Test
    void schemaDefaultsAreNonNullForLangGraphInitialState() {
        AgentStateFactory<JobScanGraphState> factory = JobScanGraphState::new;
        Map<String, Object> defaults = factory.initialDataFromSchema(JobScanGraphState.SCHEMA);

        assertThat(defaults).doesNotContainKey(JobScanGraphState.REPORT);
        assertThat(defaults.get(JobScanGraphState.SINCE)).isEqualTo("");
        assertThat(defaults.get(JobScanGraphState.RESULTS)).isInstanceOf(java.util.List.class);
        assertThat(defaults.get(JobScanGraphState.NOTIFIED_COUNT)).isEqualTo(0);
    }
}
