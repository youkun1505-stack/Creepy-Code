package com.creepycode.common.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.creepycode.common.exception.AiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本物の AI。中継プログラム（ai-relay フォルダ）を経由して、Sign in with ChatGPT で AI を呼ぶ
 * （app.ai.mode=chatgpt のとき）。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "chatgpt")
public class RelayAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(RelayAiClient.class);

    // 基本設計書 6-5：AI の応答が30秒以内に返らない場合は失敗として扱う
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper;
    private final URI generateUri;

    public RelayAiClient(ObjectMapper objectMapper, @Value("${app.ai.relay-url}") String relayUrl) {
        this.objectMapper = objectMapper;
        this.generateUri = URI.create(relayUrl.replaceAll("/+$", "") + "/generate");
    }

    /**
     * やり取りを1つの文章にまとめ、中継プログラムの /generate を呼ぶ。
     * 中継プログラムの受け口は「指示」と「入力の文章」だけなので、履歴は入力の文章に入れて渡す。
     */
    @Override
    public String generateReply(String instructions, List<ChatTurn> history) {
        return generate(instructions, toInputText(history));
    }

    /** やり取りを、古い順に「利用者：」「AI：」の行として並べた文章にする。 */
    static String toInputText(List<ChatTurn> history) {
        StringBuilder text = new StringBuilder("これまでのやり取り（古い順）：\n");
        for (ChatTurn turn : history) {
            // 役割の値は AiChatMessage.ROLE_USER（"USER"）と同じ。common から hearing の Entity を読まないよう、ここでは文字で比べる
            String speaker = "USER".equals(turn.role()) ? "利用者" : "AI";
            text.append(speaker).append("：").append(turn.content()).append("\n");
        }
        return text.toString();
    }

    /** 中継プログラムの /generate を呼び、AI が作った文章を返す。 */
    @Override
    public String generate(String instructions, String input) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("instructions", instructions);
        body.put("input", input);
        try {
            HttpRequest request = HttpRequest.newBuilder(generateUri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json;
            try {
                json = objectMapper.readTree(response.body());
            } catch (JsonProcessingException e) {
                // 中継プログラムには接続できたが、返答が JSON でなかった場合
                log.warn("中継プログラムの返答を JSON として読めませんでした：status={}", response.statusCode());
                throw new AiException("AI の返答の形式が正しくありませんでした。");
            }
            if (response.statusCode() != 200) {
                // AI に送った内容はログに出さない（エラーの種類だけ出す）
                String code = json.path("error").path("code").asText("unknown");
                log.warn("中継プログラムがエラーを返しました：status={} code={}", response.statusCode(), code);
                throw new AiException("AI の呼び出しに失敗しました（" + code + "）");
            }
            return json.path("text").asText();
        } catch (JsonProcessingException e) {
            // リクエストボディの直列化に失敗した場合（通常は発生しない）
            log.warn("リクエストの直列化に失敗しました：{}", e.getClass().getSimpleName());
            throw new AiException("AI へのリクエストを作れませんでした。", e);
        } catch (IOException e) {
            log.warn("中継プログラムを呼び出せませんでした：{}", e.getClass().getSimpleName());
            throw new AiException("AI を呼び出せませんでした。中継プログラムが起動しているか確認してください。", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("AI の呼び出しが中断されました。", e);
        }
    }
}
