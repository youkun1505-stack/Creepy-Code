package com.creepycode.plan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 会話履歴（T-15 CONVERSATION_LOGS）。AI との対話を書いた md ファイルの保存場所と、顧客・プランへの紐付け。
 * ファイル本体は保存先フォルダ（app.storage.dir）にある。1プランにつき1行。
 */
@Entity
@Table(name = "conversation_logs")
public class ConversationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    private Long userId;

    private Long planId;

    // 原文 md の保存場所（保存先フォルダからの相対パス。PC ごとに保存先フォルダの場所が違うため）
    private String fileUrl;

    // 送信用の圧縮版 md の保存場所（作っていないので NULL）
    private String summaryUrl;

    private String fileName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getLogId() {
        return logId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getSummaryUrl() {
        return summaryUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
