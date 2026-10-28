package com.creepycode.common.ai.chatgpt;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sign in with ChatGPT の ID トークンを確かめる部品。外部の JWT の部品は使わず、JDK と Jackson だけで書く。
 * 公開鍵は JWKS の URL から取り、RS256 の署名・発行元（iss）・宛先（aud）・期限（exp）・nonce を確かめる。
 * 全部通ったときだけ、本人の ID（sub）と表示用の名前（name）を返す。
 * ID トークンの本文・署名・nonce は、例外の文言とログに出さない。
 */
public class IdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(IdTokenVerifier.class);

    /** 公式の JWKS の URL（署名の公開鍵の一覧）。 */
    static final String OFFICIAL_JWKS_URL = "https://auth.openai.com/.well-known/jwks.json";
    /** 発行元（iss）。これと違う ID トークンは拒否する。 */
    static final String ISSUER = "https://auth.openai.com";
    /** 署名の方式。これ以外（none や HS256 など）は、公開鍵を取りに行く前に拒否する。 */
    static final String ALGORITHM = "RS256";
    /** 名前が読めないときに表示する文言。 */
    static final String NAME_UNKNOWN = "名前なし";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private final String jwksUrl;
    private final HttpClient httpClient;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 公式の JWKS の URL・既定の HTTP クライアント・現在時刻で作る。 */
    public IdTokenVerifier() {
        this(OFFICIAL_JWKS_URL, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), Clock.systemUTC());
    }

    /**
     * @param jwksUrl    公開鍵（JWKS）の URL（テストでは 127.0.0.1 の偽サーバー）
     * @param httpClient 公開鍵の取得に使う HTTP クライアント
     * @param clock      期限の確かめに使う時刻（テストでは固定の時刻）
     */
    public IdTokenVerifier(String jwksUrl, HttpClient httpClient, Clock clock) {
        this.jwksUrl = Objects.requireNonNull(jwksUrl, "jwksUrl");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * ID トークンを確かめる。全部通ったときだけ、本人の ID と表示用の名前を返す。
     * 確かめに通らないときは、理由（トークンの文字は含まない）を入れた ChatGptSignInException を投げる。
     *
     * @param idToken  サインインで受け取った ID トークン（JWS の形）
     * @param clientId このアプリの client_id（aud と照合する）
     * @param nonce    サインインを始めたときに作った nonce（照合する）
     */
    public VerifiedIdToken verify(String idToken, String clientId, String nonce) {
        if (isBlank(idToken) || isBlank(clientId) || isBlank(nonce)) {
            throw new IllegalArgumentException("ID トークン・client_id・nonce を指定してください");
        }
        String[] parts = idToken.split("\\.", -1);
        if (parts.length != 3) {
            throw reject("ID トークンの形が想定と違います。");
        }
        // 署名の方式を最初に見る。none などは、公開鍵を取りに行く前に拒否する
        JsonNode header = readJsonPart(parts[0]);
        if (!ALGORITHM.equals(textOf(header, "alg"))) {
            throw reject("ID トークンの署名の方式が RS256 ではないため、拒否しました。");
        }
        PublicKey key = findKey(textOf(header, "kid"));
        verifySignature(parts, key);

        // 署名が通ってから本文を信じる
        JsonNode payload = readJsonPart(parts[1]);
        checkIssuer(payload);
        checkAudience(payload, clientId);
        checkExpiry(payload);
        checkNonce(payload, nonce);

        String subject = textOf(payload, "sub");
        if (subject == null) {
            throw reject("ID トークンに本人の ID（sub）がありません。");
        }
        String displayName = textOf(payload, "name");
        return new VerifiedIdToken(subject, displayName == null ? NAME_UNKNOWN : displayName);
    }

    /** JWS の本文（ヘッダーかペイロード）を JSON のオブジェクトとして読む。読めなければ例外。 */
    private JsonNode readJsonPart(String part) {
        byte[] bytes;
        try {
            bytes = decode(part);
        } catch (IllegalArgumentException e) {
            throw reject("ID トークンの形が想定と違います。");
        }
        try {
            JsonNode json = objectMapper.readTree(bytes);
            if (json == null || !json.isObject()) {
                throw reject("ID トークンの形が想定と違います。");
            }
            return json;
        } catch (IOException e) {
            // Jackson の例外の文言には本文の一部が入るので、原因は付けずに種類だけ伝える
            throw reject("ID トークンを読めませんでした。");
        }
    }

    /** 発行元で公開される鍵の一覧（JWKS）から、ヘッダーの kid の RSA 公開鍵を作る。 */
    private PublicKey findKey(String kid) {
        JsonNode keys = fetchJwks().get("keys");
        if (keys == null || !keys.isArray()) {
            throw reject("公開鍵の一覧（JWKS）の形が想定と違います。");
        }
        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode key : keys) {
            if ("RSA".equals(textOf(key, "kty")) && (kid == null || kid.equals(textOf(key, "kid")))) {
                candidates.add(key);
            }
        }
        // kid が無いときは、鍵が1つだけの場合に限って使う（候補が多いときは決められないので拒否する）
        if (candidates.isEmpty() || (kid == null && candidates.size() > 1)) {
            throw reject("ID トークンの署名の鍵が、公開鍵の一覧に見つかりませんでした。");
        }
        JsonNode key = candidates.get(0);
        String modulus = textOf(key, "n");
        String exponent = textOf(key, "e");
        if (modulus == null || exponent == null) {
            throw reject("公開鍵の値（n・e）が読めませんでした。");
        }
        try {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(new BigInteger(1, decode(modulus)), new BigInteger(1, decode(exponent))));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw reject("公開鍵の値（n・e）が読めませんでした。");
        }
    }

    /** JWKS を取り、JSON のオブジェクトとして返す。通信の失敗は、ID トークンの文言を含めずに伝える。 */
    private JsonNode fetchJwks() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUrl))
                .timeout(FETCH_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw reject("ID トークンの確かめに使う公開鍵を取得できませんでした。通信の状態を確かめてください。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw reject("公開鍵の取得が中断されました。もう一度お試しください。");
        }
        if (response.statusCode() != 200) {
            log.warn("ChatGPT の公開鍵（JWKS）の取得が失敗しました（HTTP {}）", response.statusCode());
            throw reject("ID トークンの確かめに使う公開鍵を取得できませんでした（HTTP " + response.statusCode() + "）。");
        }
        try {
            JsonNode json = objectMapper.readTree(response.body());
            if (json == null || !json.isObject()) {
                throw reject("公開鍵の一覧（JWKS）の形が想定と違います。");
            }
            return json;
        } catch (IOException e) {
            throw reject("公開鍵の一覧（JWKS）を読めませんでした。");
        }
    }

    /** RS256 の署名（入力は「ヘッダー.ペイロード」の ASCII）を、公開鍵で確かめる。 */
    private static void verifySignature(String[] parts, PublicKey key) {
        byte[] signature;
        try {
            signature = decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw reject("ID トークンの署名が確かめられませんでした。");
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signature)) {
                throw reject("ID トークンの署名が確かめられませんでした。");
            }
        } catch (GeneralSecurityException e) {
            throw reject("ID トークンの署名が確かめられませんでした。");
        }
    }

    private static void checkIssuer(JsonNode payload) {
        if (!ISSUER.equals(textOf(payload, "iss"))) {
            throw reject("ID トークンの発行元（iss）が、OpenAI の値と違います。");
        }
    }

    /** 宛先（aud）は文字列か配列。配列のときは、そのなかに client_id が1つあればよい。 */
    private static void checkAudience(JsonNode payload, String clientId) {
        JsonNode aud = payload.get("aud");
        boolean matched = false;
        if (aud != null && aud.isTextual()) {
            matched = clientId.equals(aud.asText());
        } else if (aud != null && aud.isArray()) {
            for (JsonNode item : aud) {
                if (item.isTextual() && clientId.equals(item.asText())) {
                    matched = true;
                }
            }
        }
        if (!matched) {
            throw reject("ID トークンの宛先（aud）が、このアプリの client_id と違います。");
        }
    }

    /** 期限（exp）は秒の数字。今の時刻がそれ以上なら期限切れ。 */
    private void checkExpiry(JsonNode payload) {
        JsonNode exp = payload.get("exp");
        if (exp == null || !exp.isIntegralNumber()) {
            throw reject("ID トークンの期限（exp）が読めません。");
        }
        if (clock.instant().getEpochSecond() >= exp.asLong()) {
            throw reject("ID トークンの期限が切れています。");
        }
    }

    private static void checkNonce(JsonNode payload, String expectedNonce) {
        String actual = textOf(payload, "nonce");
        if (actual == null || !sameText(expectedNonce, actual)) {
            throw reject("ID トークンの nonce が、サインインを始めたときの値と違います。");
        }
    }

    private static ChatGptSignInException reject(String message) {
        return new ChatGptSignInException(message);
    }

    private static byte[] decode(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url);
    }

    private static String textOf(JsonNode json, String name) {
        JsonNode node = json.get(name);
        return (node != null && node.isTextual() && !node.asText().isBlank()) ? node.asText() : null;
    }

    /** 時間によらず同じ長さで比べる（nonce の照合で、文字の一致を少しずつ調べられないように）。 */
    private static boolean sameText(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * 確かめが通った ID トークンから取り出した、本人の ID と表示用の名前。
     *
     * @param subject     本人の ID（sub）
     * @param displayName 表示用の名前（name が読めなければ「名前なし」）
     */
    public record VerifiedIdToken(String subject, String displayName) {
    }
}
