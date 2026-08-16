package com.example.pipeline.sink;

import com.example.pipeline.Event;
import java.util.List;

/** Destination for a flushed batch of Kafka-consumed events. */
public interface RowSink extends AutoCloseable {

    void writeBatch(List<SinkRecord> records) throws Exception;

    @Override
    default void close() {
        // optional
    }

    record SinkRecord(Event event, int partition, long offset) {}
}
