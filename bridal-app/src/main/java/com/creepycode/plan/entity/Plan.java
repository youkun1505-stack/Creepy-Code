package com.creepycode.plan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * プラン（T-04 PLANS）。AI が作ったプランの大枠・価値観の要点・プランナーの詳細プラン・ステータス。
 */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long planId;

    // このプランの顧客（USERS.USER_ID）
    private Long userId;

    // 担当プランナー（割り当て前は NULL）
    private Long plannerId;

    // このプランの元になった回答（ONBOARDING_ANSWERS.ANSWER_ID。1回答につき1プラン）
    private Long answerId;

    @Enumerated(EnumType.STRING)
    private PlanStatus status;

    // AI が作ったプランの大枠（JSON。11項目の提案と理由）
    private String aiDraft;

    // AI が整理した価値観の要点（JSON。大事にしていること・削ってもよい部分・削ってよい順）
    private String valueSummary;

    // プランナーが作った詳細プラン
    private String plannerPlan;

    // 合意済みになった日時（未合意は NULL）
    private LocalDateTime agreedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getPlanId() {
        return planId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPlannerId() {
        return plannerId;
    }

    public Long getAnswerId() {
        return answerId;
    }

    public void setAnswerId(Long answerId) {
        this.answerId = answerId;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public String getAiDraft() {
        return aiDraft;
    }

    public void setAiDraft(String aiDraft) {
        this.aiDraft = aiDraft;
    }

    public String getValueSummary() {
        return valueSummary;
    }

    public void setValueSummary(String valueSummary) {
        this.valueSummary = valueSummary;
    }

    public String getPlannerPlan() {
        return plannerPlan;
    }

    public LocalDateTime getAgreedAt() {
        return agreedAt;
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
