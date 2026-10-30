package com.creepycode.plan.entity;

/**
 * プランのステータス（PLANS.STATUS）。並び順は進む順（基本設計書 4-2）。
 */
public enum PlanStatus {

    /** 作成中：AI がプランの大枠を作っている（まだできていない）。 */
    CREATING,
    /** AI提案確認中：AI が作ったプランを、顧客が確かめている。 */
    CUSTOMER_DRAFT_REVIEW,
    /** プランナーアサイン中：顧客がプランナーに送った。担当はまだ決まっていない。 */
    PLANNER_ASSIGNING,
    /** プランナー準備中：担当プランナーが詳細プランを作っている。 */
    PLANNER_PREPARING,
    /** 顧客確認中：プランナーが詳細プランを出し、顧客がチャットで確かめている。 */
    CUSTOMER_REVIEWING,
    /** 合意済み：プランが確定した。 */
    AGREED;

    /** プランナーに送った後（プランナーアサイン中より先）かを返す。送った後は AI で作り直さない。 */
    public boolean isSentToPlanner() {
        return compareTo(PLANNER_ASSIGNING) >= 0;
    }
}
