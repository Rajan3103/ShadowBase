package com.shadowbase.kafka;

/**
 * Central configuration constants for Kafka producers and consumers in ShadowBase replayer.
 */
public class KafkaConfig {

    /**
     * Comma-separated list of Kafka broker host-port pairs.
     */
    public static final String BOOTSTRAP_SERVERS = "localhost:9092";

    /**
     * Default Kafka topic used for broadcasting database CDC and change-events.
     */
    public static final String TOPIC = "shadowbase-topic";

    /**
     * Default consumer group identifier for scaling consumer replicas.
     */
    public static final String GROUP_ID = "shadowbase-consumer-group";
}
