package com.creepycode.hearing.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Step2 の画像のテイスト（VISUAL_IMAGES.STYLE_TAGS の taste）。並び順は、まとめで同じ数のときの並び順に使う。
 */
public enum VisualTaste {

    NATURAL("natural", "ナチュラル"),
    GORGEOUS("gorgeous", "華やか"),
    CHIC("chic", "シック・大人"),
    CUTE("cute", "かわいい・淡色"),
    JAPANESE("japanese", "和"),
    UNIQUE("unique", "個性・ふたりらしさ");

    private final String key;
    private final String label;

    VisualTaste(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** STYLE_TAGS に書く名前（例：natural）。 */
    public String getKey() {
        return key;
    }

    /** 画面に出す名前（例：ナチュラル）。 */
    public String getLabel() {
        return label;
    }

    /** STYLE_TAGS に書かれた名前からテイストを探す。知らない名前なら空。 */
    public static Optional<VisualTaste> fromKey(String key) {
        return Arrays.stream(values()).filter(taste -> taste.key.equals(key)).findFirst();
    }
}
