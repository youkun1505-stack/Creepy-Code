package com.creepycode.hearing.dto;

import java.util.List;

/**
 * 「かんたん質問の答え」の1項目（AI相談の画面に、読むだけで出す）。
 *
 * @param label 項目の名前（例：理想のカップル像）
 * @param value 答えの中身（例：まだ決まっていない）
 * @param details 答えの細かい内容を1件ずつ別の行に出すための一覧（例：好きな雰囲気の、テイストごとの好み）。無ければ空
 */
public record HearingAnswerItemView(String label, String value, List<String> details) {

    /** 細かい内容が無い項目（大半の項目はこちらを使う）。 */
    public HearingAnswerItemView(String label, String value) {
        this(label, value, List.of());
    }

    /** null は空の一覧にそろえる（画面で一覧の有無だけを見ればよいようにする）。 */
    public HearingAnswerItemView {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
