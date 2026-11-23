package com.shadowbase.kafka;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadowbase.model.ShadowBaseMessage;

/**
 * Service responsible for publishing database change events to Apache Kafka.
 * <p>
 * Serializes {@link ShadowBaseMessage} objects to JSON and dispatches them
 * asynchronously with completion callbacks.
 */
public class KafkaProducerService {

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    /**
     * Initializes Kafka producer client properties and ObjectMapper.
     */
    public KafkaProducerService() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", KafkaConfig.BOOTSTRAP_SERVERS);
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        this.producer = new KafkaProducer<>(properties);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Serializes and publishes a {@link ShadowBaseMessage} to the configured Kafka topic.
     *
     * @param message the structured database event to send
     */
    public void sendMessage(ShadowBaseMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaConfig.TOPIC,
                    String.valueOf(message.getId()),
                    jsonMessage
            );

            producer.send(record, (metadata, exception) -> {

                if (exception == null) {

                    System.out.println("\n----- Kafka Message Sent -----");

                    System.out.println("Sandbox ID : " + message.getSandboxId());
                    System.out.println("Message ID : " + message.getId());
                    System.out.println("Operation  : " + message.getOperation());
                    System.out.println("Topic      : " + metadata.topic());
                    System.out.println("Partition  : " + metadata.partition());
                    System.out.println("Offset     : " + metadata.offset());

                } else {

                    System.out.println("Failed to send message");
                    exception.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Flushes buffered records and closes the Kafka producer client.
     */
    public void close() {
        producer.close();
    }
}
