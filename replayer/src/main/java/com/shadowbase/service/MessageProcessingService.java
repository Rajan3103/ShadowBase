package com.shadowbase.service;

import com.shadowbase.model.ShadowBaseMessage;

public class MessageProcessingService {

    private final SqlReplayService sqlReplayService;

    public MessageProcessingService() {
        sqlReplayService = new SqlReplayService();
    }

    public void process(ShadowBaseMessage message) {

        System.out.println("\n----- Message Received -----");

        System.out.println("ID        : " + message.getId());
        System.out.println("Sandbox ID: " + message.getSandboxId());
        System.out.println("Operation : " + message.getOperation());
        System.out.println("Table     : " + message.getTable());
        System.out.println("Data      : " + message.getData());

        System.out.println("----------------------------");

        sqlReplayService.replay(message);
    }
}