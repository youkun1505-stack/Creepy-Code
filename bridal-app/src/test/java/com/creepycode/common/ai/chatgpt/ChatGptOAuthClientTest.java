package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Sign in with ChatGPT の OAuth（ChatGptOAuthClient・PkceCodes・ChatGptCallback・ChatGptAuthAttempt）のテスト。
 * 外のネットワークは使わない。トークン交換は 127.0.0.1 の偽サーバーで確かめる。
 */
class ChatGptOAuthClientTest {

    private static final String TOKEN_PATH = "/api/accounts/oauth/token";
    private static final String RFC_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String OFFICIAL_SCOPE =
            "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private static final String HOST = "urn:uuid:11111111-2222-4333-8444-555555555555";
    private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");

    private HttpServer server;
    private final List<String> exchangeBodies = new CopyOnWriteArrayList<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void startFakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TOKEN_PATH, exchange -> {
            exchangeBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeServer() {
        server.stop(0);
    }

    private ChatGptOAuthClient fakeClient() {
        ChatGptEndpoints endpoints = new ChatGptEndpoints(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/accounts/authorize",
                "http://127.0.0.1:" + server.getAddress().getPort() + TOKEN_PATH);
        return new ChatGptOAuthClient(endpoints, HttpClient.newHttpClient(), CLOCK, new SecureRandom());
    }

    private static ChatGptOAuthClient officialClient() {
        return new ChatGptOAuthClient(ChatGptEndpoints.official());
    }

    /** 認可 URL や form の値を、キーと値の組（デコード済み）にする。 */
    private static Map<String, String> parse(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int equal = pair.indexOf('=');
            values.put(decode(pair.substring(0, equal)), decode(pair.substring(equal + 1)));
        }
        return values;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static Map<String, String> queryOf(String url) {
        return parse(URI.create(url).getRawQuery());
    }

    private static ChatGptCallback successCallback(ChatGptAuthAttempt attempt, String code, String clientId) {
        return new ChatGptCallback(code, attempt.state(), clientId, null);
    }

    @Test
    void PKCEはRFC7636の付録Bの値と一致する() {
        PkceCodes pkce = PkceCodes.fromVerifier(RFC_VERIFIER);

        assertThat(pkce.challenge()).isEqualTo(RFC_CHALLENGE);
    }

    @Test
    void PKCEのverifierは安全な乱数で作られ_challengeはS256で計算される() {
        PkceCodes first = PkceCodes.create(new SecureRandom());
        PkceCodes second = PkceCodes.create(new SecureRandom());

        assertThat(first.verifier()).hasSize(43).matches(BASE64URL);
        assertThat(first.challenge()).isEqualTo(PkceCodes.fromVerifier(first.verifier()).challenge());
        assertThat(second.verifier()).isNotEqualTo(first.verifier());
    }

    @Test
    void PKCEのtoStringにverifierが出ない() {
        assertThat(PkceCodes.fromVerifier(RFC_VERIFIER).toString()).doesNotContain(RFC_VERIFIER);
    }

    @Test
    void 既定の接続先は公式のURLになる() {
        assertThat(ChatGptEndpoints.official().authorizeUrl())
                .isEqualTo("https://auth.openai.com/api/accounts/authorize");
        assertThat(ChatGptEndpoints.official().tokenUrl())
                .isEqualTo("https://auth.openai.com/api/accounts/oauth/token");
    }

    @Test
    void 初回の認可URLに必要な項目が入り_localhostは入らない() {
        ChatGptAuthAttempt attempt = officialClient().startAuthorization(1455, HOST, null);

        assertThat(attempt.authorizationUrl()).startsWith("https://auth.openai.com/api/accounts/authorize?");
        assertThat(attempt.authorizationUrl()).doesNotContain("localhost");
        Map<String, String> query = queryOf(attempt.authorizationUrl());
        assertThat(query)
                .containsEntry("response_type", "code")
                .containsEntry("client_id", "dynamic_agent_client")
                .containsEntry("agent_name_hint", "Creepy Code Bridal App")
                .containsEntry("ext_agent_host_id", attempt.hostId())
                .containsEntry("redirect_uri", "http://127.0.0.1:1455/auth/callback")
                .containsEntry("scope", OFFICIAL_SCOPE)
                .containsEntry("resource", "https://api.openai.com/v1")
                .containsEntry("state", attempt.state())
                .containsEntry("nonce", attempt.nonce())
                .containsEntry("code_challenge", PkceCodes.fromVerifier(attempt.codeVerifier()).challenge())
                .containsEntry("code_challenge_method", "S256");
        assertThat(attempt.isInitialRegistration()).isTrue();
        assertThat(attempt.redirectUri()).isEqualTo("http://127.0.0.1:1455/auth/callback");
    }

    @Test
    void 新しいホストの値はurn_uuidの形で_毎回別の値になる() {
        String first = ChatGptOAuthClient.newHostId();
        String second = ChatGptOAuthClient.newHostId();

        assertThat(first).startsWith("urn:uuid:");
        assertThat(UUID.fromString(first.substring("urn:uuid:".length()))).isNotNull();
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void 同じホストの値を渡せば_試すたびに同じext_agent_host_idが送られる() {
        ChatGptOAuthClient client = officialClient();

        ChatGptAuthAttempt first = client.startAuthorization(1455, HOST, null);
        ChatGptAuthAttempt second = client.startAuthorization(1455, HOST, null);

        assertThat(queryOf(first.authorizationUrl())).containsEntry("ext_agent_host_id", HOST);
        assertThat(queryOf(second.authorizationUrl())).containsEntry("ext_agent_host_id", HOST);
        assertThat(second.hostId()).isEqualTo(HOST);
    }

    @Test
    void 登録済みと違うホストの値では認可URLを作らない() {
        assertThatThrownBy(() -> officialClient().startAuthorization(1455, "urn:uuid:other",
                new ChatGptRegistration(HOST, "client-issued-9")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateとnonceは毎回別の値になる() {
        ChatGptOAuthClient client = officialClient();

        ChatGptAuthAttempt first = client.startAuthorization(1455, HOST, null);
        ChatGptAuthAttempt second = client.startAuthorization(1455, HOST, null);

        assertThat(first.state()).isNotBlank().isNotEqualTo(second.state());
        assertThat(first.nonce()).isNotBlank().isNotEqualTo(second.nonce());
        assertThat(first.codeVerifier()).isNotEqualTo(second.codeVerifier());
    }

    @Test
    void 登録済みの認可URLは発行されたclient_idを使い_agent_name_hintは送らず_ext_agent_host_idは送る() {
        ChatGptRegistration registration = new ChatGptRegistration("host-saved", "client-issued-9");

        ChatGptAuthAttempt attempt = officialClient().startAuthorization(1455, "host-saved", registration);

        Map<String, String> query = queryOf(attempt.authorizationUrl());
        assertThat(query).containsEntry("client_id", "client-issued-9")
                .containsEntry("ext_agent_host_id", "host-saved")
                .doesNotContainKey("agent_name_hint");
        assertThat(attempt.hostId()).isEqualTo("host-saved");
        assertThat(attempt.isInitialRegistration()).isFalse();
        assertThat(attempt.registeredClientId()).isEqualTo("client-issued-9");
        assertThat(attempt.authorizationUrl()).doesNotContain("localhost");
    }

    @Test
    void ポートは1から65535の外だと例外になる() {
        assertThatThrownBy(() -> officialClient().startAuthorization(0, HOST, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> officialClient().startAuthorization(65536, HOST, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 控えのtoStringに秘密の値が出ない() {
        ChatGptAuthAttempt attempt = officialClient().startAuthorization(1455, HOST, null);

        assertThat(attempt.toString())
                .doesNotContain(attempt.state(), attempt.nonce(), attempt.codeVerifier(),
                        attempt.authorizationUrl());
    }

    @Test
    void 戻りの値はクエリから読めて_codeとstateはtoStringに出ない() {
        ChatGptCallback callback = ChatGptCallback.fromQuery("code=abc%2B1&state=st-1&client_id=cid-2");

        assertThat(callback.code()).isEqualTo("abc+1");
        assertThat(callback.state()).isEqualTo("st-1");
        assertThat(callback.clientId()).isEqualTo("cid-2");
        assertThat(callback.error()).isNull();
        assertThat(callback.toString()).doesNotContain("abc+1", "st-1").contains("cid-2");
    }

    @Test
    void stateが合わないときは交換せず例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);

        assertThatThrownBy(() -> client.completeSignIn(attempt,
                new ChatGptCallback("CODE-X", "WRONG-STATE", "cid", null)))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("state");
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void 初回で発行されたclient_idが戻りに無いときは交換せず例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);

        assertThatThrownBy(() -> client.completeSignIn(attempt,
                new ChatGptCallback("CODE-X", attempt.state(), null, null)))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("発行");
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void access_deniedのときは交換せず例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);

        assertThatThrownBy(() -> client.completeSignIn(attempt,
                new ChatGptCallback(null, attempt.state(), null, "access_denied")))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("キャンセル");
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void 認可コードが無いときは交換せず例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);

        assertThatThrownBy(() -> client.completeSignIn(attempt,
                new ChatGptCallback(null, attempt.state(), "cid", null)))
                .isInstanceOf(ChatGptSignInException.class);
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void 偽サーバーでの交換の項目が正しく_資格情報と登録情報ができ_期限は今の時刻に含まれる秒数で決まる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseBody = """
                {"access_token":"ACCESS-TOKEN-TEST","refresh_token":"REFRESH-TOKEN-TEST",\
                "id_token":"ID-TOKEN-TEST","token_type":"Bearer","expires_in":3600,\
                "scope":"openid profile chatgpt.tokens.use.direct","earliest_refresh_at":1800000000}
                """;

        ChatGptSignInResult result = client.completeSignIn(attempt,
                successCallback(attempt, "CODE-TEST", "client-issued-1"));

        assertThat(exchangeBodies).hasSize(1);
        Map<String, String> form = parse(exchangeBodies.get(0));
        assertThat(form)
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("client_id", "client-issued-1")
                .containsEntry("code", "CODE-TEST")
                .containsEntry("code_verifier", attempt.codeVerifier())
                .containsEntry("redirect_uri", "http://127.0.0.1:1455/auth/callback")
                .containsEntry("resource", "https://api.openai.com/v1");
        assertThat(form).hasSize(6);
        ChatGptCredentials credentials = result.credentials();
        assertThat(credentials.accessToken()).isEqualTo("ACCESS-TOKEN-TEST");
        assertThat(credentials.refreshToken()).isEqualTo("REFRESH-TOKEN-TEST");
        assertThat(credentials.idToken()).isEqualTo("ID-TOKEN-TEST");
        assertThat(credentials.scope()).isEqualTo("openid profile chatgpt.tokens.use.direct");
        assertThat(credentials.expiresAt()).isEqualTo(CLOCK.instant().plusSeconds(3600));
        assertThat(result.registration()).isEqualTo(new ChatGptRegistration(attempt.hostId(), "client-issued-1"));
        assertThat(credentials.toString()).doesNotContain("ACCESS-TOKEN-TEST", "REFRESH-TOKEN-TEST", "ID-TOKEN-TEST");
    }

    @Test
    void 登録済みの交換では登録済みのclient_idを送る() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, "host-saved",
                new ChatGptRegistration("host-saved", "client-saved"));
        responseBody = "{\"access_token\":\"A\",\"refresh_token\":\"R\",\"expires_in\":60,\"scope\":\""
                + OFFICIAL_SCOPE + "\"}";

        ChatGptSignInResult result = client.completeSignIn(attempt,
                new ChatGptCallback("CODE-SAVED", attempt.state(), null, null));

        assertThat(parse(exchangeBodies.get(0))).containsEntry("client_id", "client-saved");
        assertThat(result.registration()).isEqualTo(new ChatGptRegistration("host-saved", "client-saved"));
        assertThat(result.credentials().idToken()).isNull();
    }

    @Test
    void 応答のscopeにプランの許可が無ければ例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseBody = "{\"access_token\":\"A\",\"refresh_token\":\"R\",\"expires_in\":3600,"
                + "\"scope\":\"openid profile email\"}";

        assertThatThrownBy(() -> client.completeSignIn(attempt, successCallback(attempt, "CODE", "cid")))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("プランの利用が許可されていません");
    }

    @Test
    void 交換が200以外なら例外になり_本文もコードもverifierも出ない() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseStatus = 400;
        responseBody = "{\"error\":\"invalid_grant\",\"error_description\":\"SECRET-BODY-XYZ\"}";

        assertThatThrownBy(() -> client.completeSignIn(attempt, successCallback(attempt, "CODE-SECRET-2", "cid")))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("400")
                .hasMessageNotContaining("SECRET-BODY-XYZ")
                .hasMessageNotContaining("CODE-SECRET-2")
                .hasMessageNotContaining(attempt.codeVerifier())
                .hasMessageNotContaining("invalid_grant")
                .hasNoCause();
    }

