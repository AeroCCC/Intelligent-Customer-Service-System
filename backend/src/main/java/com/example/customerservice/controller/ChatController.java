package com.example.customerservice.controller;

import com.example.customerservice.dto.ChatRequest;
import com.example.customerservice.dto.ConversationDto;
import com.example.customerservice.dto.MessageDto;
import com.example.customerservice.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    @Autowired
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sendMessage(@RequestBody ChatRequest request) {
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            request.setUserId("default-user");
        }

        SseEmitter emitter = new SseEmitter(300000L);
        String emitterId = "emitter_" + System.currentTimeMillis();
        activeEmitters.put(emitterId, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(emitterId));
        emitter.onTimeout(() -> activeEmitters.remove(emitterId));
        emitter.onError(e -> activeEmitters.remove(emitterId));

        try {
            chatService.sendMessage(request, emitter);
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + e.getMessage() + "\"}"));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.setCacheControl("no-cache");
        headers.add("X-Accel-Buffering", "no");
        headers.add("Connection", "keep-alive");

        return ResponseEntity.ok()
            .headers(headers)
            .body(emitter);
    }

    @GetMapping("/history/{conversationId}")
    public Map<String, Object> getHistory(@PathVariable String conversationId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<MessageDto> messages = chatService.getMessages(conversationId);
            result.put("success", true);
            result.put("data", messages);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/conversations")
    public Map<String, Object> getConversations(@RequestParam(required = false) String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ConversationDto> conversations = chatService.getConversations(userId);
            result.put("success", true);
            result.put("data", conversations);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/new")
    public Map<String, Object> createConversation(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = body.getOrDefault("userId", "default-user");
            String title = body.getOrDefault("title", "新会话");
            ConversationDto conversation = chatService.createConversation(userId, title);
            result.put("success", true);
            result.put("data", conversation);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("service", "customer-service");
        return result;
    }
}
