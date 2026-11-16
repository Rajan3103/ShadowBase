package com.shadowbase.service;

import com.shadowbase.model.ShadowBaseMessage;

/**
 * Service that processes and outputs received ShadowBase messages.
 * <p>
 * Replays incoming SQL operations against target shadow database instances.
 */
public class MessageProcessingService {

    private final SqlReplayService sqlReplayService;

    public MessageProcessingService() {
        this.sqlReplayService = new SqlReplayService();
    }

    /**
     * Processes a single received database change event message and replays the SQL.
     *
     * @param message the received {@link ShadowBaseMessage} to process
     */
    public void process(ShadowBaseMessage message) {
        System.out.println("\n----- Message Received -----");
        System.out.println("ID        : " + message.getId());
        System.out.println("Operation : " + message.getOperation());
        System.out.println("Table     : " + message.getTable());
        System.out.println("Data      : " + message.getData());
        System.out.println("----------------------------");

        sqlReplayService.replay(message);
    }
}
