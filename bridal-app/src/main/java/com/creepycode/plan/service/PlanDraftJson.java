package com.creepycode.plan.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * プランの大枠と価値観の要点を、JSON から読む・JSON にする。
 * AI の返事の読み取り（形の確かめ）と、AI_DRAFT・VALUE_SUMMARY に保存する JSON の読み書きをまとめている。
 */
@Component
public class PlanDraftJson {

    private final ObjectMapper objectMapper;

    public PlanDraftJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** AI の返事を読み、11項目と価値観の要点がそろっているか確かめて返す。そろっていなければ例外を投げる。 */
    public ParsedPlan parseAiResponse(String response) {
        JsonNode root = readTree(extractJson(response));
        return new ParsedPlan(readDraftNode(root.path("draft")), readValuesNode(root.path("values")));
    }

    /** AI の返事を読んだ結果。 */
    public record ParsedPlan(PlanDraft draft, ValueSummary values) {
    }

    /** プランの大枠を、AI_DRAFT に保存する JSON にする。 */
    public String toDraftJson(PlanDraft draft) {
        List<Map<String, String>> items = new ArrayList<>();
        for (PlanDraft.Item item : draft.items()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("key", item.item().getKey());
            entry.put("proposal", item.proposal());
            entry.put("reason", item.reason());
            items.add(entry);
        }
        return write(Map.of("items", items));
    }

    /** 価値観の要点を、VALUE_SUMMARY に保存する JSON にする。 */
    public String toValuesJson(ValueSummary values) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("important", toPointMaps(values.important()));
        root.put("removable", toPointMaps(values.removable()));
        root.put("removalOrder", values.removalOrder());
        return write(root);
    }

    /** AI_DRAFT に保存した JSON を読む。 */
    public PlanDraft readDraft(String json) {
        return readDraftNode(readTree(json));
    }

    /** VALUE_SUMMARY に保存した JSON を読む。 */
    public ValueSummary readValues(String json) {
        return readValuesNode(readTree(json));
    }

    /**
     * 返事から JSON の部分を取り出す。AI が指示に反して「```json」の印や前置きを付けることがあるので、
     * 最初の「{」から最後の「}」までを使う。
     */
    static String extractJson(String response) {
        if (response == null) {
            throw new PlanDraftFormatException("AI の返事が空でした。");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new PlanDraftFormatException("AI の返事に JSON がありませんでした。");
        }
        return response.substring(start, end + 1);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new PlanDraftFormatException("JSON として読めませんでした。", e);
        }
    }

    /** items の11項目を読む。知らない key は無視し、同じ key が2回あれば最初のものを使う。1つでも欠けたら例外。 */
    private PlanDraft readDraftNode(JsonNode draftNode) {
        JsonNode itemsNode = draftNode.path("items");
        if (!itemsNode.isArray()) {
            throw new PlanDraftFormatException("draft.items がありませんでした。");
        }
        Map<PlanItem, PlanDraft.Item> found = new EnumMap<>(PlanItem.class);
        for (JsonNode node : itemsNode) {
            String key = node.path("key").asText("");
            for (PlanItem item : PlanItem.values()) {
                if (item.getKey().equals(key) && !found.containsKey(item)) {
                    found.put(item, new PlanDraft.Item(item, requireText(node, "proposal", key),
                            requireText(node, "reason", key)));
                }
            }
        }
        for (PlanItem item : PlanItem.values()) {
            if (!found.containsKey(item)) {
                throw new PlanDraftFormatException("項目「" + item.getLabel() + "」がありませんでした。");
            }
        }
        // EnumMap は PlanItem の順に並ぶ
        return new PlanDraft(List.copyOf(found.values()));
    }

    /** 価値観の要点を読む。大事にしていることは1件以上必要。削ってもよい部分と削ってよい順は0件でもよい。 */
    private ValueSummary readValuesNode(JsonNode valuesNode) {
        if (!valuesNode.isObject()) {
            throw new PlanDraftFormatException("values がありませんでした。");
        }
        List<ValueSummary.Point> important = readPoints(valuesNode.path("important"), "important");
        if (important.isEmpty()) {
            throw new PlanDraftFormatException("大事にしていること（values.important）が空でした。");
        }
        List<ValueSummary.Point> removable = readPoints(valuesNode.path("removable"), "removable");
        JsonNode orderNode = valuesNode.path("removalOrder");
        if (!orderNode.isArray()) {
            throw new PlanDraftFormatException("削ってよい順（values.removalOrder）がありませんでした。");
        }
        List<String> removalOrder = new ArrayList<>();
        for (JsonNode node : orderNode) {
            String text = node.asText("").strip();
            if (!text.isEmpty()) {
                removalOrder.add(text);
            }
        }
        return new ValueSummary(important, removable, List.copyOf(removalOrder));
    }

    private List<ValueSummary.Point> readPoints(JsonNode arrayNode, String name) {
        if (!arrayNode.isArray()) {
            throw new PlanDraftFormatException("values." + name + " がありませんでした。");
        }
        List<ValueSummary.Point> points = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            points.add(new ValueSummary.Point(requireText(node, "item", name), node.path("reason").asText("").strip()));
        }
        return List.copyOf(points);
    }

    /** 文字の欄を読む。無い・空のときは例外。 */
    private String requireText(JsonNode node, String field, String where) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new PlanDraftFormatException(where + " の " + field + " が空でした。");
        }
        return value.asText().strip();
    }

    private List<Map<String, String>> toPointMaps(List<ValueSummary.Point> points) {
        List<Map<String, String>> maps = new ArrayList<>();
        for (ValueSummary.Point point : points) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("item", point.item());
            entry.put("reason", point.reason());
            maps.add(entry);
        }
        return maps;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // 文字と一覧だけを JSON にするので、ここに来るのはプログラムの誤り
            throw new IllegalStateException("JSON にできませんでした。", e);
        }
    }
}
