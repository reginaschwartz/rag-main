package com.example.rag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecursiveCharacterTextSplitterTest {

    private static final int CHUNK_SIZE = 300;
    private static final int CHUNK_OVERLAP = 100;

    private final RecursiveCharacterTextSplitter splitter =
            new RecursiveCharacterTextSplitter(CHUNK_SIZE, CHUNK_OVERLAP, true);

    @Test
    void keepsChunksWithinTheConfiguredSize() {
        String text = paragraphs(12);

        List<String> chunks = splitter.splitText(text);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(CHUNK_SIZE));
    }

    @Test
    void recordsStartIndexThatPointsAtTheChunk() {
        String text = paragraphs(6);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "data/books/sample.md");

        List<Document> documents = splitter.splitDocuments(List.of(new Document(text, metadata)));

        assertThat(documents).isNotEmpty();
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.metadata()).containsEntry("source", "data/books/sample.md");
            int startIndex = (int) document.metadata().get("start_index");
            assertThat(startIndex).isGreaterThanOrEqualTo(0);
            assertThat(text.substring(startIndex, startIndex + document.pageContent().length()))
                    .isEqualTo(document.pageContent());
        });
    }

    @Test
    void splitsOnParagraphsBeforeSmallerSeparators() {
        String text = "First paragraph.\n\nSecond paragraph.";

        assertThat(splitter.splitText(text)).containsExactly("First paragraph.\n\nSecond paragraph.");
    }

    @Test
    void fallsBackToCharactersWhenThereAreNoSeparators() {
        String text = "x".repeat(CHUNK_SIZE + 50);

        List<String> chunks = splitter.splitText(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(CHUNK_SIZE));
    }

    @Test
    void doesNotProduceEmptyChunks() {
        List<String> chunks = splitter.splitText("word\n\n\n\n" + paragraphs(3));

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).isNotBlank());
    }

    @Test
    void rejectsOverlapLargerThanChunkSize() {
        assertThatThrownBy(() -> new RecursiveCharacterTextSplitter(100, 200, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("larger chunk overlap");
    }

    private static String paragraphs(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append("Paragraph ").append(i)
                    .append(" tells a small part of a much longer story about Alice and her curious journey ")
                    .append("through a place where very little behaves the way she expects it to.\n\n");
        }
        return builder.toString();
    }
}