    @Test
    void 応答の本文を読めないときも本文は出ず_原因も付かない() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseBody = "not-json SECRET-BODY-ABC {";

        assertThatThrownBy(() -> client.completeSignIn(attempt, successCallback(attempt, "CODE", "cid")))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageNotContaining("SECRET-BODY-ABC")
                .hasNoCause();
    }

    @Test
    void 応答の項目が足りないときは例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseBody = "{\"access_token\":\"A\",\"scope\":\"" + OFFICIAL_SCOPE + "\"}";

        assertThatThrownBy(() -> client.completeSignIn(attempt, successCallback(attempt, "CODE", "cid")))
                .isInstanceOf(ChatGptSignInException.class);
    }

    @Test
    void 接続先はfakeのURLに差し替えられ_公式のURLへは行かない() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseBody = "{\"access_token\":\"A\",\"refresh_token\":\"R\",\"expires_in\":60,\"scope\":\""
                + OFFICIAL_SCOPE + "\"}";

        client.completeSignIn(attempt, successCallback(attempt, "CODE", "cid"));

        assertThat(exchangeBodies).hasSize(1);
        assertThat(attempt.authorizationUrl()).startsWith("http://127.0.0.1:");
    }

    @Test
    void 登録済みで戻りのclient_idが保存済みと違えば交換せず例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, "host-saved",
                new ChatGptRegistration("host-saved", "client-saved"));

        assertThatThrownBy(() -> client.completeSignIn(attempt,
                new ChatGptCallback("CODE-OTHER", attempt.state(), "client-other", null)))
                .isInstanceOf(ChatGptSignInException.class);
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void 初回で戻りのclient_idがdynamic_agent_clientなら交換せず例外になる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);

        assertThatThrownBy(() -> client.completeSignIn(attempt,
                new ChatGptCallback("CODE-X", attempt.state(), "dynamic_agent_client", null)))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("発行");
        assertThat(exchangeBodies).isEmpty();
    }

    @Test
    void 初回の交換がinvalid_grantなら発行IDを例外から取り出せ_やり直しの印が付く() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseStatus = 400;
        responseBody = "{\"error\":\"invalid_grant\",\"error_description\":\"SECRET-BODY-GRANT\"}";

        try {
            client.completeSignIn(attempt, successCallback(attempt, "CODE-G", "client-issued-7"));
            throw new AssertionError("例外になるはず");
        } catch (ChatGptSignInException e) {
            assertThat(e.canRetryWithIssuedClientId()).isTrue();
            assertThat(e.issuedClientId()).isEqualTo("client-issued-7");
            assertThat(e.getMessage()).doesNotContain("client-issued-7", "SECRET-BODY-GRANT", "CODE-G",
                    attempt.codeVerifier(), "invalid_grant");
        }
    }

    @Test
    void invalid_grant以外の400ではやり直しの印が付かない() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        responseStatus = 400;
        responseBody = "{\"error\":\"invalid_client\"}";

        try {
            client.completeSignIn(attempt, successCallback(attempt, "CODE-C", "client-issued-8"));
            throw new AssertionError("例外になるはず");
        } catch (ChatGptSignInException e) {
            assertThat(e.canRetryWithIssuedClientId()).isFalse();
            assertThat(e.issuedClientId()).isNull();
        }
    }

    @Test
    void 例外の文言にcode_state_verifierが出ないことを取り消しの場合でも確かめる() {
        ChatGptOAuthClient client = fakeClient();
        ChatGptAuthAttempt attempt = client.startAuthorization(1455, HOST, null);
        List<String> messages = new ArrayList<>();

        try {
            client.completeSignIn(attempt, new ChatGptCallback("CODE-DENIED", attempt.state(), null, "access_denied"));
        } catch (ChatGptSignInException e) {
            messages.add(e.getMessage());
        }

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).doesNotContain("CODE-DENIED", attempt.state(), attempt.codeVerifier());
    }
}
