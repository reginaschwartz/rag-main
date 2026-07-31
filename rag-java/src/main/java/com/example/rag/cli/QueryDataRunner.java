package com.example.rag.cli;

import com.example.rag.error.ApiException;
import com.example.rag.service.QueryResult;
import com.example.rag.service.QueryService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Query command, the equivalent of the former {@code query_data.py}:
 * {@code java -jar rag-api.jar --rag.cli=query "What happens to Alice?"}.
 */
@Component
@ConditionalOnProperty(name = CliMode.PROPERTY, havingValue = CliMode.QUERY)
public class QueryDataRunner implements ApplicationRunner {

    private final QueryService queryService;

    public QueryDataRunner(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String queryText = resolveQueryText(args);
        if (queryText.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing query text. Usage: --rag.cli=query \"<query text>\"");
        }
        String contextTag = firstOptionValue(args, "rag.context-tag");

        try {
            QueryResult result = queryService.answer(queryText, null, null, contextTag);
            System.out.println(result.prompt());
            System.out.printf("Response: %s%nSources: %s%n", result.response(), formatSources(result.sources()));
        } catch (ApiException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND) {
                System.out.println(exception.detail());
                return;
            }
            throw exception;
        }
    }

    private static String resolveQueryText(ApplicationArguments args) {
        String option = firstOptionValue(args, "rag.query-text");
        if (option != null) {
            return option;
        }
        return String.join(" ", args.getNonOptionArgs());
    }

    private static String firstOptionValue(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String formatSources(List<String> sources) {
        return sources.stream()
                .map(source -> "'" + source + "'")
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
