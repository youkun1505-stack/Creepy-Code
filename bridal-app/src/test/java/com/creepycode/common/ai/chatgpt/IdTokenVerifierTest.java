package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * ID トークンの確かめ（IdTokenVerifier）のテスト。
 * 鍵はテストの中で作り、公開鍵は 127.0.0.1 の偽の JWKS で返す。外のネットワークは使わない。
 */
class IdTokenVerifierTest {

    private static final String JWKS_PATH = "/.well-known/jwks.json";
    private static final String CLIENT_ID = "app_test_client_id";
    private static final String NONCE = "nonce-for-test-0001";
    private static final String KID = "test-key-1";
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static KeyPair signingKeys;
    private static KeyPair otherKeys;

    private HttpServer server;
    private final AtomicInteger jwksRequests = new AtomicInteger();
    private volatile int jwksStatus = 200;
    private volatile String jwksBody;

    @BeforeAll
    static void makeKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKeys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    @BeforeEach
    void startFakeJwksServer() throws Exception {
        jwksBody = jwksOf(signingKeys, KID);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(JWKS_PATH, exchange -> {
            jwksRequests.incrementAndGet();
            byte[] bytes = jwksBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(jwksStatus, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeJwksServer() {
        server.stop(0);
    }

    private IdTokenVerifier verifier() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + JWKS_PATH;
        return new IdTokenVerifier(url, HttpClient.newHttpClient(), CLOCK);
    }

    /** 正しい内容の本文（ペイロード）。テストごとに一部を書き換えて使う。 */
    private static Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", IdTokenVerifier.ISSUER);
        payload.put("aud", CLIENT_ID);
        payload.put("sub", "user-test-0001");
        payload.put("name", "山田 花子");
        payload.put("nonce", NONCE);
        payload.put("iat", NOW.getEpochSecond());
        payload.put("exp", NOW.plusSeconds(3600).getEpochSecond());
        return payload;
    }

    private static Map<String, Object> rs256Header() {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("kid", KID);
        header.put("typ", "JWT");
        return header;
    }

    /** ヘッダーとペイロードを、鍵で RS256 の署名をつけて ID トークンの文字にする。 */
    private static String sign(Map<String, Object> header, Map<String, Object> payload, PrivateKey key)
            throws Exception {
        String signingInput = b64(JSON.writeValueAsBytes(header)) + "." + b64(JSON.writeValueAsBytes(payload));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + b64(signer.sign());
    }

    private static String validToken() throws Exception {
        return sign(rs256Header(), validPayload(), signingKeys.getPrivate());
    }

    /** 本文だけを差し替えて、署名は元のまま残した ID トークン（改ざんの確かめ用）。 */
    private static String withPayload(String token, Map<String, Object> payload) throws Exception {
        String[] parts = token.split("\\.", -1);
        return parts[0] + "." + b64(JSON.writeValueAsBytes(payload)) + "." + parts[2];
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** RS256 の公開鍵を JWK の形（kty・kid・n・e）にした JWKS。 */
    private static String jwksOf(KeyPair keys, String kid) throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) keys.getPublic();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("kid", kid);
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("n", b64(publicKey.getModulus().toByteArray()));
        jwk.put("e", b64(publicKey.getPublicExponent().toByteArray()));
        Map<String, Object> jwks = new LinkedHashMap<>();
        jwks.put("keys", new Object[] { jwk });
        return JSON.writeValueAsString(jwks);
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> verifier().verify(token, CLIENT_ID, NONCE))
                .isInstanceOf(ChatGptSignInException.class);
    }

    @Test
    void 全部正しいときだけ通り_sub_と_name_を取り出せる() throws Exception {
        IdTokenVerifier.VerifiedIdToken result = verifier().verify(validToken(), CLIENT_ID, NONCE);

        assertThat(result.subject()).isEqualTo("user-test-0001");
        assertThat(result.displayName()).isEqualTo("山田 花子");
    }

    @Test
    void aud_が配列でも_client_id_を含めば通る() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("aud", new String[] { "other-client", CLIENT_ID });

        IdTokenVerifier.VerifiedIdToken result = verifier().verify(
                sign(rs256Header(), payload, signingKeys.getPrivate()), CLIENT_ID, NONCE);

