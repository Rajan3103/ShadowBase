package com.shadowbase.service;

import com.shadowbase.model.ShadowBaseMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MessageProcessingService {

    private final SqlReplayService sqlReplayService;

    private final HttpClient httpClient;

    private static final String METRICS_URL =
            "http://localhost:8080/api/metrics/record";

    public MessageProcessingService() {

        sqlReplayService = new SqlReplayService();

        httpClient = HttpClient.newHttpClient();
    }

    public void process(ShadowBaseMessage message) {

        System.out.println("\n----- Message Received -----");

        System.out.println("ID        : " + message.getId());
        System.out.println("Sandbox ID: " + message.getSandboxId());
        System.out.println("Operation : " + message.getOperation());
        System.out.println("Table     : " + message.getTable());
        System.out.println("Data      : " + message.getData());

        System.out.println("----------------------------");

        ReplayResult result = sqlReplayService.replay(message);

        sendMetricsToBackend(message, result);
    }

    private void sendMetricsToBackend(
            ShadowBaseMessage message,
            ReplayResult result) {

        String json = String.format(
                """
                {
                    "success": %s,
                    "table": "%s",
                    "operation": "%s",
                    "sqlState": "%s",
                    "message": "%s"
                }
                """,
                result.isSuccess(),
                escapeJson(message.getTable()),
                escapeJson(message.getOperation()),
                result.isSuccess() ? "" : "REPLAY_ERROR",
                escapeJson(
                        result.isSuccess()
                                ? "Replay successful"
                                : result.getErrorMessage()
                )
        );

        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(METRICS_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Backend metrics response : "
                            + response.statusCode()
            );

        } catch (Exception exception) {

            System.out.println(
                    "Could not send metrics to backend: "
                            + exception.getMessage()
            );
        }
    }

    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}