package com.creepycode.hearing.dto;

import java.util.List;

/**
 * Step2 のまとめ。
 * headline は見出しの文（例：「ナチュラル」と「和」が好きなようです）。好きなテイストが無ければ null。
 * tasteResults は好きなテイストごとの、細かい好み（「『和モダン』寄り」など）。好きなテイストが無ければ空。
 */
public record VisualSummaryView(String headline, List<TasteResult> tasteResults) {

    /** 好きなテイストが1つも無いか。 */
    public boolean isEmpty() {
        return tasteResults.isEmpty();
    }

    /** 好きなテイスト1つ分。detailText は細かい好みを言葉にしたもの。 */
    public record TasteResult(String label, String detailText) {
    }
}
