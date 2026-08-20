package com.example.kafkapartition.server;

import java.util.Collection;
import java.util.function.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.binder.kafka.KafkaBindingRebalanceListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

/**
 * Spring Cloud Stream input: messages arrive as a {@link Consumer} callback.
 * There is no application-level {@code poll()} loop.
 */
@Configuration
public class StreamBindings {

    private static final Logger log = LoggerFactory.getLogger(StreamBindings.class);

    @Value("${spring.cloud.stream.bindings.onMessage-in-0.destination}")
    private String topic;

    @Value("${spring.cloud.stream.instance-index}")
    private int listenPartition;

    @Value("${spring.cloud.stream.instance-count}")
    private int partitionCount;

    @Bean
    public Consumer<Message<String>> onMessage() {
        return message -> {
            Integer partition = message.getHeaders().get(KafkaHeaders.RECEIVED_PARTITION, Integer.class);
            Long offset = message.getHeaders().get(KafkaHeaders.OFFSET, Long.class);
            Object key = message.getHeaders().get(KafkaHeaders.RECEIVED_KEY);
            log.info(
                    "consumed topic={} partition={} offset={} key={} payload={}",
                    topic,
                    partition,
                    offset,
                    key,
                    message.getPayload());
        };
    }

    @Bean
    public KafkaBindingRebalanceListener partitionAssignmentLogger() {
        return new KafkaBindingRebalanceListener() {
            @Override
            public void onPartitionsAssigned(
                    String bindingName,
                    org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                    Collection<TopicPartition> partitions,
                    boolean initial) {
                log.info(
                        "listening to topic={} intendedPartition={} (instanceIndex={} of instanceCount={}); assigned={}",
                        topic,
                        listenPartition,
                        listenPartition,
                        partitionCount,
                        partitions);
            }
        };
    }
}
