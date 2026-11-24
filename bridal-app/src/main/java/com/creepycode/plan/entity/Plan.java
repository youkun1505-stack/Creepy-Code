package com.creepycode.plan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * プラン（T-04 PLANS）。
 * 顧客のプラン情報、ステータス、合意日時などを管理する。
 */
@Entity
@Table(name = "plans")
public class Plan {

    public static final String STATUS_CREATING = "CREATING";
    public static final String STATUS_CUSTOMER_DRAFT_REVIEW = "CUSTOMER_DRAFT_REVIEW";
    public static final String STATUS_PLANNER_ASSIGNING = "PLANNER_ASSIGNING";
    public static final String STATUS_PLANNER_PREPARING = "PLANNER_PREPARING";
    public static final String STATUS_CUSTOMER_REVIEWING = "CUSTOMER_REVIEWING";
    public static final String STATUS_AGREED = "AGREED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "planner_id")
    private Long plannerId;

    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "ai_draft", columnDefinition = "TEXT")
    private String aiDraft;

    @Column(name = "value_summary", columnDefinition = "TEXT")
    private String valueSummary;

    @Column(name = "planner_plan", columnDefinition = "TEXT")
    private String plannerPlan;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Plan() {
    }

    /** プランが合意済みかどうかを判定する。合意済みプランは価格改定の影響を受けない。 */
    public boolean isAgreed() {
        return STATUS_AGREED.equalsIgnoreCase(this.status) || this.agreedAt != null;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
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

    public void setPlannerId(Long plannerId) {
        this.plannerId = plannerId;
    }

    public Long getAnswerId() {
        return answerId;
    }

    public void setAnswerId(Long answerId) {
        this.answerId = answerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public void setPlannerPlan(String plannerPlan) {
        this.plannerPlan = plannerPlan;
    }

    public LocalDateTime getAgreedAt() {
        return agreedAt;
    }

    public void setAgreedAt(LocalDateTime agreedAt) {
        this.agreedAt = agreedAt;
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
