package com.creepycode.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.creepycode.common.ai.AiClient;
import com.creepycode.common.ai.DummyAiClient;
import com.creepycode.common.exception.AiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * プランの大枠を AI に作らせる部分（PlanDraftAiService）のテスト。本物の AI は呼ばない。
 */
class PlanDraftAiServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlanMaterial material = new PlanMaterial("- どんな式にしたいか（STEP1）：テスト", "会話はまだありません");

    @Test
    void ダミーモードではAIを呼ばず_11項目と価値観の要点がそろったJSONを返す() throws Exception {
        PlanDraftAiService service = new PlanDraftAiService(new DummyAiClient(), objectMapper);

        String json = service.requestDraft(material);

        // DummyAiClient を呼んでいれば、P-10 用の「質問の配列」が返るので、この形にはならない
        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.path("draft").path("items");
        assertThat(items.size()).isEqualTo(PlanItem.values().length);
        List<String> keys = new ArrayList<>();
        for (JsonNode item : items) {
            keys.add(item.path("key").asText());
            assertThat(item.path("proposal").asText()).startsWith("【ダミー】");
            assertThat(item.path("reason").asText()).startsWith("【ダミー】");
        }
        assertThat(keys).containsExactly(
                "concept", "ceremony", "venue", "guests", "food", "attire",
                "flowers", "program", "photo", "gifts", "budget");
        JsonNode values = root.path("values");
        assertThat(values.path("important").size()).isPositive();
        assertThat(values.path("removable").size()).isPositive();
        assertThat(values.path("removalOrder").size()).isPositive();
    }

    @Test
    void ダミーモードかはDummyAiClientかどうかで決める() {
        assertThat(new PlanDraftAiService(new DummyAiClient(), objectMapper).isDummyMode()).isTrue();
        assertThat(new PlanDraftAiService(mock(AiClient.class), objectMapper).isDummyMode()).isFalse();
    }

    @Test
    void 本物のときはAIに指示と材料を渡し_返事をそのまま返す() {
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generate(anyString(), anyString(), any(Duration.class))).thenReturn("{\"draft\":{}}");
        PlanDraftAiService service = new PlanDraftAiService(aiClient, objectMapper);

        String json = service.requestDraft(material);

        assertThat(json).isEqualTo("{\"draft\":{}}");
        verify(aiClient).generate(PlanDraftAiService.INSTRUCTIONS, material.toAiInput(), Duration.ofMinutes(10));
    }

    @Test
    void プラン生成のAIの返事は最大10分まで待つ() {
        assertThat(PlanDraftAiService.AI_TIMEOUT).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void 本物のときAIの失敗はAiExceptionのまま出る() {
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.generate(anyString(), anyString(), any(Duration.class))).thenThrow(new AiException("時間切れ"));
        PlanDraftAiService service = new PlanDraftAiService(aiClient, objectMapper);

        assertThatThrownBy(() -> service.requestDraft(material)).isInstanceOf(AiException.class);
    }

    @Test
    void 指示に11項目のkeyと_価値観の要点と_タグの扱いが書かれている() {
        String instructions = PlanDraftAiService.INSTRUCTIONS;

        for (PlanItem item : PlanItem.values()) {
            assertThat(instructions).contains("・" + item.getKey() + "：" + item.getLabel());
        }
        assertThat(instructions)
                .contains("\"important\"", "\"removable\"", "\"removalOrder\"")
                .contains("「こだわりたいこと」に選ばれたもの")
                .contains("「まだ決められないこと」に選ばれたもの")
                .contains("候補を2つほど並べ")
                .contains("JSON だけを返して");
    }
}
