package com.shadowbase.service;
import com.shadowbase.model.ShadowBaseMessage;

public class MessageProcessingService {


        public void process(ShadowBaseMessage message) {

            System.out.println("\n----- Message Received -----");

            System.out.println("ID        : " + message.getId());

            System.out.println("Operation  : " + message.getOperation());

            System.out.println("Table      : " + message.getTable());

            System.out.println("Data       : " + message.getData());

            System.out.println("----------------------------");
        }
}

