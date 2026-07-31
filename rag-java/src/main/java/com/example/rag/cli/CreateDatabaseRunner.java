package com.example.rag.cli;

import com.example.rag.service.IndexingService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bulk ingestion command, the equivalent of the former {@code create_database.py}: it loads the
 * configured data directory, splits it and replaces the collection contents.
 */
@Component
@ConditionalOnProperty(name = CliMode.PROPERTY, havingValue = CliMode.INDEX)
public class CreateDatabaseRunner implements ApplicationRunner {

    private final IndexingService indexingService;

    public CreateDatabaseRunner(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        IndexingService.IndexingResult result = indexingService.generateDataStore();
        System.out.printf("Indexed %d documents (%d chunks) into collection %s.%n",
                result.documents(), result.chunks(), result.collection());
    }
}
