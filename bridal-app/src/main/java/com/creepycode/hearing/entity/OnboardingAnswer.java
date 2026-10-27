package com.creepycode.hearing.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 事前ヒアリングの回答（T-03 ONBOARDING_ANSWERS）。AI深掘り質問とその回答もここに入る。
 */
@Entity
@Table(name = "onboarding_answers")
public class OnboardingAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long answerId;

    private Long userId;

    private Long archetypeId;

    // Step2 で選んだ画像IDのリスト（JSON形式）
    private String visualSelections;

    // 希望日程（未定の場合は NULL）
    private LocalDate desiredDate;

    // 予算（円単位）
    private Integer budget;

    // 「決められないこと」タグ（カンマ区切り）
    private String undecidedTags;

    // 「こだわりたいこと」タグ（カンマ区切り）
    private String decidedTags;

    // AI深掘り質問と、その回答（JSON形式）
    private String deepdiveQa;

    private LocalDateTime createdAt;

    public Long getAnswerId() {
        return answerId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getArchetypeId() {
        return archetypeId;
    }

    public void setArchetypeId(Long archetypeId) {
        this.archetypeId = archetypeId;
    }

    public String getVisualSelections() {
        return visualSelections;
    }

    public void setVisualSelections(String visualSelections) {
        this.visualSelections = visualSelections;
    }

    public LocalDate getDesiredDate() {
        return desiredDate;
    }

    public void setDesiredDate(LocalDate desiredDate) {
        this.desiredDate = desiredDate;
    }

    public Integer getBudget() {
        return budget;
    }

    public void setBudget(Integer budget) {
        this.budget = budget;
    }

    public String getUndecidedTags() {
        return undecidedTags;
    }

    public void setUndecidedTags(String undecidedTags) {
        this.undecidedTags = undecidedTags;
    }

    public String getDecidedTags() {
        return decidedTags;
    }

    public void setDecidedTags(String decidedTags) {
        this.decidedTags = decidedTags;
    }

    public String getDeepdiveQa() {
        return deepdiveQa;
    }

    public void setDeepdiveQa(String deepdiveQa) {
        this.deepdiveQa = deepdiveQa;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
