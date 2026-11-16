package com.shadowbase;

import com.shadowbase.kafka.KafkaProducerService;
import com.shadowbase.model.ShadowBaseMessage;

/**
 * Entry point for demonstrating Kafka message production in the ShadowBase replayer.
 */
public class ProducerMain {

    public static void main(String[] args) {
        KafkaProducerService producer = new KafkaProducerService();

        ShadowBaseMessage insertMessage = new ShadowBaseMessage(
                101,
                "INSERT",
                "student",
                "'Rohan', 20"
        );

        producer.sendMessage(insertMessage);
        producer.close();
    }
}
