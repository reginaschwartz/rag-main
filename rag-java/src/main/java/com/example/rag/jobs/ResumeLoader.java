package com.example.rag.jobs;

import com.example.rag.document.ContentExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Loads a resume (.docx / .pdf / plain text) into the text the fitness LLM will score against. */
@Component
public class ResumeLoader {

    private final ContentExtractor contentExtractor;

    public ResumeLoader(ContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
    }

    public Resume load(String resumePath) {
        Path path = Path.of(resumePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Resume not found: " + path.toAbsolutePath());
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            String text = contentExtractor.extract(bytes, path.getFileName().toString());
            return new Resume(path.toString(), text);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read resume " + path.toAbsolutePath(), exception);
        }
    }
}
