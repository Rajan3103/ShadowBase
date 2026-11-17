package com.shadowbase.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadowbase.model.ShadowBaseMessage;
import com.shadowbase.service.MessageProcessingService;

/**
 * Service responsible for continuously consuming and deserializing Kafka messages.
 * <p>
 * Subscribes to the configured topic and delegates each {@link ShadowBaseMessage}
 * to the {@link MessageProcessingService} for replay / processing.
 */
public class KafkaConsumerService {

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final MessageProcessingService processingService;

    /**
     * Initializes the consumer client configurations and message processing service.
     */
    public KafkaConsumerService() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", KafkaConfig.BOOTSTRAP_SERVERS);
        properties.put("group.id", KafkaConfig.GROUP_ID);
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("auto.offset.reset", "earliest");

        this.consumer = new KafkaConsumer<>(properties);
        this.objectMapper = new ObjectMapper();
        this.processingService = new MessageProcessingService();
    }

    /**
     * Subscribes to the topic and starts an infinite polling loop to process incoming messages.
     */
    public void start() {
        consumer.subscribe(Collections.singletonList(KafkaConfig.TOPIC));
        System.out.println("Consumer started...");

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    ShadowBaseMessage message = objectMapper.readValue(
                            record.value(),
                            ShadowBaseMessage.class
                    );
                    processingService.process(message);
                } catch (Exception e) {
                    System.out.println("Error processing message");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Closes the Kafka consumer connection cleanly.
     */
    public void close() {
        consumer.close();
    }
}