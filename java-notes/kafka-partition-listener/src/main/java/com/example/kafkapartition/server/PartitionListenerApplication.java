package com.example.kafkapartition.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dockerized consumer. The Kafka topic lives on the broker in this stack, not on
 * the workstation producer JVM.
 */
@SpringBootApplication
public class PartitionListenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartitionListenerApplication.class, args);
    }
}
