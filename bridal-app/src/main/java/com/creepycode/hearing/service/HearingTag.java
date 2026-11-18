package com.creepycode.hearing.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * かんたん質問 STEP4（P-09）で選べるタグの一覧。並び順は画面に並べる順で、保存するときもこの順にそろえる。
 * タグのマスタ表は無いので、ここで持つ。DB（ONBOARDING_ANSWERS）には label をカンマ区切りで保存する
 * （P-10 の AI にもそのまま渡すので、key ではなく label にしている）。label にはカンマを使わない。
 */
public enum HearingTag {

    // ---- 決められないこと（UNDECIDED_TAGS） ----
    UNDECIDED_VENUE(Group.UNDECIDED, "u-venue", "会場", "ホテル・専門式場・レストランなど、式をする場所",
            List.of("d-venue", "u-guests")),
    UNDECIDED_FOOD(Group.UNDECIDED, "u-food", "料理", "ゲストに出すコースや飲み物",
            List.of("d-food", "d-hospitality")),
    UNDECIDED_GUESTS(Group.UNDECIDED, "u-guests", "招待する人の範囲", "どこまでの友人・親せきを呼ぶか",
            List.of("u-venue", "u-gifts", "u-budget", "d-hospitality")),
    UNDECIDED_ATTIRE(Group.UNDECIDED, "u-attire", "ドレス・衣装", "ドレス・タキシード・和装など",
            List.of("d-attire", "d-photo")),
    UNDECIDED_PROGRAM(Group.UNDECIDED, "u-program", "演出・余興", "入場のしかた、映像、友人の出し物など",
            List.of("d-program", "d-family")),
    UNDECIDED_GIFTS(Group.UNDECIDED, "u-gifts", "引出物", "ゲストが持ち帰る贈り物",
            List.of("d-hospitality", "u-guests")),
    UNDECIDED_FLOWERS(Group.UNDECIDED, "u-flowers", "装花・装飾", "テーブルや会場の花・飾りつけ",
            List.of("d-flowers", "d-venue")),
    UNDECIDED_BUDGET(Group.UNDECIDED, "u-budget", "予算の配分", "どこにかけて、どこをおさえるか",
            List.of("u-guests", "u-venue")),

    // ---- こだわりたいこと（DECIDED_TAGS） ----
    DECIDED_PHOTO(Group.DECIDED, "d-photo", "写真・映像", "当日の写真や動画をきれいに残す",
            List.of("u-attire", "d-flowers")),
    DECIDED_FOOD(Group.DECIDED, "d-food", "料理の味や質", "ゲストに喜ばれる料理",
            List.of("u-food", "d-hospitality")),
    DECIDED_VENUE(Group.DECIDED, "d-venue", "会場の雰囲気", "景色・内装・広さなど",
            List.of("u-venue", "d-flowers")),
    DECIDED_HOSPITALITY(Group.DECIDED, "d-hospitality", "ゲストへのおもてなし", "ゲストが過ごしやすい工夫",
            List.of("d-food", "u-gifts")),
    DECIDED_ATTIRE(Group.DECIDED, "d-attire", "ドレス・衣装", "着たい衣装を着る",
            List.of("u-attire", "d-photo")),
    DECIDED_FLOWERS(Group.DECIDED, "d-flowers", "装花・装飾", "好きな花や色で会場を飾る",
            List.of("u-flowers", "d-venue")),
    DECIDED_PROGRAM(Group.DECIDED, "d-program", "演出", "ふたりらしい入場や映像など",
            List.of("u-program", "d-family")),
    DECIDED_FAMILY(Group.DECIDED, "d-family", "家族への感謝", "両親や家族に気持ちを伝える場面",
            List.of("d-program"));

    /** タグのグループ。 */
    public enum Group {
        /** 決められないこと（UNDECIDED_TAGS に保存する）。 */
        UNDECIDED,
        /** こだわりたいこと（DECIDED_TAGS に保存する）。 */
        DECIDED
    }

    private final Group group;
    private final String key;
    private final String label;
    private final String description;
    // おすすめ先（固定の組み合わせ表）。ほかのタグの key で持つ（enum の中で、まだ作られていない定数を指せないため）
    private final List<String> relatedKeys;

    HearingTag(Group group, String key, String label, String description, List<String> relatedKeys) {
        this.group = group;
        this.key = key;
        this.label = label;
        this.description = description;
        this.relatedKeys = relatedKeys;
    }

    /** タグのグループ（決められないこと／こだわりたいこと）。 */
    public Group getGroup() {
        return group;
    }

    /** 画面から送られる値（例：u-venue）。 */
    public String getKey() {
        return key;
    }

    /** 画面に出し、DB に保存する名前（例：会場）。 */
    public String getLabel() {
        return label;
    }

    /** 名前の下に出す一言の説明。 */
    public String getDescription() {
        return description;
    }

    /** 選んだときに「おすすめ」の印を付けるタグの key。 */
    public List<String> getRelatedKeys() {
        return relatedKeys;
    }

    /** そのグループのタグを、並び順に返す。 */
    public static List<HearingTag> of(Group group) {
        return Arrays.stream(values()).filter(tag -> tag.group == group).toList();
    }

    /** そのグループの中から、key が一致するタグを探す。見つからなければ空。 */
    public static Optional<HearingTag> fromKey(Group group, String key) {
        return of(group).stream().filter(tag -> tag.key.equals(key)).findFirst();
    }

    /** そのグループの中から、label が一致するタグを探す（DB に保存した値を読むとき）。見つからなければ空。 */
    public static Optional<HearingTag> fromLabel(Group group, String label) {
        return of(group).stream().filter(tag -> tag.label.equals(label)).findFirst();
    }
}
