package com.example.customerservice.service;

import com.example.customerservice.config.DifyConfig;
import com.example.customerservice.dto.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class DifyService {

    private final DifyConfig difyConfig;
    private final ObjectMapper objectMapper;

    @Autowired
    public DifyService(DifyConfig difyConfig, ObjectMapper objectMapper) {
        this.difyConfig = difyConfig;
        this.objectMapper = objectMapper;
    }

    public boolean hasApiKey() {
        return difyConfig.getApiKey() != null && !difyConfig.getApiKey().isBlank();
    }

    public void streamChatMessage(ChatRequest request, String difyConversationId, Consumer<String> lineConsumer) {
        Map<String, Object> body = buildRequestBody(request, difyConversationId);

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(difyConfig.getTimeout()))
                .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(difyConfig.getBaseUrl() + "/chat-messages"))
                .timeout(Duration.ofMillis(difyConfig.getTimeout()))
                .header("Authorization", "Bearer " + difyConfig.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                try (InputStream errorStream = response.body()) {
                    String errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    throw new IllegalStateException("Dify returned status " + response.statusCode() + ": " + errorBody);
                }
            }

            try (
                InputStream responseStream = response.body();
                BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))
            ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Dify request failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, String difyConversationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", request.getQuery());
        body.put("response_mode", "streaming");
        body.put("user", request.getUserId() != null ? request.getUserId() : "default");

        if (difyConversationId != null && !difyConversationId.isBlank()) {
            body.put("conversation_id", difyConversationId);
        }

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", request.getQuery());
        inputs.put("files", request.getFiles() != null ? request.getFiles() : new ArrayList<>());
        body.put("inputs", inputs);
        return body;
    }
}
