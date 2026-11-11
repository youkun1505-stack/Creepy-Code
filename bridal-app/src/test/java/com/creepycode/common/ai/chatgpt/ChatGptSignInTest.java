package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * サインイン・状態の表示・サインアウト（ChatGptSignIn）のテスト。
 * 保存先は @TempDir の中だけ使う。トークンの交換と公開鍵（JWKS）は 127.0.0.1 の偽サーバーで、
 * ブラウザは開かず、テストの中で戻り先へ偽の戻りを送る。外のネットワークは使わない。
 */
class ChatGptSignInTest {

    private static final String TOKEN_PATH = "/api/accounts/oauth/token";
    private static final String JWKS_PATH = "/.well-known/jwks.json";
    private static final String ISSUER = "https://auth.openai.com";
    private static final String KID = "test-kid-1";
    private static final String CLIENT_ID = "client-issued-1";
    private static final String SUBJECT = "user-sub-SECRET-42";
    private static final String NAME = "テスト太郎";
    private static final String OFFICIAL_SCOPE =
            "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private HttpServer fake;
    private KeyPair keys;
    private final List<String> exchangeBodies = new CopyOnWriteArrayList<>();
    private volatile String tokenResponseBody = "{}";
    private ChatGptCredentialStore store;
    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext(TOKEN_PATH, exchange -> {
            exchangeBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, tokenResponseBody);
        });
        fake.createContext(JWKS_PATH, exchange -> reply(exchange, jwksBody()));
        fake.start();
        store = new ChatGptCredentialStore(tempDir.resolve("chatgpt-java"), new FakeProtector());
        output = new ByteArrayOutputStream();
    }

    @AfterEach
    void tearDown() {
        fake.stop(0);
    }

    @Test
    void 初回のサインインが成功すると登録情報と資格情報が保存され_画面には表示名だけが出る() throws IOException {
        FakeBrowser browser = new FakeBrowser();

        service(browser, CLOCK).signIn(freePort());

        Map<String, String> query = queryOf(browser.urls.get(0));
        assertThat(query).containsEntry("client_id", "dynamic_agent_client")
                .containsEntry("agent_name_hint", "Creepy Code Bridal App");
        assertThat(store.loadRegistration())
                .isEqualTo(Optional.of(new ChatGptRegistration(query.get("ext_agent_host_id"), CLIENT_ID)));
        Optional<ChatGptCredentials> saved = store.load();
        assertThat(saved).isPresent();
        assertThat(saved.get().accessToken()).isEqualTo("ACCESS-SECRET-7");
        assertThat(saved.get().expiresAt()).isEqualTo(CLOCK.instant().plusSeconds(3600));
        assertThat(parseForm(exchangeBodies.get(0)))
                .containsEntry("client_id", CLIENT_ID)
                .containsEntry("code", "CODE-OK");
        // サインイン用 URL は画面に出すので、その中の state・nonce だけは許す。それ以外の場所には出ないことを確かめる
        assertThat(printed()).contains(NAME)
                .doesNotContain(SUBJECT, "ACCESS-SECRET-7", "REFRESH-SECRET-8", "CODE-OK");
        String printedWithoutUrl = printed().replace(browser.urls.get(0), "");
        assertThat(printedWithoutUrl).doesNotContain(query.get("state"), query.get("nonce"));
    }

    @Test
    void 登録済みならその登録のホストとclient_idを使い_agent_name_hintは送らない() throws IOException {
        store.saveRegistration(new ChatGptRegistration("host-saved", "client-saved"));
        FakeBrowser browser = new FakeBrowser();
        browser.callbackClientId = null;
        browser.audience = "client-saved";

        service(browser, CLOCK).signIn(freePort());

        assertThat(queryOf(browser.urls.get(0)))
                .containsEntry("client_id", "client-saved")
                .containsEntry("ext_agent_host_id", "host-saved")
                .doesNotContainKey("agent_name_hint");
        assertThat(parseForm(exchangeBodies.get(0))).containsEntry("client_id", "client-saved");
        assertThat(store.loadRegistration())
                .isEqualTo(Optional.of(new ChatGptRegistration("host-saved", "client-saved")));
        assertThat(store.load()).isPresent();
    }

    @Test
    void stateが違うときは交換せず_日本語の例外になり_何も保存しない() throws IOException {
        FakeBrowser browser = new FakeBrowser();
        browser.stateToSend = "WRONG-STATE";

        assertThatThrownBy(() -> service(browser, CLOCK).signIn(freePort()))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("state");
        assertThat(exchangeBodies).isEmpty();
        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).isEmpty();
    }

    @Test
    void IDトークンのnonceが違うときは_交換のあとでも保存せず例外になる() throws IOException {
        FakeBrowser browser = new FakeBrowser();
        browser.nonceToSend = "WRONG-NONCE";

        assertThatThrownBy(() -> service(browser, CLOCK).signIn(freePort()))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("nonce");
        assertThat(exchangeBodies).hasSize(1);
        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).isEmpty();
    }

    @Test
    void IDトークンが戻らないときは保存せず例外になる() throws IOException {
        FakeBrowser browser = new FakeBrowser();
        browser.withIdToken = false;

        assertThatThrownBy(() -> service(browser, CLOCK).signIn(freePort()))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("ID トークン");
        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).isEmpty();
    }

    @Test
    void ポートが使用中なら_ブラウザを開かず_日本語の例外で止まり_何も保存しない() throws IOException {
        FakeBrowser browser = new FakeBrowser();
        try (ServerSocket busy = new ServerSocket()) {
            busy.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = busy.getLocalPort();

            assertThatThrownBy(() -> service(browser, CLOCK).signIn(port))
                    .isInstanceOf(ChatGptSignInException.class)
                    .hasMessageContaining("使用中");
        }
        assertThat(browser.urls).isEmpty();
        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).isEmpty();
    }

    @Test
    void 認可URLは最初から_貼り付けて開く案内と一緒に画面に出し_自動で開いた時の文は出さない() throws IOException {
        FakeBrowser browser = new FakeBrowser();

        service(browser, CLOCK).signIn(freePort());

        assertThat(printed()).contains("次の URL をブラウザに貼り付けて開いてください。")
                .contains(browser.urls.get(0))
                .doesNotContain("ブラウザを自動で開けませんでした", "ブラウザでサインインしてください。");
        assertThat(store.load()).isPresent();
    }

    @Test
    void 未サインインのときは未サインインと表示し_通信はしない() {
        service(new FakeBrowser(), CLOCK).status();

        assertThat(printed()).contains("未サインイン");
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void 期限の前ならサインイン済みと期限を表示し_通信はしない() {
        store.save(new ChatGptCredentials("ACCESS-STATUS", "REFRESH-STATUS", null,
                CLOCK.instant().plusSeconds(3600), OFFICIAL_SCOPE));

        service(new FakeBrowser(), CLOCK).status();

        assertThat(printed()).contains("サインイン済み（期限：2026-10-27 20:00）")
                .doesNotContain("ACCESS-STATUS", "REFRESH-STATUS");
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void 期限を過ぎていれば期限切れと表示する() {
        store.save(new ChatGptCredentials("ACCESS-STATUS", "REFRESH-STATUS", null,
                CLOCK.instant().plusSeconds(3600), OFFICIAL_SCOPE));

        service(new FakeBrowser(), Clock.offset(CLOCK, Duration.ofHours(2))).status();

        assertThat(printed()).contains("期限切れ（期限：2026-10-27 20:00）");
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void サインアウトは資格情報を消し_登録情報は残す() {
        store.saveRegistration(new ChatGptRegistration("host-1", CLIENT_ID));
        store.save(new ChatGptCredentials("ACCESS-OUT", "REFRESH-OUT", null,
                CLOCK.instant().plusSeconds(3600), OFFICIAL_SCOPE));

        service(new FakeBrowser(), CLOCK).signOut();

        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).isEqualTo(Optional.of(new ChatGptRegistration("host-1", CLIENT_ID)));
        assertThat(printed()).contains("消しました");
    }

    /**
     * サインインの操作を作る。画面に出された認可 URL（偽の接続先の認可の行）を、ブラウザの代わり（browser）に渡す。
     * 本物は利用者がブラウザに貼り付けるが、テストでは貼り付けの代わりに browser が戻りを送る。
     */
    private ChatGptSignIn service(FakeBrowser browser, Clock clock) {
        String base = "http://127.0.0.1:" + fake.getAddress().getPort();
        ChatGptEndpoints endpoints = new ChatGptEndpoints(base + "/api/accounts/authorize", base + TOKEN_PATH);
        PrintStream screen = new PrintStream(output, true, StandardCharsets.UTF_8) {
            @Override
            public void println(String line) {
                super.println(line);
                if (line != null && line.startsWith(base + "/api/accounts/authorize?")) {
                    browser.open(line);
                }
            }
        };
        return new ChatGptSignIn(store,
                new ChatGptOAuthClient(endpoints, HttpClient.newHttpClient(), CLOCK, new SecureRandom()),
                new IdTokenVerifier(base + JWKS_PATH, HttpClient.newHttpClient(), CLOCK),
                WAIT, clock, screen);
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    /**
     * ブラウザの代わり。画面に出された認可 URL を受け取ると、戻り先へ偽の戻りを送る（実際のブラウザは開かない）。
     * 次に交換で返す偽のトークン（ID トークンを含む）も、ここで用意する。
     */
    class FakeBrowser {

        final List<String> urls = new CopyOnWriteArrayList<>();
        /** 戻りで送る client_id（null なら送らない）。 */
        String callbackClientId = CLIENT_ID;
        /** ID トークンの宛先（aud）。 */
        String audience = CLIENT_ID;
        /** 戻りで送る state（null なら正しい値）。 */
        String stateToSend;
        /** ID トークンに入れる nonce（null なら認可 URL の正しい値）。 */
        String nonceToSend;
        boolean withIdToken = true;

        void open(String url) {
            urls.add(url);
            Map<String, String> query = queryOf(url);
            String nonce = nonceToSend != null ? nonceToSend : query.get("nonce");
            tokenResponseBody = tokenBody(withIdToken ? idToken(nonce, audience) : null);

            String state = stateToSend != null ? stateToSend : query.get("state");
            StringBuilder callback = new StringBuilder("code=CODE-OK&state=")
                    .append(URLEncoder.encode(state, StandardCharsets.UTF_8));
            if (callbackClientId != null) {
                callback.append("&client_id=").append(URLEncoder.encode(callbackClientId, StandardCharsets.UTF_8));
            }
            int port = URI.create(query.get("redirect_uri")).getPort();
            try {
                get(port, "/auth/callback?" + callback);
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private String tokenBody(String idToken) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", "ACCESS-SECRET-7");
            body.put("refresh_token", "REFRESH-SECRET-8");
            if (idToken != null) {
                body.put("id_token", idToken);
            }
            body.put("token_type", "Bearer");
            body.put("expires_in", 3600);
            body.put("scope", OFFICIAL_SCOPE);
            return MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** テストの鍵で RS256 の ID トークンを作る。 */
    private String idToken(String nonce, String audience) {
        try {
            String header = encode(MAPPER.writeValueAsBytes(Map.of("alg", "RS256", "kid", KID, "typ", "JWT")));
            String payload = encode(MAPPER.writeValueAsBytes(Map.of("iss", ISSUER, "aud", audience,
                    "exp", 4102444800L, "sub", SUBJECT, "name", NAME, "nonce", nonce)));
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keys.getPrivate());
            signer.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + encode(signer.sign());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String jwksBody() throws IOException {
        RSAPublicKey publicKey = (RSAPublicKey) keys.getPublic();
        Map<String, Object> jwk = Map.of("kty", "RSA", "kid", KID, "alg", "RS256", "use", "sig",
                "n", encode(publicKey.getModulus().toByteArray()),
                "e", encode(publicKey.getPublicExponent().toByteArray()));
        return MAPPER.writeValueAsString(Map.of("keys", List.of(jwk)));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static HttpResponse<String> get(int port, String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 認可 URL の ? の後ろを、キーと値の組（デコード済み）にする。 */
    private static Map<String, String> queryOf(String url) {
        return parseForm(URI.create(url).getRawQuery());
    }

    /** key=value を & でつないだ形（form や URL のクエリ）を、キーと値の組（デコード済み）にする。 */
    private static Map<String, String> parseForm(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            int equal = pair.indexOf('=');
            values.put(decode(pair.substring(0, equal)), decode(pair.substring(equal + 1)));
        }
        return values;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    /** テスト用の偽の暗号化。平文をそのまま見えないように XOR する（DPAPI の代わり）。 */
    static class FakeProtector implements CredentialProtector {

        @Override
        public byte[] protect(byte[] plain) {
            return xor(plain);
        }

        @Override
        public byte[] unprotect(byte[] encrypted) {
            return xor(encrypted);
        }

        private static byte[] xor(byte[] data) {
            byte[] out = data.clone();
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) (out[i] ^ 0x5A);
            }
            return out;
        }
    }
}
