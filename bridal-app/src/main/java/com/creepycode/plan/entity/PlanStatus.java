package com.creepycode.plan.entity;

/**
 * プランのステータス（PLANS.STATUS）。並び順は進む順（基本設計書 4-2）。
 */
public enum PlanStatus {

    /** 作成中：AI がプランの大枠を作っている（まだできていない）。 */
    CREATING("作成中"),
    /** AI提案確認中：AI が作ったプランを、顧客が確かめている。 */
    CUSTOMER_DRAFT_REVIEW("AI提案確認中"),
    /** プランナーアサイン中：顧客がプランナーに送った。担当はまだ決まっていない。 */
    PLANNER_ASSIGNING("担当プランナーを決めています"),
    /** プランナー準備中：担当プランナーが詳細プランを作っている。 */
    PLANNER_PREPARING("担当プランナーが準備中"),
    /** 顧客確認中：プランナーが詳細プランを出し、顧客がチャットで確かめている。 */
    CUSTOMER_REVIEWING("プランナーの提案を確認中"),
    /** 合意済み：プランが確定した。 */
    AGREED("合意済み");

    private final String label;

    PlanStatus(String label) {
        this.label = label;
    }

    /** 顧客の画面に出す名前を返す（設計書の名前のうち、顧客にわかりにくいものは言い換えている）。 */
    public String getLabel() {
        return label;
    }

    /** プランナーに送った後（プランナーアサイン中より先）かを返す。送った後は AI で作り直さない。 */
    public boolean isSentToPlanner() {
        return compareTo(PLANNER_ASSIGNING) >= 0;
    }
}
