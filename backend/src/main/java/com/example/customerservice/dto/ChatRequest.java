package com.example.customerservice.dto;

import java.util.List;
import java.util.Map;

public class ChatRequest {
    private String query;
    private String conversationId;
    private String userId;
    private List<Map<String, Object>> files;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<Map<String, Object>> getFiles() { return files; }
    public void setFiles(List<Map<String, Object>> files) { this.files = files; }
}
