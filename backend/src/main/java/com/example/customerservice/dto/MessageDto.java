package com.example.customerservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public class MessageDto {
    private Long id;
    private String role;
    private String content;
    private String metadata;
    private LocalDateTime createdAt;
    private List<Citation> citations;

    public static class Citation {
        private String source;
        private String content;
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<Citation> getCitations() { return citations; }
    public void setCitations(List<Citation> citations) { this.citations = citations; }
}
