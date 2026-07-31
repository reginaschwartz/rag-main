package com.example.rag.document;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of LangChain's {@code RecursiveCharacterTextSplitter}: text is split on the first separator
 * that occurs in it, and pieces that are still too long are split again with the remaining, finer
 * separators. Adjacent pieces are then merged back up to {@code chunkSize} with {@code chunkOverlap}
 * characters carried over between neighbouring chunks.
 */
public class RecursiveCharacterTextSplitter {

    private static final List<String> DEFAULT_SEPARATORS = List.of("\n\n", "\n", " ", "");

    private final int chunkSize;
    private final int chunkOverlap;
    private final boolean addStartIndex;
    private final List<String> separators;

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, boolean addStartIndex) {
        this(chunkSize, chunkOverlap, addStartIndex, DEFAULT_SEPARATORS);
    }

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, boolean addStartIndex,
            List<String> separators) {
        if (chunkOverlap > chunkSize) {
            throw new IllegalArgumentException(
                    "Got a larger chunk overlap (" + chunkOverlap + ") than chunk size (" + chunkSize + ").");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.addStartIndex = addStartIndex;
        this.separators = List.copyOf(separators);
    }

    public List<Document> splitDocuments(List<Document> documents) {
        List<String> texts = new ArrayList<>(documents.size());
        List<Map<String, Object>> metadatas = new ArrayList<>(documents.size());
        for (Document document : documents) {
            texts.add(document.pageContent());
            metadatas.add(document.metadata());
        }
        return createDocuments(texts, metadatas);
    }

    public List<Document> createDocuments(List<String> texts, List<Map<String, Object>> metadatas) {
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            Map<String, Object> sourceMetadata = metadatas != null && i < metadatas.size() && metadatas.get(i) != null
                    ? metadatas.get(i)
                    : Map.of();
            int index = 0;
            int previousChunkLength = 0;
            for (String chunk : splitText(text)) {
                Map<String, Object> metadata = new LinkedHashMap<>(sourceMetadata);
                if (addStartIndex) {
                    int offset = index + previousChunkLength - chunkOverlap;
                    index = text.indexOf(chunk, Math.max(0, offset));
                    metadata.put("start_index", index);
                    previousChunkLength = chunk.length();
                }
                documents.add(new Document(chunk, metadata));
            }
        }
        return documents;
    }

    public List<String> splitText(String text) {
        return splitText(text, separators);
    }

    private List<String> splitText(String text, List<String> currentSeparators) {
        List<String> finalChunks = new ArrayList<>();

        String separator = currentSeparators.get(currentSeparators.size() - 1);
        List<String> remainingSeparators = List.of();
        for (int i = 0; i < currentSeparators.size(); i++) {
            String candidate = currentSeparators.get(i);
            if (candidate.isEmpty()) {
                separator = candidate;
                break;
            }
            if (text.contains(candidate)) {
                separator = candidate;
                remainingSeparators = currentSeparators.subList(i + 1, currentSeparators.size());
                break;
            }
        }

        List<String> splits = splitKeepingSeparator(text, separator);

        // Separators stay attached to the text that follows them, so merging joins with "".
        List<String> goodSplits = new ArrayList<>();
        for (String split : splits) {
            if (split.length() < chunkSize) {
                goodSplits.add(split);
                continue;
            }
            if (!goodSplits.isEmpty()) {
                finalChunks.addAll(mergeSplits(goodSplits, ""));
                goodSplits = new ArrayList<>();
            }
            if (remainingSeparators.isEmpty()) {
                finalChunks.add(split);
            } else {
                finalChunks.addAll(splitText(split, remainingSeparators));
            }
        }
        if (!goodSplits.isEmpty()) {
            finalChunks.addAll(mergeSplits(goodSplits, ""));
        }
        return finalChunks;
    }

    private static List<String> splitKeepingSeparator(String text, String separator) {
        List<String> splits = new ArrayList<>();
        if (separator.isEmpty()) {
            for (int i = 0; i < text.length(); i++) {
                splits.add(String.valueOf(text.charAt(i)));
            }
            return splits;
        }

        // Alternates text, separator, text, separator, ... exactly like re.split with a capture group.
        List<String> parts = new ArrayList<>();
        Matcher matcher = Pattern.compile(Pattern.quote(separator)).matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            parts.add(text.substring(cursor, matcher.start()));
            parts.add(matcher.group());
            cursor = matcher.end();
        }
        parts.add(text.substring(cursor));

        splits.add(parts.get(0));
        for (int i = 1; i + 1 < parts.size(); i += 2) {
            splits.add(parts.get(i) + parts.get(i + 1));
        }
        if (parts.size() % 2 == 0) {
            splits.add(parts.get(parts.size() - 1));
        }
        splits.removeIf(String::isEmpty);
        return splits;
    }

    private List<String> mergeSplits(List<String> splits, String separator) {
        int separatorLength = separator.length();
        List<String> merged = new ArrayList<>();
        Deque<String> current = new ArrayDeque<>();
        int total = 0;

        for (String split : splits) {
            int length = split.length();
            if (total + length + (current.isEmpty() ? 0 : separatorLength) > chunkSize) {
                if (!current.isEmpty()) {
                    String joined = joinSplits(current, separator);
                    if (joined != null) {
                        merged.add(joined);
                    }
                    // Drop leading pieces until the carried-over overlap fits the next chunk.
                    while (!current.isEmpty()
                            && (total > chunkOverlap
                                    || (total + length + (current.isEmpty() ? 0 : separatorLength) > chunkSize
                                            && total > 0))) {
                        total -= current.peekFirst().length() + (current.size() > 1 ? separatorLength : 0);
                        current.pollFirst();
                    }
                }
            }
            current.addLast(split);
            total += length + (current.size() > 1 ? separatorLength : 0);
        }

        String joined = joinSplits(current, separator);
        if (joined != null) {
            merged.add(joined);
        }
        return merged;
    }

    private static String joinSplits(Deque<String> splits, String separator) {
        String text = String.join(separator, splits).strip();
        return text.isEmpty() ? null : text;
    }
}
