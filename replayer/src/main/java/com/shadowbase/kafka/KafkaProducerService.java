package com.shadowbase.kafka;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadowbase.model.ShadowBaseMessage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

public class KafkaProducerService {

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public KafkaProducerService() {

        Properties properties = new Properties();

        properties.put(
                "bootstrap.servers",
                KafkaConfig.BOOTSTRAP_SERVERS
        );

        properties.put(
                "key.serializer",
                "org.apache.kafka.common.serialization.StringSerializer"
        );

        properties.put(
                "value.serializer",
                "org.apache.kafka.common.serialization.StringSerializer"
        );

        producer = new KafkaProducer<>(properties);
        objectMapper = new ObjectMapper();
    }

    public void sendMessage(ShadowBaseMessage message) {

        try {

            String jsonMessage =
                    objectMapper.writeValueAsString(message);

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            KafkaConfig.TOPIC,
                            String.valueOf(message.getId()),
                            jsonMessage
                    );

            producer.send(record, (metadata, exception) -> {

                if (exception == null) {
                    System.out.println(
                            "Message sent successfully"
                    );
                    System.out.println(
                            "Topic: " + metadata.topic()
                    );
                    System.out.println(
                            "Partition: " + metadata.partition()
                    );
                } else {
                    System.out.println(
                            "Failed to send message"
                    );
                    exception.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        producer.close();
    }
}