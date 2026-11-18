package com.creepycode.hearing.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Step2 の2回目で比べる、細かいテイスト（VISUAL_IMAGES.STYLE_TAGS の detail）。
 * テイスト1つにつき2つ。並び順は、2回目の画面の左・右の順に使う。
 */
public enum VisualDetail {

    NATURAL_GARDEN(VisualTaste.NATURAL, "garden", "ガーデン"),
    NATURAL_BOTANICAL(VisualTaste.NATURAL, "botanical", "ボタニカル"),
    GORGEOUS_LUXURY(VisualTaste.GORGEOUS, "luxury", "ラグジュアリー"),
    GORGEOUS_PRINCESS(VisualTaste.GORGEOUS, "princess", "プリンセス"),
    CHIC_MODERN(VisualTaste.CHIC, "modern", "モダン"),
    CHIC_CLASSIC(VisualTaste.CHIC, "classic", "クラシック"),
    CUTE_KOREAN(VisualTaste.CUTE, "korean", "淡色・韓国風"),
    CUTE_GIRLY(VisualTaste.CUTE, "girly", "ガーリー"),
    JAPANESE_TRADITIONAL(VisualTaste.JAPANESE, "traditional", "伝統"),
    JAPANESE_WAMODERN(VisualTaste.JAPANESE, "wamodern", "和モダン"),
    UNIQUE_HOBBY(VisualTaste.UNIQUE, "hobby", "趣味・テーマ"),
    UNIQUE_FES(VisualTaste.UNIQUE, "fes", "カジュアル・フェス");

    private final VisualTaste taste;
    private final String key;
    private final String label;

    VisualDetail(VisualTaste taste, String key, String label) {
        this.taste = taste;
        this.key = key;
        this.label = label;
    }

    /** この細かいテイストが属するテイスト。 */
    public VisualTaste getTaste() {
        return taste;
    }

    /** STYLE_TAGS に書く名前（例：garden）。 */
    public String getKey() {
        return key;
    }

    /** 画面に出す名前（例：ガーデン）。 */
    public String getLabel() {
        return label;
    }

    /** テイストと STYLE_TAGS に書かれた名前から、細かいテイストを探す。知らない組み合わせなら空。 */
    public static Optional<VisualDetail> fromKey(VisualTaste taste, String key) {
        return Arrays.stream(values())
                .filter(detail -> detail.taste == taste && detail.key.equals(key))
                .findFirst();
    }
}
