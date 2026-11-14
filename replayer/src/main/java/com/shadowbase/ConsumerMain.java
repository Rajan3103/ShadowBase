package com.shadowbase;



import com.shadowbase.kafka.KafkaConsumerService;

public class ConsumerMain {

    public static void main(String[] args) {

        KafkaConsumerService consumer = new KafkaConsumerService();

        consumer.start();
    }
}
