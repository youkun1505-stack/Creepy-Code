package com.creepycode.plan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * プランに含まれるオプション明細（T-05 PLAN_OPTIONS）。
 * 保存時点での単価（UNIT_PRICE）を保持し、料金マスタ改定時は差分から改定を判定する。
 */
@Entity
@Table(name = "plan_options")
public class PlanOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    @Column(name = "is_variable", nullable = false, length = 1)
    private String isVariable;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PlanOption() {
    }

    public PlanOption(Long planId, Long itemId, Integer quantity, Integer unitPrice, boolean variable) {
        this.planId = planId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.isVariable = variable ? "Y" : "N";
        this.createdAt = LocalDateTime.now();
    }

    /** 変動アイテムかどうか（人数等で金額が変わるもの） */
    public boolean isVariable() {
        return "Y".equalsIgnoreCase(this.isVariable);
    }

    public void setVariable(boolean variable) {
        this.isVariable = variable ? "Y" : "N";
    }

    /** 小計（単価 × 数量） */
    public long getSubtotal() {
        return (long) (this.unitPrice != null ? this.unitPrice : 0) * (this.quantity != null ? this.quantity : 0);
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Integer unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getIsVariable() {
        return isVariable;
    }

    public void setIsVariable(String isVariable) {
        this.isVariable = isVariable;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
