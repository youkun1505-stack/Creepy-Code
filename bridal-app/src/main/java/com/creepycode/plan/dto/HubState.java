package com.creepycode.plan.dto;

/**
 * トップページ（P-01）のハブに出す、顧客の進み具合。
 * stage は段階、nextStepUrl はかんたん質問の途中のときに次に開く STEP の URL（途中でなければ null）、
 * answersChanged はプランの元になった回答より新しい回答があるか（作り直しを勧める）。
 */
public record HubState(Stage stage, String nextStepUrl, boolean answersChanged) {

    /** 顧客の段階（進む順）。 */
    public enum Stage {
        /** まだ何も答えていない。 */
        NEW,
        /** かんたん質問の途中（STEP4 まで答えていない）。 */
        HEARING,
        /** STEP4 まで答えたが、プランはまだ無い。 */
        READY,
        /** プランを作っている途中（作成中のまま。前回うまく作れなかったときも含む）。 */
        CREATING,
        /** AI が作ったプランを確かめている（AI提案確認中）。 */
        DRAFT_REVIEW,
        /** プランナーに送った。担当はまだ決まっていない（プランナーアサイン中）。 */
        SENT,
        /** 担当プランナーが決まった後（プランナー準備中・顧客確認中・合意済み）。 */
        ASSIGNED
    }
}
