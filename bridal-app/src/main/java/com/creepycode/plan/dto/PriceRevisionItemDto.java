package com.creepycode.plan.dto;

/**
 * 価格改定がある各アイテム（オプション）の明細情報。
 * 旧単価、最新単価、および差額を保持する。
 */
public class PriceRevisionItemDto {

    private Long optionId;
    private Long itemId;
    private String itemName;
    private String category;
    private Integer quantity;
    private Integer oldUnitPrice;
    private Integer newUnitPrice;
    private int unitPriceDiff;
    private long oldSubtotal;
    private long newSubtotal;
    private long subtotalDiff;
    private boolean revised;
    private boolean variable;

    public PriceRevisionItemDto() {
    }

    public PriceRevisionItemDto(Long optionId, Long itemId, String itemName, String category,
            Integer quantity, Integer oldUnitPrice, Integer newUnitPrice, boolean variable) {
        this.optionId = optionId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.category = category;
        this.quantity = quantity != null ? quantity : 0;
        this.oldUnitPrice = oldUnitPrice != null ? oldUnitPrice : 0;
        this.newUnitPrice = newUnitPrice != null ? newUnitPrice : 0;
        this.variable = variable;

        this.unitPriceDiff = this.newUnitPrice - this.oldUnitPrice;
        this.oldSubtotal = (long) this.oldUnitPrice * this.quantity;
        this.newSubtotal = (long) this.newUnitPrice * this.quantity;
        this.subtotalDiff = this.newSubtotal - this.oldSubtotal;
        this.revised = this.oldUnitPrice != this.newUnitPrice;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getOldUnitPrice() {
        return oldUnitPrice;
    }

    public void setOldUnitPrice(Integer oldUnitPrice) {
        this.oldUnitPrice = oldUnitPrice;
    }

    public Integer getNewUnitPrice() {
        return newUnitPrice;
    }

    public void setNewUnitPrice(Integer newUnitPrice) {
        this.newUnitPrice = newUnitPrice;
    }

    public int getUnitPriceDiff() {
        return unitPriceDiff;
    }

    public void setUnitPriceDiff(int unitPriceDiff) {
        this.unitPriceDiff = unitPriceDiff;
    }

    public long getOldSubtotal() {
        return oldSubtotal;
    }

    public void setOldSubtotal(long oldSubtotal) {
        this.oldSubtotal = oldSubtotal;
    }

    public long getNewSubtotal() {
        return newSubtotal;
    }

    public void setNewSubtotal(long newSubtotal) {
        this.newSubtotal = newSubtotal;
    }

    public long getSubtotalDiff() {
        return subtotalDiff;
    }

    public void setSubtotalDiff(long subtotalDiff) {
        this.subtotalDiff = subtotalDiff;
    }

    public boolean isRevised() {
        return revised;
    }

    public void setRevised(boolean revised) {
        this.revised = revised;
    }

    public boolean isVariable() {
        return variable;
    }

    public void setVariable(boolean variable) {
        this.variable = variable;
    }
}
