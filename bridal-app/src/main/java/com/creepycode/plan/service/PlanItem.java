package com.creepycode.plan.service;

/**
 * AI が作るプランの大枠（AI_DRAFT）の11項目。並び順は画面に並べる順で、AI にもこの順で書かせる。
 * key は AI_DRAFT の JSON に入れる名前なので、変えるときは保存済みのプランも読めるか確かめる。
 * 項目は STEP4 のタグ（HearingTag）の話題をすべて受け止められるように選んである（1対1ではない。
 * 例：「招待する人の範囲」は「ゲスト」、「家族への感謝」は「演出」の項目で受け止める）。
 */
public enum PlanItem {

    CONCEPT("concept", "コンセプト", "式全体のテーマ"),
    CEREMONY("ceremony", "挙式スタイル", "教会式・人前式・神前式など"),
    VENUE("venue", "会場", "会場の種類や雰囲気（式場名は書かない）"),
    GUESTS("guests", "ゲスト", "招待する範囲と人数の目安"),
    FOOD("food", "料理", "料理のスタイルや飲み物"),
    ATTIRE("attire", "衣装", "ドレス・タキシード・和装など"),
    FLOWERS("flowers", "装花・装飾", "花や飾りつけの色・雰囲気"),
    PROGRAM("program", "演出", "入場・余興・映像など。家族への感謝の場面を含む"),
    PHOTO("photo", "写真・映像", "写真や動画の残し方"),
    GIFTS("gifts", "引出物・おもてなし", "引出物と、ゲストが過ごしやすい工夫"),
    BUDGET("budget", "予算の配分の目安", "どこにかけて、どこをおさえるか");

    private final String key;
    private final String label;
    private final String hint;

    PlanItem(String key, String label, String hint) {
        this.key = key;
        this.label = label;
        this.hint = hint;
    }

    /** AI_DRAFT の JSON に入れる名前を返す。 */
    public String getKey() {
        return key;
    }

    /** 画面に出す名前を返す。 */
    public String getLabel() {
        return label;
    }

    /** AI に伝える、この項目に書いてほしい中身を返す。 */
    public String getHint() {
        return hint;
    }
}
