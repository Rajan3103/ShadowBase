package com.shadowbase.main;

import com.shadowbase.kafka.KafkaProducerService;
import com.shadowbase.model.ShadowBaseMessage;

public class ProducerMain {

    public static void main(String[] args) {

        KafkaProducerService producer = new KafkaProducerService();

        String sandboxId = "073a3b7a";

        ShadowBaseMessage insertMessage =
                new ShadowBaseMessage(
                        101,
                        "INSERT",
                        "student",
                        "101, 'Rohan', 20",
                        sandboxId
                );

        producer.sendMessage(insertMessage);

        producer.close();
    }
}