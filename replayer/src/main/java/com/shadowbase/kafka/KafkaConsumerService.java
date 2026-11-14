package com.shadowbase.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadowbase.model.ShadowBaseMessage;
import com.shadowbase.service.MessageProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class KafkaConsumerService {

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final MessageProcessingService processingService;

    public KafkaConsumerService() {

        Properties properties = new Properties();

        properties.put(
                "bootstrap.servers",
                KafkaConfig.BOOTSTRAP_SERVERS
        );

        properties.put(
                "group.id",
                KafkaConfig.GROUP_ID
        );

        properties.put(
                "key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer"
        );

        properties.put(
                "value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer"
        );

        properties.put(
                "auto.offset.reset",
                "earliest"
        );

        consumer = new KafkaConsumer<>(properties);

        objectMapper = new ObjectMapper();

        processingService = new MessageProcessingService();
    }

    public void start() {

        consumer.subscribe(
                Collections.singletonList(
                        KafkaConfig.TOPIC
                )
        );

        System.out.println(
                "Consumer started..."
        );

        while (true) {

            var records =
                    consumer.poll(
                            Duration.ofMillis(1000)
                    );

            for (ConsumerRecord<String, String> record : records) {

                try {

                    ShadowBaseMessage message =
                            objectMapper.readValue(
                                    record.value(),
                                    ShadowBaseMessage.class
                            );

                    processingService.process(message);

                } catch (Exception e) {

                    System.out.println(
                            "Error processing message"
                    );

                    e.printStackTrace();
                }
            }
        }
    }

    public void close() {
        consumer.close();
    }
}