package com.example.rag.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Writes the fitness report as JSON and formats a console table of title / apply URL / rank. */
@Component
public class FitnessReportWriter {

    private final ObjectMapper objectMapper;

    public FitnessReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void writeJson(FitnessReport report, Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(path.toFile(), report);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write report to " + path, exception);
        }
    }

    public void printConsole(FitnessReport report) {
        System.out.printf(
                "Job fitness report | resume=%s | emails=%d | jobs=%d | source=%s%n",
                report.resumePath(),
                report.emailsScanned(),
                report.jobsScanned(),
                report.mailSource());
        System.out.println("-".repeat(100));
        System.out.printf("%-4s  %-6s  %-40s  %s%n", "#", "Rank", "Job title", "Apply URL");
        System.out.println("-".repeat(100));

        int index = 1;
        for (JobFitnessResult result : report.results()) {
            System.out.printf(
                    "%-4d  %-6d  %-40s  %s%n",
                    index++,
                    result.fitnessRank(),
                    truncate(result.jobTitle(), 40),
                    result.applyUrl());
            if (!result.rationale().isBlank()) {
                System.out.printf("      %s%n", result.rationale());
            }
        }
        if (report.results().isEmpty()) {
            System.out.println("(no jobs found)");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
