package com.creepycode.common.ai.chatgpt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.creepycode.common.ai.AiClient;
import com.creepycode.common.ai.ChatTurn;
import com.creepycode.common.exception.AiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本物の AI。Sign in with ChatGPT で保存したサインイン情報のアクセストークンを使い、
 * OpenAI の Responses API を直接呼ぶ（app.ai.mode=chatgpt のとき）。
 * 返事は SSE（stream:true）で届くので、文章の断片（response.output_text.delta）をつないで返す。
 * トークン・AI に送った指示と入力は、例外の文言とログに出さない。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "chatgpt")
public class ChatGptAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(ChatGptAiClient.class);

    /** 接続の制限時間。 */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // 基本設計書 v1.5（v1.5_対応表とテスト観点.md T16）：AI の応答が30秒以内に返らない場合は失敗として扱う
    static final Duration REPLY_TIMEOUT = Duration.ofSeconds(30);

    private static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String EVENT_COMPLETED = "response.completed";
    private static final String EVENT_FAILED = "response.failed";
    private static final String EVENT_ERROR = "error";

    /** エラーの応答の本文を読む上限（バイト）。これより後ろは読まない。 */
    private static final int MAX_ERROR_BODY_BYTES = 16 * 1024;
    /** ログに出す項目1つの長さの上限（文字）。 */
    private static final int MAX_FIELD_CHARS = 100;

    private final ChatGptTokenProvider tokens;
    private final URI responsesUri;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Spring が使う組み立て。接続先は公式の URL、制限時間は本番の 30 秒。 */
    @Autowired
    public ChatGptAiClient(ChatGptCredentialStore store, @Value("${app.ai.model}") String model) {
        this(store, ChatGptEndpoints.OFFICIAL_RESPONSES_URL, model, REPLY_TIMEOUT, Clock.systemUTC());
    }

    /**
     * 保存先から資格情報を読み、公式の接続先で更新するクライアントを作る（テストの組み立て）。
     *
     * @param store        サインイン情報の保存先（トークンの読み込みに使う）
     * @param responsesUrl Responses API の URL（テストでは 127.0.0.1 の偽サーバー）
     * @param model        使うモデル名（app.ai.model）
     * @param timeout      返事全体の制限時間（テストでは短くする）
     * @param clock        期限の確認に使う時刻（テストでは固定の時刻）
     */
    ChatGptAiClient(ChatGptCredentialStore store, String responsesUrl, String model, Duration timeout, Clock clock) {
        this(new ChatGptTokenProvider(store, new ChatGptOAuthClient(ChatGptEndpoints.official()), clock),
                responsesUrl, model, timeout);
    }

    /**
     * @param tokens       使うトークン（読み込みと更新。テストでは更新の接続先を偽サーバーにした部品）
     * @param responsesUrl Responses API の URL（テストでは 127.0.0.1 の偽サーバー）
     * @param model        使うモデル名（app.ai.model）
     * @param timeout      更新とやり直しを含めた、返事全体の制限時間
     */
    ChatGptAiClient(ChatGptTokenProvider tokens, String responsesUrl, String model, Duration timeout) {
        this.tokens = tokens;
        this.responsesUri = URI.create(responsesUrl);
        this.model = model;
        this.timeout = timeout;
    }

    /**
     * やり取りを1つの文章にまとめ、指示と一緒に Responses API へ渡す。
     * Responses API の入力は1つの文章で足りるので、履歴は入力の文章に入れて渡す。
     */
    @Override
    public String generateReply(String instructions, List<ChatTurn> history) {
        return generate(instructions, toInputText(history));
    }

    /** やり取りを、古い順に「利用者：」「AI：」の行として並べた文章にする。 */
    public static String toInputText(List<ChatTurn> history) {
        StringBuilder text = new StringBuilder("これまでのやり取り（古い順）：\n");
        for (ChatTurn turn : history) {
            // 役割の値は AiChatMessage.ROLE_USER（"USER"）と同じ。common から hearing の Entity を読まないよう、ここでは文字で比べる
            String speaker = "USER".equals(turn.role()) ? "利用者" : "AI";
            text.append(speaker).append("：").append(turn.content()).append("\n");
        }
        return text.toString();
    }

    /**
     * Responses API を呼び、返事の文章を返す。返事全体（トークンの更新・やり直しを含む）の制限時間を過ぎたら時間切れの例外にする。
     * トークンは ChatGptTokenProvider で確かめ、期限が近ければ更新する。サインインしていない・更新できないときは通信せずに例外にする。
     * AI が 401 を返したときは、トークンを1回だけ更新してやり直す。2回目の 401 は、そのまま 401 の文言の例外にする。
     */
    @Override
    public String generate(String instructions, String input) {
        long deadline = System.nanoTime() + timeout.toNanos();
        String body = requestBody(instructions, input);
        ChatGptCredentials credentials = tokens.usable(remaining(deadline));
        try {
            return send(credentials, body, deadline);
        } catch (UnauthorizedException e) {
            ChatGptCredentials renewed = tokens.afterUnauthorized(credentials.accessToken(), remaining(deadline));
            return send(renewed, body, deadline);
        }
    }

    /** 残りの制限時間（0 より小さくはしない）。 */
    private static Duration remaining(long deadline) {
        return Duration.ofNanos(Math.max(0L, deadline - System.nanoTime()));
    }

    /**
     * 1回の送信と返事の読み取り。返事は別のスレッドで読み、残りの制限時間を過ぎたら通信を閉じる。
     * 401 のときは UnauthorizedException を投げる（呼び出し側が更新してやり直すかを決める）。
     */
    private String send(ChatGptCredentials credentials, String body, long deadline) {
        Duration left = remaining(deadline);
        if (left.isZero()) {
            throw timedOut();
        }
        HttpRequest request = HttpRequest.newBuilder(responsesUri)
                .timeout(left)
                .header("Authorization", "Bearer " + credentials.accessToken())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        // 返事は別のスレッドで読む。制限時間を過ぎたら、読んでいる途中でも通信を閉じられるようにするため
        AtomicReference<InputStream> bodyRef = new AtomicReference<>();
        FutureTask<String> task = new FutureTask<>(() -> readReply(request, bodyRef));
        Thread worker = new Thread(task, "chatgpt-reply");
        worker.setDaemon(true);
        worker.start();
        try {
            return task.get(left.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            stopReading(task, bodyRef);
            throw timedOut();
        } catch (ExecutionException e) {
            throw toAiException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopReading(task, bodyRef);
            throw new AiException("AI の呼び出しが中断されました。", e);
        }
    }

    /** 返事全体の制限時間を過ぎたときの例外（ログも残す。文言に内容は入れない）。 */
    private static AiException timedOut() {
        log.warn("AI の返事が制限時間内に終わりませんでした");
        return new AiException("AI の返事が制限時間内に終わりませんでした。時間をおいてから、もう一度お試しください。");
    }

    /**
     * 送る本文（JSON）。store:false を毎回送り、送ってはいけない項目（temperature など）は入れない。
     * input は公式の指定どおり配列にし、役割が user のメッセージを1つだけ入れる。
     */
    private String requestBody(String instructions, String input) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", input);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", instructions);
        body.put("input", List.of(message));
        body.put("stream", true);
        body.put("store", false);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new AiException("AI に送る内容を作れませんでした。", e);
        }
    }

    /** 返事を読む（別のスレッドで動く）。読み始めた通信の入れ物は bodyRef に入れて、外から閉じられるようにする。 */
    private String readReply(HttpRequest request, AtomicReference<InputStream> bodyRef)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        bodyRef.set(response.body());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            if (response.statusCode() != 200) {
                // 本文はそのまま出さず、原因の項目（code・type・param など）だけをログに足す。reader はまだ何も読んでいない
                String detail = errorDetail(response.body());
                log.warn("ChatGPT の AI 呼び出しがエラーを返しました：status={}{}", response.statusCode(), detail);
                if (response.statusCode() == 401) {
                    // 401 は、トークンを更新してやり直せるように別の型にする
                    throw new UnauthorizedException();
                }
                throw new AiException(messageFor(response.statusCode()));
            }
            return readEvents(reader);
        }
    }

    /**
     * エラーの応答の本文（上限まで）から、原因の項目だけを「 code=… type=… param=… detail=…」の形で返す。
     * error.message と本文の残りは使わない。JSON でない・読めないときは空の文字（HTTP の番号だけをログに残す）。
     */
    private String errorDetail(InputStream body) {
        try {
            JsonNode json = objectMapper.readTree(body.readNBytes(MAX_ERROR_BODY_BYTES));
            if (json == null || !json.isObject()) {
                return "";
            }
            JsonNode error = json.path("error");
            StringBuilder detail = new StringBuilder();
            appendField(detail, "code", error.path("code"));
            appendField(detail, "type", error.path("type"));
            appendField(detail, "param", error.path("param"));
            appendField(detail, "detail", json.path("detail"));
            return detail.toString();
        } catch (IOException e) {
            return "";
        }
    }

    /** 値が文字の項目だけを、短く切って末尾に足す（改行は空白にする）。無い項目は何も足さない。 */
    private static void appendField(StringBuilder out, String name, JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) {
            return;
        }
        String text = node.asText().replaceAll("\\p{Cntrl}+", " ").trim();
        if (text.isEmpty()) {
            return;
        }
        int length = text.codePointCount(0, text.length());
        if (length > MAX_FIELD_CHARS) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_FIELD_CHARS));
        }
        out.append(' ').append(name).append('=').append(text);
    }

    /**
     * SSE の行を順に読み、文章の断片をつないで返す。response.completed で終わる。
     * 「data:」の行の JSON の type で判断する。type が無いときは直前の「event:」の行の名前を使う（どちらの形でも読めるように）。
     */
    private String readEvents(BufferedReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        String eventName = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                eventName = null;
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
                continue;
            }
            if (!line.startsWith("data:")) {
                // 注釈の行（":" で始まる）など、使わない行は読み飛ばす
                continue;
            }
            String data = line.substring("data:".length()).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }
            JsonNode json = parseEvent(data);
            String type = json.path("type").asText(eventName == null ? "" : eventName);
            if (EVENT_OUTPUT_TEXT_DELTA.equals(type)) {
                text.append(json.path("delta").asText(""));
            } else if (EVENT_COMPLETED.equals(type)) {
                return text.toString();
            } else if (EVENT_FAILED.equals(type) || EVENT_ERROR.equals(type)) {
                // 失敗の中身（メッセージ）は利用者の内容を含みうるので、例外の文言にもログにも出さない
                log.warn("ChatGPT の AI が返事を作れませんでした（{}）", type);
                throw new AiException("AI が返事を作れませんでした。時間をおいてから、もう一度お試しください。");
            }
            // それ以外の通知（作成中の印など）は文章に使わない
        }
        // response.completed が来ないまま終わった（途中で切れた）
        throw new AiException("AI の返事が途中で切れました。時間をおいてから、もう一度お試しください。");
    }

    private JsonNode parseEvent(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (JsonProcessingException e) {
            throw new AiException("AI の返事の形が想定と違いました。", e);
        }
    }

    /** HTTP の番号ごとに、利用者が取れる行動が分かる文言にする（ChatGPT の利用枠・サインインの確認など）。 */
    static String messageFor(int status) {
        return switch (status) {
            case 401 -> "ChatGPT のサインインが無効になりました。サインインし直してください。";
            case 403 -> "このアカウントでは AI を使えません（利用が許可されていません）。";
            case 429 -> "ChatGPT の利用枠の上限に達しました。ChatGPT の設定の Usage で確認してください。";
            case 503 -> "AI が混み合っています。時間をおいてから、もう一度お試しください。";
            default -> "AI の呼び出しに失敗しました（HTTP " + status + "）。";
        };
    }

    /**
     * AI が 401（サインインの無効）を返したときの例外。文言は 401 のものと同じ。
     * generate の中で、トークンを1回だけ更新してやり直すかどうかの判断に使う。
     */
    private static final class UnauthorizedException extends AiException {

        UnauthorizedException() {
            super(messageFor(401));
        }
    }

    /** 読んでいる途中の通信を閉じて、読むスレッドを止める。 */
    private static void stopReading(FutureTask<String> task, AtomicReference<InputStream> bodyRef) {
        task.cancel(true);
        InputStream body = bodyRef.get();
        if (body != null) {
            try {
                body.close();
            } catch (IOException e) {
                log.debug("通信を閉じるときにエラーが出ました（{}）", e.getClass().getSimpleName());
            }
        }
    }

    /** 読むスレッドの中で起きた例外を、利用者に出せる例外にする（元の例外の文言は出さない）。 */
    private static AiException toAiException(Throwable cause) {
        if (cause instanceof AiException aiException) {
            return aiException;
        }
        if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException) {
            log.warn("ChatGPT の AI に接続できませんでした（{}）", cause.getClass().getSimpleName());
            return new AiException("AI に接続できませんでした。通信の状態を確かめて、もう一度お試しください。", cause);
        }
        if (cause instanceof HttpTimeoutException) {
            return new AiException("AI の返事が制限時間内に終わりませんでした。時間をおいてから、もう一度お試しください。", cause);
        }
        if (cause instanceof IOException) {
            log.warn("AI との通信が途中で切れました（{}）", cause.getClass().getSimpleName());
            return new AiException("AI との通信が途中で切れました。時間をおいてから、もう一度お試しください。", cause);
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new AiException("AI の呼び出しが中断されました。", cause);
        }
        return new AiException("AI の呼び出しに失敗しました。", cause);
    }
}
