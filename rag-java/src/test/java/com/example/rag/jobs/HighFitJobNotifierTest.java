package com.example.rag.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HighFitJobNotifierTest {

    @Test
    void buildBodyListsOnlyHighFitJobsWithTitleUrlAndRank() {
        String body = HighFitJobNotifier.buildBody(
                80,
                List.of(new JobFitnessResult(
                        "Senior Java Backend Engineer",
                        "https://www.linkedin.com/jobs/view/4123456789/",
                        85,
                        "Acme Cloud Ltd",
                        "Tel Aviv",
                        "Strong Java and Spring overlap.")));

        assertThat(body).contains("Fitness rank: 85%");
        assertThat(body).contains("Senior Java Backend Engineer");
        assertThat(body).contains("https://www.linkedin.com/jobs/view/4123456789/");
        assertThat(body).doesNotContain("rinatschwartz770@gmail.com");
    }
}
