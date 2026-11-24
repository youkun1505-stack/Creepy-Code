package com.creepycode.plan.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * プランの価格改定判定のサマリー情報。
 * 改定のあるアイテム一覧、旧合計、新合計、差額などを保持する。
 */
public class PriceRevisionSummaryDto {

    private Long planId;
    private boolean agreed;
    private boolean hasRevisions;
    private int revisedItemCount;
    private long totalOldAmount;
    private long totalNewAmount;
    private long totalDiffAmount;
    private List<PriceRevisionItemDto> items = new ArrayList<>();
    private List<PriceRevisionItemDto> revisedItems = new ArrayList<>();

    public PriceRevisionSummaryDto() {
    }

    public PriceRevisionSummaryDto(Long planId, boolean agreed, List<PriceRevisionItemDto> items) {
        this.planId = planId;
        this.agreed = agreed;
        this.items = items != null ? items : new ArrayList<>();

        if (this.agreed) {
            // 合意済みの場合は価格改定の影響を受けない（改定なしとして扱う）
            this.hasRevisions = false;
            this.revisedItemCount = 0;
            this.revisedItems = new ArrayList<>();
            this.totalOldAmount = this.items.stream().mapToLong(PriceRevisionItemDto::getOldSubtotal).sum();
            this.totalNewAmount = this.totalOldAmount;
            this.totalDiffAmount = 0L;
        } else {
            this.revisedItems = this.items.stream()
                    .filter(PriceRevisionItemDto::isRevised)
                    .toList();
            this.hasRevisions = !this.revisedItems.isEmpty();
            this.revisedItemCount = this.revisedItems.size();
            this.totalOldAmount = this.items.stream().mapToLong(PriceRevisionItemDto::getOldSubtotal).sum();
            this.totalNewAmount = this.items.stream().mapToLong(PriceRevisionItemDto::getNewSubtotal).sum();
            this.totalDiffAmount = this.totalNewAmount - this.totalOldAmount;
        }
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public boolean isAgreed() {
        return agreed;
    }

    public void setAgreed(boolean agreed) {
        this.agreed = agreed;
    }

    public boolean isHasRevisions() {
        return hasRevisions;
    }

    public void setHasRevisions(boolean hasRevisions) {
        this.hasRevisions = hasRevisions;
    }

    public int getRevisedItemCount() {
        return revisedItemCount;
    }

    public void setRevisedItemCount(int revisedItemCount) {
        this.revisedItemCount = revisedItemCount;
    }

    public long getTotalOldAmount() {
        return totalOldAmount;
    }

    public void setTotalOldAmount(long totalOldAmount) {
        this.totalOldAmount = totalOldAmount;
    }

    public long getTotalNewAmount() {
        return totalNewAmount;
    }

    public void setTotalNewAmount(long totalNewAmount) {
        this.totalNewAmount = totalNewAmount;
    }

    public long getTotalDiffAmount() {
        return totalDiffAmount;
    }

    public void setTotalDiffAmount(long totalDiffAmount) {
        this.totalDiffAmount = totalDiffAmount;
    }

    public List<PriceRevisionItemDto> getItems() {
        return items;
    }

    public void setItems(List<PriceRevisionItemDto> items) {
        this.items = items;
    }

    public List<PriceRevisionItemDto> getRevisedItems() {
        return revisedItems;
    }

    public void setRevisedItems(List<PriceRevisionItemDto> revisedItems) {
        this.revisedItems = revisedItems;
    }
}
