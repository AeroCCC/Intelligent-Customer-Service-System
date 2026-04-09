package com.example.customerservice.dto;

import java.time.LocalDateTime;

public class ConversationDto {
    private Long id;
    private String difyConversationId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer messageCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDifyConversationId() { return difyConversationId; }
    public void setDifyConversationId(String difyConversationId) { this.difyConversationId = difyConversationId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
}
