package com.creepycode.hearing.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * AI相談チャット（P-10）の発言（AI_CHAT_MESSAGES）。利用者の質問（ROLE = USER）とAIの返事（ROLE = AI）を1発言ずつ保存する。
 */
@Entity
@Table(name = "ai_chat_messages")
public class AiChatMessage {

    /** 発言者：利用者の質問 */
    public static final String ROLE_USER = "USER";

    /** 発言者：AI の返事 */
    public static final String ROLE_AI = "AI";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    // 相談の持ち主（USERS.USER_ID）
    private Long userId;

    // 発言者（USER / AI）
    private String role;

    // 発言の本文
    private String content;

    private LocalDateTime createdAt;

    public Long getMessageId() {
        return messageId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
