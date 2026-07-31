package com.example.rag.web;

import com.example.rag.error.ApiException;
import com.example.rag.service.IndexingService;
import com.example.rag.service.QueryResult;
import com.example.rag.service.QueryService;
import com.example.rag.web.dto.IndexResponse;
import com.example.rag.web.dto.QueryRequest;
import com.example.rag.web.dto.QueryResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class RagController {

    private final IndexingService indexingService;
    private final QueryService queryService;

    public RagController(IndexingService indexingService, QueryService queryService) {
        this.indexingService = indexingService;
        this.queryService = queryService;
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

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("Uploaded file could not be read.");
        }
    }
}
