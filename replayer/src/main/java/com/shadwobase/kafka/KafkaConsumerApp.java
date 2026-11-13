package com.shadwobase.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
public class KafkaConsumerApp {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = "shadowbase-events";
    private static final String GROUP_ID = "shadowbase-replayer-group";

    public static void main(String[] args) {

        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                BOOTSTRAP_SERVERS
        );

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                GROUP_ID
        );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        Consumer<String, String> consumer =
                new KafkaConsumer<>(properties);

        consumer.subscribe(
                Collections.singletonList(TOPIC_NAME)
        );

        System.out.println(
                "Kafka Consumer started..."
        );

        System.out.println(
                "Waiting for messages..."
        );

        try {

            while (true) {

                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {

                    System.out.println();
                    System.out.println("-----------------------------");

                    System.out.println(
                            "Message received:"
                    );

                    System.out.println(
                            "Key: " + record.key()
                    );

                    System.out.println(
                            "Value: " + record.value()
                    );

                    System.out.println(
                            "Topic: " + record.topic()
                    );

                    System.out.println(
                            "Partition: " + record.partition()
                    );

                    System.out.println(
                            "Offset: " + record.offset()
                    );

                    System.out.println(
                            "-----------------------------"
                    );
                }
            }

        } finally {

            consumer.close();
        }
    }

    
}

