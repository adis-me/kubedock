package com.joyrex2001.kubedock.examples.testcontainers;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

@Testcontainers
public class KafkaTest {

    @Test
    void testKafkaStartup() throws ExecutionException, InterruptedException, TimeoutException {
        final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.0"))
                .withStartupAttempts(3)
                .withLogConsumer(new Slf4jLogConsumer(getLogger("kafka")));

        KAFKA_CONTAINER.start();

        try (
                AdminClient adminClient = AdminClient.create(ImmutableMap.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers()
                ));

                KafkaProducer<String, String> producer = new KafkaProducer<>(
                        ImmutableMap.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers(),
                                ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString()
                        ),
                        new StringSerializer(),
                        new StringSerializer()
                );

                KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(
                        ImmutableMap.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers(),
                                ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
                        ),
                        new StringDeserializer(),
                        new StringDeserializer()
                );
        ) {
            String topicName = "messages";

            Collection<NewTopic> topics = Collections.singletonList(new NewTopic(topicName, 1, (short) 1));
            adminClient.createTopics(topics).all().get(30, TimeUnit.SECONDS);

            consumer.subscribe(Collections.singletonList(topicName));

            producer.send(new ProducerRecord<>(topicName, "testcontainers", "rulezzz")).get();

            Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                if (records.isEmpty()) {
                    return false;
                }

                assertThat(records)
                        .hasSize(1)
                        .extracting(ConsumerRecord::topic, ConsumerRecord::key, ConsumerRecord::value)
                        .containsExactly(Tuple.tuple(topicName, "testcontainers", "rulezzz"));

                return true;
            });

            consumer.unsubscribe();
        }

        KAFKA_CONTAINER.stop();
    }
}
