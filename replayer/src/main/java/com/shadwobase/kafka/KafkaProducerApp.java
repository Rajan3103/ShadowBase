package com.shadwobase.kafka;


import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaProducerApp {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = "shadowbase-events";

    public static void main(String[] args) {

        Properties properties = new Properties();

        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                BOOTSTRAP_SERVERS
        );

        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        Producer<String, String> producer =
                new KafkaProducer<>(properties);

        try {

            String key = "event-1";
            String message = "Hello from ShadowBase";

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            TOPIC_NAME,
                            key,
                            message
                    );

            producer.send(record, (metadata, exception) -> {

                if (exception == null) {

                    System.out.println("Message sent successfully!");

                    System.out.println(
                            "Topic: " + metadata.topic()
                    );

                    System.out.println(
                            "Partition: " + metadata.partition()
                    );

                    System.out.println(
                            "Offset: " + metadata.offset()
                    );

                } else {

                    System.out.println(
                            "Failed to send message: "
                                    + exception.getMessage()
                    );
                }
            });

        } finally {

            producer.flush();
            producer.close();
        }
    }
}
    

