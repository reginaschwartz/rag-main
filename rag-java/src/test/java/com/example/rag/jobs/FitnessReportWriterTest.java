package com.example.rag.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FitnessReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesJsonSortedByFitnessRank() throws Exception {
        FitnessReport report = new FitnessReport(
                Instant.parse("2026-08-08T12:00:00Z"),
                "resume.docx",
                "eml",
                1,
                2,
                List.of(
                        new JobFitnessResult("Low Fit", "https://example.com/1", 40, "", "", ""),
                        new JobFitnessResult("High Fit", "https://example.com/2", 90, "", "", "")));

        Path out = tempDir.resolve("report.json");
        new FitnessReportWriter(new ObjectMapper()).writeJson(report, out);

        String json = Files.readString(out);
        assertThat(json.indexOf("High Fit")).isLessThan(json.indexOf("Low Fit"));
        assertThat(json).contains("\"fitnessRank\" : 90");
        assertThat(json).contains("\"applyUrl\" : \"https://example.com/2\"");
    }
}
