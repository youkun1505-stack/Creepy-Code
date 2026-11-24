package com.creepycode.plan.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 料金シミュレーション（P-13）の計算結果。
 * 予定金額の累計、予算残額、予算超過の有無などを保持する。
 */
public class SimulationResultDto {

    // 選択された（または表示対象の）アイテム明細一覧
    private List<SimulationItemDto> items = new ArrayList<>();

    // 合計予定金額（累計、円）
    private long totalAmount;

    // 顧客の予算（円、未設定の場合は null）
    private Integer budget;

    // 予算残額（予算 - 合計金額、予算未設定の場合は null）
    private Long remainingBudget;

    // 予算を超過しているかどうか（予算が設定されており、残額がマイナスの場合に true）
    private boolean overBudget;

    // 予算超過額（超過していなければ 0、超過していれば超過した金額）
    private long overAmount;

    public SimulationResultDto() {
    }

    public SimulationResultDto(List<SimulationItemDto> items, long totalAmount, Integer budget) {
        this.items = items != null ? items : new ArrayList<>();
        this.totalAmount = totalAmount;
        this.budget = budget;
        if (this.budget != null) {
            this.remainingBudget = (long) this.budget - this.totalAmount;
            this.overBudget = this.remainingBudget < 0;
            this.overAmount = this.overBudget ? this.totalAmount - (long) this.budget : 0L;
        } else {
            this.remainingBudget = null;
            this.overBudget = false;
            this.overAmount = 0L;
        }
    }

    public List<SimulationItemDto> getItems() {
        return items;
    }

    public void setItems(List<SimulationItemDto> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getBudget() {
        return budget;
    }

    public void setBudget(Integer budget) {
        this.budget = budget;
    }

    public Long getRemainingBudget() {
        return remainingBudget;
    }

    public void setRemainingBudget(Long remainingBudget) {
        this.remainingBudget = remainingBudget;
    }

    public boolean isOverBudget() {
        return overBudget;
    }

    public void setOverBudget(boolean overBudget) {
        this.overBudget = overBudget;
    }

    public long getOverAmount() {
        return overAmount;
    }

    public void setOverAmount(long overAmount) {
        this.overAmount = overAmount;
    }
}

