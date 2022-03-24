package com.joyrex2001.kubedock.examples;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class KafkaTest {

    @Test
    void testKafkaStartup() {
        final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.0"))
                .withStartupAttempts(3);

        KAFKA_CONTAINER.start();
        KAFKA_CONTAINER.stop();
    }
}