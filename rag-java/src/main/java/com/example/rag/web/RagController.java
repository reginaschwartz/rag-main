package com.example.rag.web;

import com.example.rag.config.JobAgentProperties;
import com.example.rag.error.ApiException;
import com.example.rag.jobs.FitnessReport;
import com.example.rag.jobs.FitnessReportWriter;
import com.example.rag.jobs.JobScanAgent;
import com.example.rag.service.IndexingService;
import com.example.rag.service.QueryResult;
import com.example.rag.service.QueryService;
import com.example.rag.web.dto.IndexResponse;
import com.example.rag.web.dto.JobScanResponse;
import com.example.rag.web.dto.QueryRequest;
import com.example.rag.web.dto.QueryResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@ConditionalOnBean({IndexingService.class, QueryService.class})
public class RagController {

    private final IndexingService indexingService;
    private final QueryService queryService;
    private final JobScanAgent jobScanAgent;
    private final FitnessReportWriter fitnessReportWriter;
    private final JobAgentProperties jobAgentProperties;

    public RagController(
            IndexingService indexingService,
            QueryService queryService,
            JobScanAgent jobScanAgent,
            FitnessReportWriter fitnessReportWriter,
            JobAgentProperties jobAgentProperties) {
        this.indexingService = indexingService;
        this.queryService = queryService;
        this.jobScanAgent = jobScanAgent;
        this.fitnessReportWriter = fitnessReportWriter;
        this.jobAgentProperties = jobAgentProperties;
    }

    @PostMapping(path = "/index", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IndexResponse indexDocuments(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "metadata_json", required = false) String metadataJson,
            @RequestParam(name = "reset_collection", defaultValue = "false") boolean resetCollection,
            @RequestParam(name = "context_tag", required = false) String contextTag) {
        byte[] content = read(file);
        IndexingService.IndexingResult result =
                indexingService.indexUpload(content, file.getOriginalFilename(), metadataJson, resetCollection,
                        contextTag);
        return new IndexResponse(result.documents(), result.chunks(), result.collection());
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        QueryResult result = queryService.answer(request.queryText(), request.k(), request.minRelevance(),
                request.contextTag());
        return new QueryResponse(result.response(), result.sources());
    }

    /**
     * Scan LinkedIn job-alert emails from {@code since} (inclusive), score each job vs the resume,
     * email high-fit matches, and return the ranked report.
     *
     * <p>Postman / browser: {@code http://localhost:8000/jobs/scan?since=2026-08-01}
     * (GET or POST; no body, do not send Content-Type: application/json).
     */
    @RequestMapping(
            path = "/jobs/scan",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JobScanResponse scanJobs(
            @RequestParam("since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        FitnessReport report = jobScanAgent.run(since);
        fitnessReportWriter.writeJson(report, Path.of(jobAgentProperties.reportPath()));
        return JobScanResponse.from(since, report);
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("Uploaded file could not be read.");
        }
    }
}
