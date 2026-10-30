package com.creepycode.plan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.creepycode.common.ai.AiClient;
import com.creepycode.common.ai.DummyAiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * プランの大枠（雛形）と価値観の要点を、AI に作らせる。返すのは AI の返事（JSON の文章）のまま。
 * 返事を読んで確かめる・保存するのは、呼び出す側の仕事。
 * ダミーモードのときは AI を呼ばず、プラン用の固定の JSON を返す。
 */
@Service
public class PlanDraftAiService {

    /** AI への指示。返してほしい JSON の形と、書き方の決まりを伝える。 */
    static final String INSTRUCTIONS = buildInstructions();

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PlanDraftAiService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 材料をもとに、AI にプランの大枠と価値観の要点を作らせ、返事（JSON の文章）を返す。
     * ダミーモードのときは AI を呼ばずに固定の JSON を返す。AI の呼び出しに失敗したときは AiException がそのまま出る。
     */
    public String requestDraft(PlanMaterial material) {
        if (isDummyMode()) {
            return dummyDraft();
        }
        return aiClient.generate(INSTRUCTIONS, material.toAiInput());
    }

    /**
     * ダミーモードかを返す。common/ai が用意した AI がダミー（DummyAiClient）かで決める。
     * ダミーにする条件（APP_AI_MODE が dummy か未設定）を、ここに二重に書かないため。
     */
    boolean isDummyMode() {
        return aiClient instanceof DummyAiClient;
    }

    /** ダミーモード用の固定のプラン（11項目と価値観の要点）を、AI の返事と同じ形の JSON で返す。 */
    String dummyDraft() {
        List<Map<String, String>> items = new ArrayList<>();
        for (PlanItem item : PlanItem.values()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("key", item.getKey());
            entry.put("proposal", "【ダミー】" + item.getLabel() + "の提案が入ります。");
            entry.put("reason", "【ダミー】ダミーモードのため、回答をもとにしていません。");
            items.add(entry);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("important", List.of(
                Map.of("item", "【ダミー】大事にしていること", "reason", "【ダミー】ダミーモードの例です。")));
        values.put("removable", List.of(
                Map.of("item", "【ダミー】削ってもよい部分", "reason", "【ダミー】ダミーモードの例です。")));
        values.put("removalOrder", List.of("【ダミー】削ってもよい部分"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("draft", Map.of("items", items));
        root.put("values", values);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // 決まった中身を JSON にするだけなので、ここに来るのはプログラムの誤り
            throw new IllegalStateException("ダミーのプランを JSON にできませんでした。", e);
        }
    }

    /** AI への指示を組み立てる。項目の一覧は PlanItem から作り、key がずれないようにする。 */
    private static String buildInstructions() {
        StringBuilder itemLines = new StringBuilder();
        for (PlanItem item : PlanItem.values()) {
            itemLines.append("・").append(item.getKey()).append("：").append(item.getLabel())
                    .append("（").append(item.getHint()).append("）\n");
        }
        return """
                あなたは結婚式場のプランナーの助手です。
                お客様の回答（確定情報）と、相談の記録（時系列の対話ログ）をもとに、
                結婚式のプランの大枠（たたき台）と、お客様の価値観の要点を作ってください。

                次の形の JSON だけを返してください。前後に説明の文章や「```」の印を付けないでください。
                {
                  "draft": {
                    "items": [
                      {"key": "項目の key", "proposal": "提案", "reason": "そう考えた理由（どの回答や相談をもとにしたか）"}
                    ]
                  },
                  "values": {
                    "important": [{"item": "大事にしていること", "reason": "理由"}],
                    "removable": [{"item": "削ってもよい部分", "reason": "理由"}],
                    "removalOrder": ["削ってもよい部分を、削ってよい順に並べたもの"]
                  }
                }

                draft.items には、次の11項目をすべて、この順に入れてください。key は次のとおりに書いてください。
                %s
                書き方の決まり：
                ・「こだわりたいこと」に選ばれたものに当たる項目は、提案をくわしく書く
                ・「まだ決められないこと」に選ばれたものに当たる項目は、候補を2つほど並べ、それぞれの良いところを書く
                ・回答が「まだ決まっていない」「まだ答えていない」ものは決めつけず、考え方や目安を書く
                ・予算が分かるときは、予算の配分の目安を予算の中で書く（予算はご祝儀を引く前の総額）。金額は目安として書く
                ・実在の式場名・お店の名前・商品名は書かない
                ・相談の記録にある希望は、回答より新しい気持ちとして大事にする
                ・専門用語は使わず、かみ砕いて書く
                ・values.important と values.removable は、それぞれ1〜3件にする
                ・values.removalOrder には、values.removable の item を、削ってよい順にすべて並べる
                """.formatted(itemLines);
    }
}
