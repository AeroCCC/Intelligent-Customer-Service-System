package com.example.customerservice.dto;

import java.util.List;

public class ChatResponse {
    private String conversationId;
    private String messageId;
    private String answer;
    private String type;
    private List<Citation> citations;

    public static class Citation {
        private String source;
        private String content;
        private Double score;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<Citation> getCitations() { return citations; }
    public void setCitations(List<Citation> citations) { this.citations = citations; }
}
