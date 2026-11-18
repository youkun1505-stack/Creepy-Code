package com.creepycode.common.ai.chatgpt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.creepycode.common.exception.AiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * アクセストークンの確認と更新（ChatGptTokenProvider）のテスト。
 * 更新の接続先は 127.0.0.1 の偽サーバー（外のネットワークは使わない）。保存先は @TempDir。暗号化は偽の部品で代用する。
 */
class ChatGptTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    /** 期限まで90分（更新しない）・3分（期限が近い）・期限切れ */
    private static final Instant LATER = Instant.parse("2026-10-28T11:30:00Z");
    private static final Instant NEAR = Instant.parse("2026-10-28T10:03:00Z");
    private static final Instant EXPIRED = Instant.parse("2026-10-28T09:50:00Z");

    private static final String OLD_ACCESS = "OLD-ACCESS-SECRET";
    private static final String OLD_REFRESH = "OLD-REFRESH-SECRET";
    private static final String NEW_ACCESS = "NEW-ACCESS-SECRET";
    private static final String NEW_REFRESH = "NEW-REFRESH-SECRET";
    private static final String BODY_SECRET = "BODY-SECRET-XYZ";
    private static final String CLIENT_ID = "client-id-test";
    private static final String HOST_ID = "urn:uuid:test-host";
    private static final String PLAN_SCOPE =
            "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final String PATH = "/oauth/token";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 偽サーバーが受け取った1回ぶんの内容（Content-Type と本文）。 */
    private record Captured(String contentType, String body) {
    }

    /** 偽サーバーの応答を決める部品。 */
    private interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @TempDir
    Path tempDir;

    private HttpServer server;
    private final List<Captured> requests = new CopyOnWriteArrayList<>();
    private volatile Responder responder;
    private CountDownLatch release;
    private Path directory;
    private CountingProtector protector;
    private ChatGptCredentialStore store;

    @BeforeEach
    void startFakeServer() throws IOException {
        directory = tempDir.resolve("chatgpt-java");
        protector = new CountingProtector();
        store = new ChatGptCredentialStore(directory, protector);
        release = new CountDownLatch(1);
        responder = exchange -> sendJson(exchange, 500, "{\"error\":\"unexpected\"}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
            requests.add(new Captured(exchange.getRequestHeaders().getFirst("Content-Type"), body));
            responder.respond(exchange);
        });
        server.start();
        store.saveRegistration(new ChatGptRegistration(HOST_ID, CLIENT_ID));
    }

    @AfterEach
    void stopFakeServer() {
        // 応答を止めているテストを先に解放してから、偽サーバーを止める
        release.countDown();
        server.stop(0);
    }

    @Test
    void 期限まで余裕があるときは_更新せずに今のトークンを返す() {
        signIn(LATER);
        responder = refreshOk("");

        ChatGptCredentials usable = provider().usable(Duration.ofSeconds(5));

        assertThat(usable.accessToken()).isEqualTo(OLD_ACCESS);
        assertThat(requests).isEmpty();
    }

    @Test
    void 期限が近いときは更新し_新しい更新用トークンを保存してから返す() {
        signIn(NEAR);
        responder = refreshOk(",\"earliest_refresh_at\":\"2026-10-28T10:20:00Z\"");

        ChatGptCredentials usable = provider().usable(Duration.ofSeconds(5));

        assertThat(usable.accessToken()).isEqualTo(NEW_ACCESS);
        ChatGptCredentials saved = store.load().orElseThrow();
        assertThat(saved.refreshToken()).isEqualTo(NEW_REFRESH);
        assertThat(saved.accessToken()).isEqualTo(NEW_ACCESS);
        assertThat(saved.earliestRefreshAt()).isEqualTo(Instant.parse("2026-10-28T10:20:00Z"));
        assertThat(requests).hasSize(1);
    }

    @Test
    void 期限が切れていれば_更新して新しいトークンを返す() {
        signIn(EXPIRED);
        responder = refreshOk("");

        assertThat(provider().usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(NEW_ACCESS);
        assertThat(requests).hasSize(1);
    }

    @Test
    void 更新の本文はformで_4つの項目だけを送り_scopeは送らない() {
        signIn(NEAR);
        responder = refreshOk("");

        provider().usable(Duration.ofSeconds(5));

        Captured sent = requests.get(0);
        assertThat(sent.contentType()).startsWith("application/x-www-form-urlencoded");
        Map<String, String> form = parseForm(sent.body());
        assertThat(form).containsOnlyKeys("grant_type", "client_id", "refresh_token", "resource")
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("client_id", CLIENT_ID)
                .containsEntry("refresh_token", OLD_REFRESH)
                .containsEntry("resource", "https://api.openai.com/v1");
    }

    @Test
    void 十か所から同時に401が来ても_更新の通信は1回だけで_全員が新しいトークンを受け取る() throws Exception {
        signIn(LATER);
        Responder ok = refreshOk("");
        responder = exchange -> {
            sleepQuietly(400);
            ok.respond(exchange);
        };
        ChatGptTokenProvider provider = provider();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatGptCredentials>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                Callable<ChatGptCredentials> call = () -> {
                    start.await();
                    return provider.afterUnauthorized(OLD_ACCESS, Duration.ofSeconds(10));
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<ChatGptCredentials> result : results) {
                assertThat(result.get(15, TimeUnit.SECONDS).accessToken()).isEqualTo(NEW_ACCESS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired",
            "refresh_token_invalidated", "refresh_token_reused"})
    void 公式の6つの失敗_errorが文字の形_では資格情報を消し_client_idは残す(String error) {
        signIn(EXPIRED);
        responder = exchange -> sendJson(exchange, 400, "{\"error\":\"" + error + "\"}");

        assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("サインインし直して");
        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).contains(new ChatGptRegistration(HOST_ID, CLIENT_ID));
    }

    @Test
    void 公式の失敗_errorcodeの形_でも資格情報を消し_client_idは残す() {
        signIn(EXPIRED);
        responder = exchange -> sendJson(exchange, 400,
                "{\"error\":{\"code\":\"refresh_token_reused\",\"message\":\"" + BODY_SECRET + "\"}}");

        assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5))).isInstanceOf(AiException.class);
        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).contains(new ChatGptRegistration(HOST_ID, CLIENT_ID));
    }

    @Test
    void 応答の503では資格情報を消さない() {
        signIn(EXPIRED);
        responder = exchange -> sendJson(exchange, 503, "{\"error\":\"server_error\"}");

        assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("更新できませんでした");
        assertThat(store.load()).isPresent();
    }

    @Test
    void 通信エラーでは資格情報を消さない() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        signIn(EXPIRED);

        assertThatThrownBy(() -> provider("http://127.0.0.1:" + closedPort + PATH).usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class);
        assertThat(store.load()).isPresent();
    }

    @Test
    void invalid_clientでは資格情報を消さない() {
        signIn(EXPIRED);
        responder = exchange -> sendJson(exchange, 400, "{\"error\":\"invalid_client\"}");

        assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("アプリの登録の設定が合わない");
        assertThat(store.load()).isPresent();
    }

    @Test
    void 登録情報が無いときは_通信も消去もせずサインインが必要な例外にする() {
        signIn(EXPIRED);
        // 登録情報だけを消して、期限の切れた資格情報が残っている状態にする
        deleteRegistration();
        responder = refreshOk("");

        assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("期限が切れ");
        assertThat(requests).isEmpty();
        assertThat(store.load()).isPresent();
    }

    @Test
    void 期限の前の更新が失敗しても_まだ期限内の今のトークンを使う() {
        signIn(NEAR);
        responder = exchange -> sendJson(exchange, 503, "{\"error\":\"server_error\"}");

        assertThat(provider().usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(OLD_ACCESS);
        assertThat(store.load()).isPresent();
    }

    @Test
    void earliest_refresh_atより前は_期限が近くても更新しない() {
        store.save(new ChatGptCredentials(OLD_ACCESS, OLD_REFRESH, null, NEAR, PLAN_SCOPE,
                Instant.parse("2026-10-28T10:30:00Z")));
        responder = refreshOk("");

        assertThat(provider().usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(OLD_ACCESS);
        assertThat(requests).isEmpty();
    }

    @Test
    void 期限が切れていれば_earliest_refresh_atより前でも更新する() {
        store.save(new ChatGptCredentials(OLD_ACCESS, OLD_REFRESH, null, EXPIRED, PLAN_SCOPE,
                Instant.parse("2026-10-28T10:30:00Z")));
        responder = refreshOk("");

        assertThat(provider().usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(NEW_ACCESS);
    }

    @Test
    void earliest_refresh_atは数値の秒とISOの文字を読み_読めなければ無いものとする() throws IOException {
        assertThat(ChatGptOAuthClient.parseEarliest(JSON.readTree("1793193600")))
                .isEqualTo(Instant.ofEpochSecond(1793193600L));
        assertThat(ChatGptOAuthClient.parseEarliest(JSON.readTree("\"2026-10-28T10:20:00Z\"")))
                .isEqualTo(Instant.parse("2026-10-28T10:20:00Z"));
        assertThat(ChatGptOAuthClient.parseEarliest(JSON.readTree("\"2026-10-28T19:20:00+09:00\"")))
                .isEqualTo(Instant.parse("2026-10-28T10:20:00Z"));
        assertThat(ChatGptOAuthClient.parseEarliest(JSON.readTree("\"だめな値\""))).isNull();
        assertThat(ChatGptOAuthClient.parseEarliest(JSON.readTree("{}"))).isNull();
        assertThat(ChatGptOAuthClient.parseEarliest(null)).isNull();
    }

    @Test
    void 応答のscopeにプランの許可が無いときは例外にし_新しい更新用トークンは保存する() {
        signIn(EXPIRED);
        responder = exchange -> sendJson(exchange, 200,
                "{\"access_token\":\"" + NEW_ACCESS + "\",\"refresh_token\":\"" + NEW_REFRESH
                        + "\",\"expires_in\":3600,\"scope\":\"openid\"}");

        assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("プランの利用が許可されていません");
        // 応答の時点でサーバー側の更新用トークンは替わっているので、新しいものを保存する（古いものはもう使えない）
        assertThat(store.load().orElseThrow().refreshToken()).isEqualTo(NEW_REFRESH);
    }

    @Test
    void 応答の形が想定と違っても_新しい更新用トークンは保存する() {
        signIn(EXPIRED);
        responder = exchange -> sendJson(exchange, 200,
                "{\"refresh_token\":\"" + NEW_REFRESH + "\",\"expires_in\":3600,\"scope\":\"" + PLAN_SCOPE + "\"}");

        assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("想定と違います");
        ChatGptCredentials saved = store.load().orElseThrow();
        assertThat(saved.refreshToken()).isEqualTo(NEW_REFRESH);
        assertThat(saved.accessToken()).isEqualTo(OLD_ACCESS);
    }

    @Test
    void 本文が途中で止まっても_更新の通信を打ち切り_有効なトークンで進み_次の更新を妨げない() throws Exception {
        signIn(NEAR);
        responder = exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                // 本文の途中まで送って止める（ヘッダーは届いている）
                out.write("{\"access_token\":".getBytes(UTF_8));
                out.flush();
                awaitRelease();
            }
        };
        ChatGptTokenProvider provider = provider(tokenUrl(), Duration.ofSeconds(1), Duration.ZERO);

        long start = System.nanoTime();
        // 期限内なので、更新が止まっても今のトークンで進む。待ちは短い上限で打ち切る
        assertThat(provider.usable(Duration.ofSeconds(30)).accessToken()).isEqualTo(OLD_ACCESS);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertThat(store.load().orElseThrow().refreshToken()).isEqualTo(OLD_REFRESH);

        // 止まっていた応答を解放し、次の呼び出しで更新できる（進行中の印が残っていないこと）
        release.countDown();
        responder = refreshOk("");
        assertThat(provider.usable(Duration.ofSeconds(30)).accessToken()).isEqualTo(NEW_ACCESS);
    }

    @Test
    void 更新が失敗したあとは_短い間隔のあいだ_新しい通信を始めない() {
        signIn(NEAR);
        responder = exchange -> sendJson(exchange, 503, "{\"error\":\"server_error\"}");
        ChatGptTokenProvider provider = provider();

        assertThat(provider.usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(OLD_ACCESS);
        assertThat(provider.usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(OLD_ACCESS);
        assertThat(requests).hasSize(1);
    }

    @Test
    void 読み込みが一時的に失敗しても_更新時刻を覚えず_次の呼び出しで読み直す() {
        signIn(LATER);
        protector.failUnprotects.set(1);
        ChatGptTokenProvider provider = provider();

        assertThatThrownBy(() -> provider.usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("サインインしていません");
        assertThat(provider.usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(OLD_ACCESS);
    }

    @Test
    void earliest_refresh_atの前でも_401なら1回だけ更新して新しいトークンを返す() {
        store.save(new ChatGptCredentials(OLD_ACCESS, OLD_REFRESH, null, LATER, PLAN_SCOPE,
                Instant.parse("2026-10-28T10:30:00Z")));
        responder = refreshOk("");

        assertThat(provider().afterUnauthorized(OLD_ACCESS, Duration.ofSeconds(5)).accessToken())
                .isEqualTo(NEW_ACCESS);
        assertThat(requests).hasSize(1);
    }

    private String tokenUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
    }

    @Test
    void 保存に失敗したら例外にする() {
        signIn(EXPIRED);
        protector.failProtect = true;
        responder = refreshOk("");
        ChatGptTokenProvider provider = provider();

        assertThatThrownBy(() -> provider.usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("保存できませんでした");
        // 保存は失敗したが、今の起動の中では新しいトークンを使える（次の呼び出しは通信しない）
        assertThat(provider.usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(NEW_ACCESS);
        assertThat(requests).hasSize(1);
    }

    @Test
    void 呼び出しの時間切れがあっても_届いた更新の結果は保存される() throws Exception {
        signIn(EXPIRED);
        responder = exchange -> {
            awaitRelease();
            refreshOk("").respond(exchange);
        };

        assertThatThrownBy(() -> provider().usable(Duration.ofMillis(100)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("制限時間内に終わりませんでした");

        release.countDown();
        awaitSavedRefreshToken(NEW_REFRESH);
        assertThat(store.load().orElseThrow().accessToken()).isEqualTo(NEW_ACCESS);
    }

    @Test
    void 呼び出しの中断があっても_届いた更新の結果は保存される() throws Exception {
        signIn(EXPIRED);
        responder = exchange -> {
            awaitRelease();
            refreshOk("").respond(exchange);
        };
        ChatGptTokenProvider provider = provider();

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> provider.usable(Duration.ofSeconds(5)))
                    .isInstanceOf(AiException.class)
                    .hasMessageContaining("中断されました");
        } finally {
            Thread.interrupted();
        }

        release.countDown();
        awaitSavedRefreshToken(NEW_REFRESH);
    }

    @Test
    void ファイルが別の場所で書き換わったら_新しい資格情報を使い_毎回は復号しない() throws IOException {
        signIn(LATER);
        ChatGptTokenProvider provider = provider();
        assertThat(provider.usable(Duration.ofSeconds(5)).accessToken()).isEqualTo(OLD_ACCESS);
        int decryptedOnce = protector.unprotects.get();

        provider.usable(Duration.ofSeconds(5));
        provider.usable(Duration.ofSeconds(5));
        assertThat(protector.unprotects.get()).isEqualTo(decryptedOnce);

        // 別のプログラムでサインインし直した（ファイルの更新時刻が進む）
        store.save(new ChatGptCredentials("RESIGNED-ACCESS", "RESIGNED-REFRESH", null,
                Instant.parse("2026-10-28T12:00:00Z"), PLAN_SCOPE));
        Path file = directory.resolve("credentials.dat");
        Files.setLastModifiedTime(file, FileTime.from(Files.getLastModifiedTime(file).toInstant().plusSeconds(10)));
        assertThat(provider.usable(Duration.ofSeconds(5)).accessToken()).isEqualTo("RESIGNED-ACCESS");
        assertThat(requests).isEmpty();

        // 別のプログラムでサインアウトした
        store.clear();
        assertThatThrownBy(() -> provider.usable(Duration.ofSeconds(5)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("サインインしていません");
    }

    @Test
    void 更新の失敗の本文は_例外にもログにも出ない() {
        signIn(EXPIRED);
        responder = exchange -> sendJson(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\""
                + BODY_SECRET + "\",\"refresh_token\":\"" + BODY_SECRET + "\"}");
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        Logger providerLogger = (Logger) LoggerFactory.getLogger(ChatGptTokenProvider.class);
        Logger oauthLogger = (Logger) LoggerFactory.getLogger(ChatGptOAuthClient.class);
        providerLogger.addAppender(logs);
        oauthLogger.addAppender(logs);
        try {
            assertThatThrownBy(() -> provider().usable(Duration.ofSeconds(5)))
                    .isInstanceOf(AiException.class)
                    .hasMessageNotContaining(BODY_SECRET)
                    .hasMessageNotContaining(OLD_REFRESH);
            assertThat(logs.list).isNotEmpty();
            assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(BODY_SECRET)
                    || event.getFormattedMessage().contains(OLD_REFRESH));
        } finally {
            providerLogger.detachAppender(logs);
            oauthLogger.detachAppender(logs);
        }
    }

    /** 偽サーバーの更新の応答（成功）。extra は JSON の項目を足す場合に「,"name":value」の形で渡す。 */
    private static Responder refreshOk(String extra) {
        return exchange -> sendJson(exchange, 200, "{\"access_token\":\"" + NEW_ACCESS + "\",\"refresh_token\":\""
                + NEW_REFRESH + "\",\"id_token\":\"NEW-ID\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
                + "\"scope\":\"" + PLAN_SCOPE + "\"" + extra + "}");
    }

    private ChatGptTokenProvider provider() {
        return provider("http://127.0.0.1:" + server.getAddress().getPort() + PATH);
    }

    private ChatGptTokenProvider provider(String tokenUrl) {
        return provider(tokenUrl, Duration.ofSeconds(20), ChatGptTokenProvider.DEFAULT_RETRY_INTERVAL);
    }

    private ChatGptTokenProvider provider(String tokenUrl, Duration refreshTimeout, Duration retryInterval) {
        ChatGptOAuthClient oauth = new ChatGptOAuthClient(
                new ChatGptEndpoints(ChatGptEndpoints.OFFICIAL_AUTHORIZE_URL, tokenUrl),
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), CLOCK, new SecureRandom(),
                refreshTimeout);
        return new ChatGptTokenProvider(store, oauth, CLOCK, retryInterval);
    }

    private void signIn(Instant expiresAt) {
        store.save(new ChatGptCredentials(OLD_ACCESS, OLD_REFRESH, null, expiresAt, PLAN_SCOPE));
    }

    private void deleteRegistration() {
        try {
            Files.delete(directory.resolve("registration.json"));
        } catch (IOException e) {
            throw new IllegalStateException("テストの準備に失敗しました", e);
        }
    }

    /** 保存された更新用トークンが期待の値になるまで待つ（別のスレッドで保存されるため）。 */
    private void awaitSavedRefreshToken(String expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (store.load().map(ChatGptCredentials::refreshToken).filter(expected::equals).isPresent()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("更新の結果が保存されませんでした");
    }

    private void awaitRelease() {
        try {
            release.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** form 形式（key=value を & でつないだもの）を読む。 */
    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            form.put(URLDecoder.decode(pair.substring(0, equals), UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), UTF_8));
        }
        return form;
    }

    /** 暗号化の代わりに中身をそのまま返す偽の部品。復号の回数を数え、保存と復号の失敗も起こせる。 */
    private static final class CountingProtector implements CredentialProtector {

        final AtomicInteger unprotects = new AtomicInteger();
        /** この回数ぶんだけ、次の復号を失敗させる（一時的な読み込みの失敗の再現）。 */
        final AtomicInteger failUnprotects = new AtomicInteger();
        volatile boolean failProtect;

        @Override
        public byte[] protect(byte[] plain) {
            if (failProtect) {
                throw new IllegalStateException("保存できません（テスト）");
            }
            return plain;
        }

        @Override
        public byte[] unprotect(byte[] encrypted) {
            unprotects.incrementAndGet();
            if (failUnprotects.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new IllegalStateException("一時的に復号できません（テスト）");
            }
            return encrypted;
        }
    }
}
