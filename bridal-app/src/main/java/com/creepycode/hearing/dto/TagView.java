package com.creepycode.hearing.dto;

/**
 * かんたん質問 STEP4（P-09）に並べるタグ1つ分。
 * key は画面から送られる値、relatedKeys は「おすすめ」の印を付けるタグの key を空白で区切ったもの（hearing.js が使う）。
 * checked は、保存してあるタグなら true（開き直したとき、選ばれた状態で出すため）。
 */
public record TagView(String key, String label, String description, String relatedKeys, boolean checked) {
}
