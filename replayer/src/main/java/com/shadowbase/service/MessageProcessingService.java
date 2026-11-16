package com.shadowbase.service;

import com.shadowbase.model.ShadowBaseMessage;

public class MessageProcessingService {

    private final SqlReplayService sqlReplayService;

    public MessageProcessingService() {
        this.sqlReplayService = new SqlReplayService();
    }

    public void process(ShadowBaseMessage message) {

        System.out.println();
        System.out.println("----- Message Received -----");
        System.out.println("ID        : " + message.getId());
        System.out.println("Operation : " + message.getOperation());
        System.out.println("Table     : " + message.getTable());
        System.out.println("Data      : " + message.getData());
        System.out.println("----------------------------");

        ReplayResult result = sqlReplayService.replay(message);

        if (result.isSuccess()) {

            System.out.println("Message replay completed successfully.");

        } else {

            System.out.println("Message replay failed.");
            System.out.println(
                    "SQL error: " + result.getErrorMessage()
            );
        }
    }
}