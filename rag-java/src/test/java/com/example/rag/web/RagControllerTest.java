package com.example.rag.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.rag.error.ApiException;
import com.example.rag.service.IndexingService;
import com.example.rag.service.QueryResult;
import com.example.rag.service.QueryService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexingService indexingService;

    @MockitoBean
    private QueryService queryService;

    @Test
    void indexesAnUploadedFile() throws Exception {
        given(indexingService.indexUpload(any(), eq("alice.md"), isNull(), eq(true), eq("book")))
                .willReturn(new IndexingService.IndexingResult(1, 7, "default"));

        MockMultipartFile file = new MockMultipartFile("file", "alice.md", "text/markdown",
                "Alice fell down the rabbit hole.".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/index").file(file)
                        .param("reset_collection", "true")
                        .param("context_tag", "book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(1))
                .andExpect(jsonPath("$.chunks").value(7))
                .andExpect(jsonPath("$.collection").value("default"));
    }

    @Test
    void rejectsAnUploadWithoutAFile() throws Exception {
        mockMvc.perform(multipart("/index"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void reportsInvalidMetadataAsBadRequest() throws Exception {
        given(indexingService.indexUpload(any(), any(), any(), anyBoolean(), any()))
                .willThrow(ApiException.badRequest("metadata_json must be a JSON object."));

        MockMultipartFile file = new MockMultipartFile("file", "alice.md", "text/markdown",
                "Alice.".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/index").file(file).param("metadata_json", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("metadata_json must be a JSON object."));
    }

    @Test
    void answersAQuery() throws Exception {
        given(queryService.answer("Who is Alice?", 3, 0.7, "book"))
                .willReturn(new QueryResult("prompt", "Alice is the protagonist.", List.of("alice.md")));

        mockMvc.perform(post("/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"query_text": "Who is Alice?", "k": 3, "min_relevance": 0.7, "context_tag": "book"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Alice is the protagonist."))
                .andExpect(jsonPath("$.sources[0]").value("alice.md"));
    }

    @Test
    void returnsNotFoundWhenNothingIsRelevantEnough() throws Exception {
        given(queryService.answer(any(), any(), any(), any()))
                .willThrow(ApiException.notFound("Unable to find matching results."));

        mockMvc.perform(post("/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query_text\": \"Who is Alice?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unable to find matching results."));
    }

    @Test
    void rejectsAQueryWithoutText() throws Exception {
        mockMvc.perform(post("/query").contentType(MediaType.APPLICATION_JSON).content("{\"k\": 3}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("queryText: must not be blank"));
    }
}
