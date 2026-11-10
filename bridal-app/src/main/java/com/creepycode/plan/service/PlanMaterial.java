package com.creepycode.plan.service;

/**
 * プランを作る材料。AI に渡す文章の3章のうち、AI が作る前から分かる2章の本文を持つ。
 * confirmedInfo は「確定情報」（STEP1〜4 の回答）、dialogLog は「時系列の対話ログ」（P-10 の会話）の本文。
 * 「価値観・優先順位」の章は AI が作るので、ここには持たない。
 */
public record PlanMaterial(String confirmedInfo, String dialogLog) {

    /** 章の見出し：確定情報 */
    public static final String HEADING_CONFIRMED = "## 確定情報";

    /** 章の見出し：価値観・優先順位 */
    public static final String HEADING_VALUES = "## 価値観・優先順位";

    /** 章の見出し：時系列の対話ログ */
    public static final String HEADING_DIALOG = "## 時系列の対話ログ";

    /** AI に渡すときの「価値観・優先順位」の章の本文（まだ無いので、その旨を書く）。 */
    static final String VALUES_NOT_YET = "（まだありません。このあと整理します）";

    /** AI に渡す文章を返す。3章の形（見出しと本文）にする。 */
    public String toAiInput() {
        return sections(confirmedInfo, VALUES_NOT_YET, dialogLog);
    }

    /** 3章を、見出しを付けて1つの文章にする（AI に渡す文章の形）。 */
    public static String sections(String confirmed, String values, String dialog) {
        return HEADING_CONFIRMED + "\n" + confirmed.strip() + "\n\n"
                + HEADING_VALUES + "\n" + values.strip() + "\n\n"
                + HEADING_DIALOG + "\n" + dialog.strip() + "\n";
    }
}
