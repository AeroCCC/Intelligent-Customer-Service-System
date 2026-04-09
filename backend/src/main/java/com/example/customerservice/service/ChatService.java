package com.example.customerservice.service;

import com.example.customerservice.dto.ChatRequest;
import com.example.customerservice.dto.ChatResponse;
import com.example.customerservice.dto.ConversationDto;
import com.example.customerservice.dto.MessageDto;
import com.example.customerservice.entity.Conversation;
import com.example.customerservice.entity.Message;
import com.example.customerservice.repository.ConversationRepository;
import com.example.customerservice.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final DifyService difyService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final Executor chatTaskExecutor;

    public ChatService(
        DifyService difyService,
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        ObjectMapper objectMapper,
        Executor chatTaskExecutor
    ) {
        this.difyService = difyService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.chatTaskExecutor = chatTaskExecutor;
    }

    public SseEmitter sendMessage(ChatRequest request, SseEmitter clientEmitter) {
        Conversation conversation = findConversation(request.getConversationId()).orElse(null);
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setUserIdentifier(request.getUserId() != null ? request.getUserId() : "default");
            String query = request.getQuery();
            conversation.setTitle(query != null && query.length() > 50 ? query.substring(0, 50) : (query != null ? query : "新会话"));
            conversation = conversationRepository.save(conversation);
        }

        Long localConversationId = conversation.getId();
        String existingDifyConversationId = conversation.getDifyConversationId();

        Message userMessage = new Message();
        userMessage.setConversationId(localConversationId);
        userMessage.setRole("user");
        userMessage.setContent(request.getQuery());
        userMessage.setMetadata("{}");
        messageRepository.save(userMessage);

        final Long finalConversationId = localConversationId;
        final String finalExistingDifyConversationId = existingDifyConversationId;

        chatTaskExecutor.execute(() -> {
            try {
                if (!difyService.hasApiKey()) {
                    throw new IllegalStateException("Dify API key is not configured");
                }

                Map<String, Object> statusData = new HashMap<>();
                statusData.put("type", "status");
                statusData.put("status", "processing");
                statusData.put("message", "正在调用 Dify 工作流...");
                clientEmitter.send(SseEmitter.event()
                    .name("status")
                    .data(objectMapper.writeValueAsString(statusData)));

                StringBuilder fullAnswer = new StringBuilder();
                String[] conversationHolder = new String[]{finalExistingDifyConversationId};
                String[] currentEventHolder = new String[]{""};
                boolean[] emittedAnswerChunk = new boolean[]{false};
                List<ChatResponse.Citation> citations = new ArrayList<>();

                streamWithRetry(
                    request,
                    finalExistingDifyConversationId,
                    line -> processStreamLine(
                        line,
                        currentEventHolder,
                        conversationHolder,
                        fullAnswer,
                        citations,
                        emittedAnswerChunk,
                        clientEmitter
                    ),
                    () -> emittedAnswerChunk[0]
                );

                syncConversationIdentifier(finalConversationId, conversationHolder[0]);

                Map<String, Object> endData = new HashMap<>();
                endData.put("type", "end");
                endData.put("conversationId", String.valueOf(finalConversationId));
                endData.put("difyConversationId", conversationHolder[0] != null ? conversationHolder[0] : "");
                endData.put("answer", fullAnswer.toString());
                endData.put("citations", citations);
                clientEmitter.send(SseEmitter.event()
                    .name("message_end")
                    .data(objectMapper.writeValueAsString(endData)));

                Message aiMessage = new Message();
                aiMessage.setConversationId(finalConversationId);
                aiMessage.setRole("assistant");
                aiMessage.setContent(fullAnswer.toString());
                aiMessage.setMetadata("{\"citations\":" + objectMapper.writeValueAsString(citations) + "}");
                messageRepository.save(aiMessage);

                clientEmitter.complete();
            } catch (Exception e) {
                System.out.println("Dify API call failed: " + e.getMessage());
                e.printStackTrace();
                try {
                    Map<String, Object> errorPayload = new HashMap<>();
                    errorPayload.put("error", e.getMessage());
                    clientEmitter.send(SseEmitter.event()
                        .name("error")
                        .data(objectMapper.writeValueAsString(errorPayload)));
                } catch (Exception ignored) {
                }
                clientEmitter.completeWithError(e);
            }
        });

        return clientEmitter;
    }

    private void processStreamLine(
        String rawLine,
        String[] currentEventHolder,
        String[] conversationHolder,
        StringBuilder fullAnswer,
        List<ChatResponse.Citation> citations,
        boolean[] emittedAnswerChunk,
        SseEmitter clientEmitter
    ) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return;
        }

        if (line.startsWith("event:")) {
            currentEventHolder[0] = line.substring(6).trim();
            return;
        }

        if (!line.startsWith("data:")) {
            return;
        }

        String data = line.substring(5).trim();
        if (data.equals("[DONE]") || !data.startsWith("{")) {
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(data);
            String eventType = node.has("event") ? node.get("event").asText() : currentEventHolder[0];

            if ("message".equals(eventType) || "message_end".equals(eventType) || "agent_message".equals(eventType)) {
                if (node.has("answer")) {
                    String answerText = node.get("answer").asText();
                    fullAnswer.append(answerText);
                    emittedAnswerChunk[0] = true;

                    Map<String, Object> chunkPayload = new HashMap<>();
                    chunkPayload.put("type", "message");
                    chunkPayload.put("content", answerText);
                    clientEmitter.send(SseEmitter.event()
                        .name("message")
                        .data(objectMapper.writeValueAsString(chunkPayload)));
                }

                if (node.has("conversation_id") && !node.get("conversation_id").isNull()) {
                    conversationHolder[0] = node.get("conversation_id").asText();
                }

                if (node.has("metadata") && node.get("metadata").has("citations")) {
                    citations.clear();
                    for (JsonNode citation : node.get("metadata").get("citations")) {
                        ChatResponse.Citation item = new ChatResponse.Citation();
                        item.setSource(citation.path("source").asText(""));
                        item.setContent(citation.path("content").asText(""));
                        if (citation.has("score")) {
                            item.setScore(citation.get("score").asDouble());
                        }
                        citations.add(item);
                    }
                }
            }
        } catch (Exception parseError) {
            System.out.println("Parse SSE data failed: " + parseError.getMessage());
        }
    }

    private void streamWithRetry(
        ChatRequest request,
        String difyConversationId,
        Consumer<String> lineConsumer,
        BooleanSupplier hasEmittedAnswer
    ) {
        try {
            difyService.streamChatMessage(request, difyConversationId, lineConsumer);
        } catch (Exception firstError) {
            if (difyConversationId == null || difyConversationId.isBlank() || hasEmittedAnswer.getAsBoolean()) {
                throw firstError;
            }

            System.out.println("Retrying Dify request without stored conversation id after failure: " + firstError.getMessage());
            difyService.streamChatMessage(request, null, lineConsumer);
        }
    }

    Optional<Conversation> findConversation(String conversationIdentifier) {
        if (conversationIdentifier == null || conversationIdentifier.isBlank()) {
            return Optional.empty();
        }

        if (conversationIdentifier.chars().allMatch(Character::isDigit)) {
            try {
                return conversationRepository.findById(Long.parseLong(conversationIdentifier));
            } catch (NumberFormatException ignored) {
            }
        }

        return conversationRepository.findByDifyConversationId(conversationIdentifier);
    }

    private void syncConversationIdentifier(Long localConversationId, String difyConversationId) {
        if (difyConversationId == null || difyConversationId.isBlank()) {
            return;
        }

        conversationRepository.findById(localConversationId).ifPresent(conversation -> {
            if (!difyConversationId.equals(conversation.getDifyConversationId())) {
                conversation.setDifyConversationId(difyConversationId);
                conversationRepository.save(conversation);
            }
        });
    }

    private List<MessageDto.Citation> parseCitations(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return List.of();
        }

        try {
            JsonNode metadataNode = objectMapper.readTree(metadata);
            JsonNode citationNodes = metadataNode.get("citations");
            if (citationNodes == null || !citationNodes.isArray()) {
                return List.of();
            }

            List<MessageDto.Citation> citations = new ArrayList<>();
            for (JsonNode citationNode : citationNodes) {
                MessageDto.Citation citation = new MessageDto.Citation();
                citation.setSource(citationNode.path("source").asText(""));
                citation.setContent(citationNode.path("content").asText(""));
                citations.add(citation);
            }
            return citations;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public List<MessageDto> getMessages(String conversationIdentifier) {
        List<MessageDto> result = new ArrayList<>();
        Optional<Conversation> conversation = findConversation(conversationIdentifier);
        if (conversation.isEmpty()) {
            return result;
        }

        for (Message msg : messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.get().getId())) {
            MessageDto dto = new MessageDto();
            dto.setId(msg.getId());
            dto.setRole(msg.getRole());
            dto.setContent(msg.getContent());
            dto.setMetadata(msg.getMetadata());
            dto.setCreatedAt(msg.getCreatedAt());
            dto.setCitations(parseCitations(msg.getMetadata()));
            result.add(dto);
        }
        return result;
    }

    public List<ConversationDto> getConversations(String userId) {
        List<Conversation> list = userId != null
            ? conversationRepository.findAll().stream()
                .filter(c -> userId.equals(c.getUserIdentifier()))
                .collect(Collectors.toList())
            : conversationRepository.findAll();

        return list.stream().map(conv -> {
            ConversationDto dto = new ConversationDto();
            dto.setId(conv.getId());
            dto.setDifyConversationId(conv.getDifyConversationId());
            dto.setTitle(conv.getTitle());
            dto.setCreatedAt(conv.getCreatedAt());
            dto.setUpdatedAt(conv.getUpdatedAt());
            dto.setMessageCount(messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).size());
            return dto;
        }).sorted(Comparator.comparing(ConversationDto::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .collect(Collectors.toList());
    }

    @Transactional
    public ConversationDto createConversation(String userId, String title) {
        Conversation conv = new Conversation();
        conv.setUserIdentifier(userId != null ? userId : "default");
        conv.setTitle(title != null ? title : "新会话");
        conv = conversationRepository.save(conv);

        ConversationDto dto = new ConversationDto();
        dto.setId(conv.getId());
        dto.setDifyConversationId(conv.getDifyConversationId());
        dto.setTitle(conv.getTitle());
        dto.setCreatedAt(conv.getCreatedAt());
        dto.setUpdatedAt(conv.getUpdatedAt());
        dto.setMessageCount(0);
        return dto;
    }
}
