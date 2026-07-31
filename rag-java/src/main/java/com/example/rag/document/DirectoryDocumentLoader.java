package com.example.rag.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Loads every file of a directory that matches a glob pattern, equivalent to LangChain's
 * {@code DirectoryLoader}. Each file becomes one document whose {@code source} metadata is its path.
 */
@Component
public class DirectoryDocumentLoader {

    public List<Document> load(String dataPath, String globPattern) {
        Path directory = Paths.get(dataPath);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Directory not found: " + directory.toAbsolutePath());
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

        List<Path> files = new ArrayList<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(path.getFileName()))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(files::add);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        List<Document> documents = new ArrayList<>(files.size());
        for (Path file : files) {
            documents.add(new Document(readText(file), sourceMetadata(file)));
        }
        return documents;
    }

    private static Map<String, Object> sourceMetadata(Path file) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", file.toString());
        return metadata;
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + file, exception);
        }
    }
}
