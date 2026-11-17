package com.shadowbase.main;

import com.shadowbase.kafka.KafkaConsumerService;

/**
 * Entry point for demonstrating Kafka message consumption in the ShadowBase replayer.
 */
public class ConsumerMain {

    public static void main(String[] args) {
        KafkaConsumerService consumer = new KafkaConsumerService();
        consumer.start();
    }
}