        assertThat(result.subject()).isEqualTo("user-test-0001");
    }

    @Test
    void name_が無いときは_名前なし_になる() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.remove("name");

        IdTokenVerifier.VerifiedIdToken result = verifier().verify(
                sign(rs256Header(), payload, signingKeys.getPrivate()), CLIENT_ID, NONCE);

        assertThat(result.displayName()).isEqualTo("名前なし");
    }

    @Test
    void 署名が違う鍵で作ったトークンは拒否する() throws Exception {
        String token = sign(rs256Header(), validPayload(), otherKeys.getPrivate());

        assertRejected(token);
    }

    @Test
    void 本文を書き換えると署名が合わず拒否する() throws Exception {
        Map<String, Object> changed = validPayload();
        changed.put("sub", "someone-else");

        assertRejected(withPayload(validToken(), changed));
    }

    @Test
    void 発行元_iss_が違うと拒否する() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("iss", "https://example.com");

        assertRejected(sign(rs256Header(), payload, signingKeys.getPrivate()));
    }

    @Test
    void 宛先_aud_が違うと拒否する() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("aud", "another-client");

        assertRejected(sign(rs256Header(), payload, signingKeys.getPrivate()));
    }

    @Test
    void 期限_exp_が過ぎていると拒否する() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("exp", NOW.minusSeconds(60).getEpochSecond());

        assertRejected(sign(rs256Header(), payload, signingKeys.getPrivate()));
    }

    @Test
    void nonce_が違うと拒否する() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("nonce", "another-nonce");

        assertRejected(sign(rs256Header(), payload, signingKeys.getPrivate()));
    }

    @Test
    void nonce_が無いと拒否する() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.remove("nonce");

        assertRejected(sign(rs256Header(), payload, signingKeys.getPrivate()));
    }

    @Test
    void alg_が_none_や_HS256_のときは公開鍵を取りに行かずに拒否する() throws Exception {
        Map<String, Object> noneHeader = new LinkedHashMap<>();
        noneHeader.put("alg", "none");
        String noneToken = b64(JSON.writeValueAsBytes(noneHeader)) + "."
                + b64(JSON.writeValueAsBytes(validPayload())) + ".";
        Map<String, Object> hs256Header = new LinkedHashMap<>();
        hs256Header.put("alg", "HS256");
        hs256Header.put("kid", KID);
        String hs256Token = sign(hs256Header, validPayload(), signingKeys.getPrivate());

        assertRejected(noneToken);
        assertRejected(hs256Token);
        assertThat(jwksRequests.get()).isZero();
    }

    @Test
    void kid_が公開鍵の一覧に無いと拒否する() throws Exception {
        Map<String, Object> header = rs256Header();
        header.put("kid", "unknown-key");

        assertRejected(sign(header, validPayload(), signingKeys.getPrivate()));
    }

    @Test
    void 公開鍵の取得が失敗したら拒否する() throws Exception {
        jwksStatus = 503;
        String token = validToken();

        assertThatThrownBy(() -> verifier().verify(token, CLIENT_ID, NONCE))
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("503")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(token));
    }

    @Test
    void 拒否の文言に_ID_トークンの本文と_nonce_が出ない() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("nonce", "another-nonce");
        String token = sign(rs256Header(), payload, signingKeys.getPrivate());
        String payloadPart = token.split("\\.", -1)[1];

        assertThatThrownBy(() -> verifier().verify(token, CLIENT_ID, NONCE))
                .isInstanceOf(ChatGptSignInException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain(token)
                        .doesNotContain(payloadPart)
                        .doesNotContain("user-test-0001")
                        .doesNotContain("another-nonce")
                        .doesNotContain(NONCE));
    }

    @Test
    void 確かめに通った結果の表示に_ID_トークンの文字が出ない() throws Exception {
        String token = validToken();

        IdTokenVerifier.VerifiedIdToken result = verifier().verify(token, CLIENT_ID, NONCE);

        assertThat(result.toString()).doesNotContain(token).doesNotContain(token.split("\\.", -1)[2]);
    }

    @Test
    void 形が3つに分かれないトークンは拒否する() {
        assertRejected("abc.def");
    }

    @Test
    void 既定の公開鍵のURLと発行元は公式の値になる() {
        assertThat(IdTokenVerifier.OFFICIAL_JWKS_URL)
                .isEqualTo("https://auth.openai.com/.well-known/jwks.json");
        assertThat(IdTokenVerifier.ISSUER).isEqualTo("https://auth.openai.com");
    }
}
