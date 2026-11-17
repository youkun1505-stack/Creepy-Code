package com.creepycode.plan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 料金マスタ（T-06 PRICE_MASTER）。
 * 各オプションの基本料金・固定／変動区分を管理する。
 */
@Entity
@Table(name = "price_master")
public class PriceMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "base_price", nullable = false)
    private Integer basePrice;

    // Y = 変動あり（人数等で金額が動く）、N = 固定
    @Column(name = "is_variable", nullable = false, length = 1)
    private String isVariable;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 削除日時（NULL = 有効、日時あり = 論理削除済み）
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 金額が変動するオプション（人数などで金額が変わるもの）かどうかを返す。 */
    public boolean isVariable() {
        return "Y".equalsIgnoreCase(this.isVariable);
    }

    /** 変動区分を boolean で設定する。true = 'Y', false = 'N'。 */
    public void setVariable(boolean variable) {
        this.isVariable = variable ? "Y" : "N";
    }

    /** 論理削除されているかどうかを返す。 */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Integer basePrice) {
        this.basePrice = basePrice;
    }

    public String getIsVariable() {
        return isVariable;
    }

    public void setIsVariable(String isVariable) {
        this.isVariable = isVariable;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

