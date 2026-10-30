package com.creepycode.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.creepycode.common.ai.DummyAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI の返事の読み取りと、保存する JSON の読み書き（PlanDraftJson）のテスト。
 */
class PlanDraftJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlanDraftJson draftJson = new PlanDraftJson(objectMapper);

    @Test
    void 正しい返事なら11項目をPlanItemの順に読み_価値観の要点も読む() throws Exception {
        PlanDraftJson.ParsedPlan parsed = draftJson.parseAiResponse(response(reversedItems(), values()));

        assertThat(parsed.draft().items()).extracting(PlanDraft.Item::item).containsExactly(PlanItem.values());
        assertThat(parsed.draft().items().get(0).proposal()).isEqualTo("concept の提案");
        assertThat(parsed.draft().items().get(0).reason()).isEqualTo("concept の理由");
        assertThat(parsed.values().important()).containsExactly(new ValueSummary.Point("写真", "残したい"));
        assertThat(parsed.values().removable()).containsExactly(new ValueSummary.Point("引出物", "こだわりなし"));
        assertThat(parsed.values().removalOrder()).containsExactly("引出物");
    }

    @Test
    void 前後に文章やコードブロックの印があっても読める() throws Exception {
        String raw = "はい、作りました。\n```json\n" + response(items(), values()) + "\n```\n以上です。";

        assertThat(draftJson.parseAiResponse(raw).draft().items()).hasSize(PlanItem.values().length);
    }

    @Test
    void プラン用のダミーの返事も読める() {
        String dummy = new PlanDraftAiService(new DummyAiClient(), objectMapper).dummyDraft();

        assertThat(draftJson.parseAiResponse(dummy).draft().items()).hasSize(PlanItem.values().length);
    }

    @Test
    void JSONでない返事は形の誤りにする() {
        assertThatThrownBy(() -> draftJson.parseAiResponse("すみません、作れませんでした。"))
                .isInstanceOf(PlanDraftFormatException.class);
        assertThatThrownBy(() -> draftJson.parseAiResponse("{ 壊れた JSON }"))
                .isInstanceOf(PlanDraftFormatException.class);
        assertThatThrownBy(() -> draftJson.parseAiResponse(null)).isInstanceOf(PlanDraftFormatException.class);
    }

    @Test
    void 項目が1つでも欠けたら形の誤りにする() throws Exception {
        List<Map<String, String>> items = items();
        items.removeIf(item -> item.get("key").equals("photo"));

        assertThatThrownBy(() -> draftJson.parseAiResponse(response(items, values())))
                .isInstanceOf(PlanDraftFormatException.class).hasMessageContaining("写真・映像");
    }

    @Test
    void 提案や理由が空なら形の誤りにする() throws Exception {
        List<Map<String, String>> items = items();
        items.set(2, Map.of("key", "venue", "proposal", " ", "reason", "理由"));

        assertThatThrownBy(() -> draftJson.parseAiResponse(response(items, values())))
                .isInstanceOf(PlanDraftFormatException.class);
    }

    @Test
    void 知らないkeyは無視する() throws Exception {
        List<Map<String, String>> items = items();
        items.add(Map.of("key", "unknown", "proposal", "余分", "reason", "余分"));

        assertThat(draftJson.parseAiResponse(response(items, values())).draft().items())
                .hasSize(PlanItem.values().length);
    }

    @Test
    void 大事にしていることが空か_価値観の要点が無ければ形の誤りにする() throws Exception {
        Map<String, Object> emptyImportant = Map.of("important", List.of(), "removable", List.of(),
                "removalOrder", List.of());

        assertThatThrownBy(() -> draftJson.parseAiResponse(response(items(), emptyImportant)))
                .isInstanceOf(PlanDraftFormatException.class);
        String noValues = objectMapper.writeValueAsString(Map.of("draft", Map.of("items", items())));
        assertThatThrownBy(() -> draftJson.parseAiResponse(noValues)).isInstanceOf(PlanDraftFormatException.class);
    }

    @Test
    void 削ってもよい部分と削ってよい順は0件でもよい() throws Exception {
        Map<String, Object> values = Map.of("important", List.of(Map.of("item", "写真", "reason", "残したい")),
                "removable", List.of(), "removalOrder", List.of());

        ValueSummary read = draftJson.parseAiResponse(response(items(), values)).values();

        assertThat(read.removable()).isEmpty();
        assertThat(read.removalOrder()).isEmpty();
    }

    @Test
    void 保存するJSONに書いて読み戻すと同じ中身になる() throws Exception {
        PlanDraftJson.ParsedPlan parsed = draftJson.parseAiResponse(response(items(), values()));

        assertThat(draftJson.readDraft(draftJson.toDraftJson(parsed.draft()))).isEqualTo(parsed.draft());
        assertThat(draftJson.readValues(draftJson.toValuesJson(parsed.values()))).isEqualTo(parsed.values());
    }

    private List<Map<String, String>> items() {
        List<Map<String, String>> items = new ArrayList<>();
        for (PlanItem item : PlanItem.values()) {
            items.add(Map.of("key", item.getKey(), "proposal", item.getKey() + " の提案",
                    "reason", item.getKey() + " の理由"));
        }
        return items;
    }

    private List<Map<String, String>> reversedItems() {
        List<Map<String, String>> items = items();
        java.util.Collections.reverse(items);
        return items;
    }

    private Map<String, Object> values() {
        return Map.of("important", List.of(Map.of("item", "写真", "reason", "残したい")),
                "removable", List.of(Map.of("item", "引出物", "reason", "こだわりなし")),
                "removalOrder", List.of("引出物"));
    }

    private String response(List<Map<String, String>> items, Map<String, Object> values) throws Exception {
        return objectMapper.writeValueAsString(Map.of("draft", Map.of("items", items), "values", values));
    }
}
