
package com.shadwobase.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public class KafkaConsumerApp {

    private static final String TOPIC =
            "shadowbase_mysql.shadowbase_cdc.customers";

    public static void main(String[] args) {

        Properties properties = new Properties();

        properties.put("bootstrap.servers", "localhost:9094");
       properties.put("group.id", "shadowbase-replayer-test");
        properties.put("key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("auto.offset.reset", "earliest");
        properties.put("enable.auto.commit", "false");

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(properties)) {

            consumer.subscribe(Collections.singletonList(TOPIC));

            System.out.println("ShadowBase Kafka Consumer Started");
            System.out.println("Listening to topic: " + TOPIC);

            while (true) {

                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {

                    System.out.println("\nReceived CDC Event");
                    System.out.println("Topic: " + record.topic());
                    System.out.println("Partition: " + record.partition());
                    System.out.println("Offset: " + record.offset());
                    System.out.println("Key: " + record.key());
                    System.out.println("Value: " + record.value());
                }

                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }

        } catch (Exception e) {
            System.err.println("Kafka Consumer Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
