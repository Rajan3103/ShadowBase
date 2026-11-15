package com.shadowbase;

import com.shadowbase.kafka.KafkaProducerService;
import com.shadowbase.model.ShadowBaseMessage;

/**
 * Entry point for demonstrating Kafka message production in the ShadowBase replayer.
 */
public class ProducerMain {

    public static void main(String[] args) {
        KafkaProducerService producer = new KafkaProducerService();

        ShadowBaseMessage message = new ShadowBaseMessage(
                101,
                "INSERT",
                "student",
                "name=Rohan, age=20"
        );

        producer.sendMessage(message);
        producer.close();
    }
}