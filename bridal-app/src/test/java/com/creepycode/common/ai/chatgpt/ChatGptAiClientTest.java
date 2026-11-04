package com.creepycode.common.ai.chatgpt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.creepycode.common.ai.AiClient;
import com.creepycode.common.ai.DummyAiClient;
import com.creepycode.common.exception.AiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 本物の AI（ChatGptAiClient）のテスト。外のネットワークは使わず、127.0.0.1 の偽の Responses API に向ける。
 * サインイン情報は @TempDir に保存し、暗号化は偽の部品で代用する。
 */
class ChatGptAiClientTest {

    private static final String PATH = "/v1/responses";
    private static final String TOKEN = "ACCESS-TOKEN-SECRET-123";
    private static final String REFRESH = "REFRESH-TOKEN-SECRET-456";
    private static final String INSTRUCTIONS = "INSTRUCTIONS-SECRET-789";
    private static final String INPUT = "INPUT-SECRET-000";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC);
    private static final Instant EXPIRES_LATER = Instant.parse("2026-10-28T11:00:00Z");
    private static final Instant EXPIRED = Instant.parse("2026-10-28T09:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 偽サーバーが受け取った1回ぶんの内容（認証の値と本文）。 */
    private record Captured(String authorization, String body) {
    }

    /** 偽サーバーの応答を決める部品。 */
    private interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @TempDir
    Path tempDir;

    private HttpServer server;
    private ChatGptCredentialStore store;
    private final List<Captured> requests = new CopyOnWriteArrayList<>();
    private volatile Responder responder;
    private volatile CountDownLatch release;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void startFakeServer() throws IOException {
        store = new ChatGptCredentialStore(tempDir.resolve("chatgpt-java"), new IdentityProtector());
        release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
            requests.add(new Captured(exchange.getRequestHeaders().getFirst("Authorization"), body));
            responder.respond(exchange);
        });
        server.start();
        // ログは ListAppender で捕まえて、中身を確かめる
        logs = new ListAppender<>();
        logs.start();
        logger = (Logger) LoggerFactory.getLogger(ChatGptAiClient.class);
        logger.addAppender(logs);
    }

    @AfterEach
    void stopFakeServer() {
        // 途中で止めた応答を先に解放してから、偽サーバーを止める
        release.countDown();
        server.stop(0);
        logger.detachAppender(logs);
    }

    @Test
    void 有効なサインイン情報で_Bearerと正しい形の本文を送り_断片をつないだ文章を返す() throws IOException {
        signIn(EXPIRES_LATER);
        responder = exchange -> sseOk(exchange, sse(delta("こんに"), delta("ちは"), completed()));

        String reply = client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT);

        assertThat(reply).isEqualTo("こんにちは");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).authorization()).isEqualTo("Bearer " + TOKEN);
        JsonNode body = JSON.readTree(requests.get(0).body());
        assertThat(body.path("model").asText()).isEqualTo("gpt-test");
        assertThat(body.path("instructions").asText()).isEqualTo(INSTRUCTIONS);
        // input は配列で、役割が user・内容が渡した文章のメッセージを1つだけ持つ
        JsonNode input = body.path("input");
        assertThat(input.isArray()).isTrue();
        assertThat(input.size()).isEqualTo(1);
        JsonNode message = input.get(0);
        assertThat(message.size()).isEqualTo(2);
        assertThat(message.path("role").asText()).isEqualTo("user");
        assertThat(message.path("content").asText()).isEqualTo(INPUT);
        assertThat(body.get("stream").toString()).isEqualTo("true");
        assertThat(body.get("store").toString()).isEqualTo("false");
        // 送ってはいけない項目（公式の制限）を送らない
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.has("max_output_tokens")).isFalse();
        assertThat(body.has("previous_response_id")).isFalse();
    }

    @Test
    void event行の形でも_data行だけの形でも同じ文章になる() throws IOException {
        signIn(EXPIRES_LATER);
        ChatGptAiClient client = client(Duration.ofSeconds(30));

        responder = exchange -> sseOk(exchange, "data: " + delta("A") + "\n\ndata: " + delta("B")
                + "\n\ndata: " + completed() + "\n\n");
        assertThat(client.generate(INSTRUCTIONS, INPUT)).isEqualTo("AB");

        // JSON に type が無いときは、直前の event 行の名前で判断する
        responder = exchange -> sseOk(exchange, "event: response.output_text.delta\ndata: {\"delta\":\"C\"}\n\n"
                + "event: response.completed\ndata: {}\n\n");
        assertThat(client.generate(INSTRUCTIONS, INPUT)).isEqualTo("C");
    }

    @Test
    void response_completedが来る前に切れたら_途中で切れた例外になる() throws IOException {
        signIn(EXPIRES_LATER);
        responder = exchange -> sseOk(exchange, "event: response.output_text.delta\ndata: " + delta("途中") + "\n\n");

        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("途中で切れ")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(INSTRUCTIONS)
                .hasMessageNotContaining(INPUT);
    }

    @Test
    void response_failedが来たら_失敗の例外になり_中身の文章は出さない() throws IOException {
        signIn(EXPIRES_LATER);
        String failed = "{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"server_error\","
                + "\"message\":\"" + INPUT + "\"}}}";
        responder = exchange -> sseOk(exchange, "event: response.failed\ndata: " + failed + "\n\n");

        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("返事を作れませんでした")
                .hasMessageNotContaining(INPUT);
    }

    @Test
    void HTTPの401_403_429_503は_それぞれ分かる例外になり_トークンや内容は出さない() throws IOException {
        signIn(EXPIRES_LATER);
        ChatGptAiClient client = client(Duration.ofSeconds(30));
        String[][] cases = {
                {"401", "サインインし直して"},
                {"403", "利用が許可されていません"},
                {"429", "利用枠の上限"},
                {"503", "混み合っています"},
        };
        for (String[] c : cases) {
            requests.clear();
            int status = Integer.parseInt(c[0]);
            responder = exchange -> sseError(exchange, status, "{\"detail\":\"error\"}");

            assertThatThrownBy(() -> client.generate(INSTRUCTIONS, INPUT))
                    .as("HTTP " + c[0])
                    .isInstanceOf(AiException.class)
                    .hasMessageContaining(c[1])
                    .hasMessageNotContaining(TOKEN)
                    .hasMessageNotContaining(REFRESH)
                    .hasMessageNotContaining(INSTRUCTIONS)
                    .hasMessageNotContaining(INPUT);
            assertThat(requests).hasSize(1);
        }
    }

    @Test
    void 本文のerrorのcode_type_paramだけを_警告のログに足し_message_や送った内容は出さない() throws IOException {
        signIn(EXPIRES_LATER);
        responder = exchange -> sseError(exchange, 400, "{\"error\":{\"message\":\"秘密の文\","
                + "\"type\":\"invalid_request_error\",\"param\":\"model\",\"code\":\"model_not_found\"}}");

        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessage("AI の呼び出しに失敗しました（HTTP 400）。");

        List<String> warnings = warnings();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .endsWith("status=400 code=model_not_found type=invalid_request_error param=model")
                .doesNotContain("秘密の文")
                .doesNotContain(TOKEN)
                .doesNotContain(INSTRUCTIONS)
                .doesNotContain(INPUT);
    }

    @Test
    void 入口のエラーの形_detailだけ_も警告のログに足す() throws IOException {
        signIn(EXPIRES_LATER);
        responder = exchange -> sseError(exchange, 401, "{\"detail\":\"Not authenticated\"}");

        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("サインインし直して");

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).endsWith("status=401 detail=Not authenticated");
    }

    @Test
    void 改行は空白にして_項目は100文字までに切る() throws IOException {
        signIn(EXPIRES_LATER);
        responder = exchange -> sseError(exchange, 400, "{\"error\":{\"code\":\"x\\ny\"}}");
        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class);
        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).endsWith("status=400 code=x y");

        requests.clear();
        responder = exchange -> sseError(exchange, 400, "{\"error\":{\"code\":\"" + "a".repeat(150) + "\"}}");
        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class);
        assertThat(warnings()).hasSize(2);
        assertThat(warnings().get(1)).endsWith("status=400 code=" + "a".repeat(100));
    }

    @Test
    void 本文がJSONでないときは_ログはstatusだけで_例外は今までどおり() throws IOException {
        signIn(EXPIRES_LATER);
        responder = exchange -> sseError(exchange, 400, "<html>bad gateway " + INPUT + "</html>");

        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessage("AI の呼び出しに失敗しました（HTTP 400）。");

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0))
                .endsWith("ChatGPT の AI 呼び出しがエラーを返しました：status=400")
                .doesNotContain(INPUT);
    }

    @Test
    void サインインしていないときは_通信せずに例外にする() {
        responder = exchange -> sseOk(exchange, completed());

        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("サインインしていません");
        assertThat(requests).isEmpty();
    }

    @Test
    void サインインの期限が切れているときは_通信せずに例外にする() {
        signIn(EXPIRED);
        responder = exchange -> sseOk(exchange, completed());

        assertThatThrownBy(() -> client(Duration.ofSeconds(30)).generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("期限が切れ")
                .hasMessageNotContaining(TOKEN);
        assertThat(requests).isEmpty();
    }

    @Test
    void 返事が途中で止まっても_全体の制限時間で時間切れの例外になる() throws IOException {
        signIn(EXPIRES_LATER);
        // 最初の断片を送ったあと、解放されるまで何も送らない
        responder = exchange -> sseStallAfter(exchange, "event: response.output_text.delta\ndata: " + delta("途中") + "\n\n");
        ChatGptAiClient client = client(Duration.ofMillis(500));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.generate(INSTRUCTIONS, INPUT))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("制限時間内に終わりませんでした")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(INSTRUCTIONS)
                .hasMessageNotContaining(INPUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void app_ai_modeがchatgptならChatGptAiClient_dummyならDummyAiClientになる() {
        new ApplicationContextRunner()
                .withUserConfiguration(ChatGptAiClient.class, DummyAiClient.class)
                .withBean(ChatGptCredentialStore.class, () -> store)
                .withPropertyValues("app.ai.mode=chatgpt", "app.ai.model=gpt-6-luna")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(ChatGptAiClient.class);
                });
        new ApplicationContextRunner()
                .withUserConfiguration(ChatGptAiClient.class, DummyAiClient.class)
                .withBean(ChatGptCredentialStore.class, () -> store)
                .withPropertyValues("app.ai.mode=dummy")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(DummyAiClient.class);
                });
    }

    /** 偽サーバーの Responses API に向けたクライアントを作る。 */
    private ChatGptAiClient client(Duration timeout) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
        return new ChatGptAiClient(store, url, "gpt-test", timeout, CLOCK);
    }

    private void signIn(Instant expiresAt) {
        store.save(new ChatGptCredentials(TOKEN, REFRESH, null, expiresAt, "openid"));
    }

    /** このテストの間に出た警告（WARN）のログの文を、出た順に返す。 */
    private List<String> warnings() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static String delta(String text) {
        return "{\"type\":\"response.output_text.delta\",\"delta\":\"" + text + "\"}";
    }

    private static String completed() {
        return "{\"type\":\"response.completed\",\"response\":{}}";
    }

    /** 各イベントを「data: 行 ＋ 空行」の形にしてつなぐ。 */
    private static String sse(String... events) {
        StringBuilder text = new StringBuilder();
        for (String event : events) {
            text.append("data: ").append(event).append("\n\n");
        }
        return text.toString();
    }

    private static void sseOk(HttpExchange exchange, String body) throws IOException {
        sseError(exchange, 200, body);
    }

    private static void sseError(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 途中まで送ってから、テストが終わるまで応答を止める（時間切れの確認用）。 */
    private void sseStallAfter(HttpExchange exchange, String partial) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(partial.getBytes(UTF_8));
            out.flush();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            // 利用者の側が閉じたときは何もしない
        }
    }

    /** 暗号化の代わりに中身をそのまま返す偽の部品（テスト用）。 */
    private static final class IdentityProtector implements CredentialProtector {

        @Override
        public byte[] protect(byte[] plain) {
            return plain;
        }

        @Override
        public byte[] unprotect(byte[] encrypted) {
            return encrypted;
        }
    }
}
