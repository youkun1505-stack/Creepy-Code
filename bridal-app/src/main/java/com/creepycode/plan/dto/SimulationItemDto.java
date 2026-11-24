package com.creepycode.plan.dto;

/**
 * 料金シミュレーション（P-13）で選択された各アイテムの明細。
 */
public class SimulationItemDto {

    private Long itemId;
    private String itemName;
    private String category;
    private Integer unitPrice;
    private Integer quantity;
    private boolean variable;
    private long subtotal;

    public SimulationItemDto() {
    }

    public SimulationItemDto(Long itemId, String itemName, String category,
            Integer unitPrice, Integer quantity, boolean variable) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.category = category;
        this.unitPrice = unitPrice;
        this.quantity = quantity != null && quantity >= 0 ? quantity : 0;
        this.variable = variable;
        this.subtotal = (long) (this.unitPrice != null ? this.unitPrice : 0) * this.quantity;
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

    public Integer getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Integer unitPrice) {
        this.unitPrice = unitPrice;
        recalculateSubtotal();
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity != null && quantity >= 0 ? quantity : 0;
        recalculateSubtotal();
    }

    public boolean isVariable() {
        return variable;
    }

    public void setVariable(boolean variable) {
        this.variable = variable;
    }

    public long getSubtotal() {
        return subtotal;
    }

    private void recalculateSubtotal() {
        int price = this.unitPrice != null ? this.unitPrice : 0;
        int qty = this.quantity != null ? this.quantity : 0;
        this.subtotal = (long) price * qty;
    }
}

