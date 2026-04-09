package com.example.customerservice.service;

import com.example.customerservice.dto.ChatRequest;
import com.example.customerservice.dto.ConversationDto;
import com.example.customerservice.dto.MessageDto;
import com.example.customerservice.entity.Conversation;
import com.example.customerservice.entity.Message;
import com.example.customerservice.repository.ConversationRepository;
import com.example.customerservice.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private DifyService difyService;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
            difyService,
            conversationRepository,
            messageRepository,
            new ObjectMapper(),
            Runnable::run
        );
    }

    @Test
    void sendMessageStreamsChunksAndUpdatesConversationWithReturnedDifyId() {
        Conversation localConversation = new Conversation();
        localConversation.setId(42L);
        localConversation.setUserIdentifier("default-user");
        localConversation.setTitle("hello");

        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) {
                conversation.setId(42L);
            }
            return conversation;
        });
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(localConversation));
        when(difyService.hasApiKey()).thenReturn(true);

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        List<Integer> sendCountsDuringStream = new ArrayList<>();

        doAnswer(invocation -> {
            Consumer<String> lineConsumer = invocation.getArgument(2);
            lineConsumer.accept("event: message");
            lineConsumer.accept("data: {\"event\":\"message\",\"answer\":\"hello \",\"conversation_id\":\"dify-conv-1\"}");
            sendCountsDuringStream.add(emitter.getSendCount());
            lineConsumer.accept("event: message");
            lineConsumer.accept("data: {\"event\":\"message\",\"answer\":\"world\"}");
            sendCountsDuringStream.add(emitter.getSendCount());
            lineConsumer.accept("event: message_end");
            lineConsumer.accept("data: {\"event\":\"message_end\",\"metadata\":{\"citations\":[{\"source\":\"FAQ\",\"content\":\"引用内容\",\"score\":0.88}]}}");
            return null;
        }).when(difyService).streamChatMessage(any(ChatRequest.class), any(), any());

        ChatRequest request = new ChatRequest();
        request.setQuery("hello");
        request.setUserId("default-user");

        chatService.sendMessage(request, emitter);

        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository, times(2)).save(conversationCaptor.capture());

        List<Conversation> savedConversations = conversationCaptor.getAllValues();
        Conversation updatedConversation = savedConversations.get(savedConversations.size() - 1);
        assertEquals("dify-conv-1", updatedConversation.getDifyConversationId());
        assertEquals(List.of(2, 3), sendCountsDuringStream);
        assertEquals(4, emitter.getSendCount());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(2)).save(messageCaptor.capture());
        Message assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals("assistant", assistantMessage.getRole());
        assertEquals("hello world", assistantMessage.getContent());
        assertTrue(assistantMessage.getMetadata().contains("\"source\":\"FAQ\""));
    }

    @Test
    void sendMessageRetriesWithoutStoredDifyIdBeforeAnyAnswerChunkIsEmitted() {
        Conversation localConversation = new Conversation();
        localConversation.setId(52L);
        localConversation.setUserIdentifier("default-user");
        localConversation.setTitle("retry");
        localConversation.setDifyConversationId("stale-dify-id");

        when(conversationRepository.findById(52L)).thenReturn(Optional.of(localConversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(difyService.hasApiKey()).thenReturn(true);

        doAnswer(invocation -> {
            String difyConversationId = invocation.getArgument(1);
            Consumer<String> lineConsumer = invocation.getArgument(2);
            if ("stale-dify-id".equals(difyConversationId)) {
                throw new IllegalStateException("Dify returned status 400: conversation not found");
            }

            lineConsumer.accept("event: message");
            lineConsumer.accept("data: {\"event\":\"message\",\"answer\":\"recovered\",\"conversation_id\":\"new-dify-id\"}");
            return null;
        }).when(difyService).streamChatMessage(any(ChatRequest.class), any(), any());

        ChatRequest request = new ChatRequest();
        request.setConversationId("52");
        request.setQuery("retry");
        request.setUserId("default-user");

        RecordingSseEmitter emitter = new RecordingSseEmitter();
        chatService.sendMessage(request, emitter);

        verify(difyService).streamChatMessage(any(ChatRequest.class), anyString(), any());
        verify(difyService).streamChatMessage(any(ChatRequest.class), isNull(), any());
        assertTrue(emitter.getSendCount() >= 3);
    }

    @Test
    void getMessagesAcceptsLocalConversationIdAndParsesCitations() {
        Conversation conversation = new Conversation();
        conversation.setId(7L);

        Message assistantMessage = new Message();
        assistantMessage.setId(100L);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("answer");
        assistantMessage.setMetadata("{\"citations\":[{\"source\":\"FAQ\",\"content\":\"帮助文档\"}]}");
        assistantMessage.setCreatedAt(LocalDateTime.now());

        when(conversationRepository.findById(7L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(assistantMessage));

        List<MessageDto> messages = chatService.getMessages("7");

        assertEquals(1, messages.size());
        assertEquals("FAQ", messages.get(0).getCitations().get(0).getSource());
        verify(conversationRepository, never()).findByDifyConversationId(anyString());
    }

    @Test
    void createConversationKeepsDifyIdentifierEmptyUntilFirstRemoteReply() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setId(99L);
            return conversation;
        });

        ConversationDto conversation = chatService.createConversation("default-user", "新会话");

        assertEquals(99L, conversation.getId());
        assertNull(conversation.getDifyConversationId());
    }

    private static class RecordingSseEmitter extends SseEmitter {
        private int sendCount = 0;

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            sendCount++;
        }

        int getSendCount() {
            return sendCount;
        }
    }
}
