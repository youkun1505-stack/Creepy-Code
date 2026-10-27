package com.creepycode.common.ai.chatgpt;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sign in with ChatGPT の OAuth（PKCE）。認可 URL を作り、戻りの値を確かめ、code をトークンに交換する。
 * 戻り先のサーバーとコマンドは F-4 の部品が担当し、この部品を呼ぶ。保存もそちらで行う。
 * 秘密（code・verifier・state・nonce・トークン・応答の本文）は、例外の文言とログに出さない。
 */
public class ChatGptOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(ChatGptOAuthClient.class);

    /** 初回の登録で使う client_id（公式の値）。 */
    static final String INITIAL_CLIENT_ID = "dynamic_agent_client";
    /** 初回の登録で送るアプリの名前。 */
    static final String AGENT_NAME_HINT = "Creepy Code Bridal App";
    /** 戻り先の道筋。 */
    static final String REDIRECT_PATH = "/auth/callback";
    /** 資源（API）の指定。認可とトークン交換の両方で同じ値を送る。 */
    static final String RESOURCE = "https://api.openai.com/v1";
    static final String SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    /** プランの利用が許可されたことを表す scope。応答に無ければ例外にする。 */
    static final String PLAN_SCOPE = "chatgpt.tokens.use.direct";
    /** トークン交換の失敗の種類。この値のときだけ、発行 ID で認可をやり直せる。 */
    static final String ERROR_INVALID_GRANT = "invalid_grant";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(30);

    private final ChatGptEndpoints endpoints;
    private final HttpClient httpClient;
    private final Clock clock;
    private final SecureRandom random;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 公式の仕組み（既定の HttpClient・現在時刻・安全な乱数）で作る。 */
    public ChatGptOAuthClient(ChatGptEndpoints endpoints) {
        this(endpoints, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), Clock.systemUTC(), new SecureRandom());
    }

    /**
     * @param endpoints  認可とトークン交換の URL（テストでは偽サーバー）
     * @param httpClient トークン交換に使う HTTP クライアント
     * @param clock      期限の計算に使う時刻（テストでは固定の時刻）
     * @param random     verifier・state・nonce に使う乱数（本物は SecureRandom）
     */
    public ChatGptOAuthClient(ChatGptEndpoints endpoints, HttpClient httpClient, Clock clock, SecureRandom random) {
        this.endpoints = endpoints;
        this.httpClient = httpClient;
        this.clock = clock;
        this.random = random;
    }

    /**
     * 認可 URL を作る。registration が null なら初回の登録（client_id は dynamic_agent_client、
     * agent_name_hint を送る）。あれば登録済みとして、その client_id を送る（agent_name_hint は送らない）。
     * ext_agent_host_id は初回も登録済みも毎回送る。state・nonce・verifier は毎回新しく作る。
     *
     * @param port         戻り先の受け口のポート（127.0.0.1 で待ち受けるポート）
     * @param hostId       このホストの ext_agent_host_id。呼び出し側が、初回のサインインの前に保存した値を渡す
     * @param registration 登録済みの登録情報。初回なら null（登録済みなら hostId と同じ値が必要）
     */
    public ChatGptAuthAttempt startAuthorization(int port, String hostId, ChatGptRegistration registration) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("ポートは 1〜65535 にしてください");
        }
        if (isBlank(hostId)) {
            throw new IllegalArgumentException("ホストの値（ext_agent_host_id）を指定してください");
        }
        if (registration != null && !hostId.equals(registration.hostId())) {
            throw new IllegalArgumentException("ホストの値が、登録済みの値と違います");
        }
        PkceCodes pkce = PkceCodes.create(random);
        String state = PkceCodes.randomValue(random);
        String nonce = PkceCodes.randomValue(random);
        String redirectUri = "http://127.0.0.1:" + port + REDIRECT_PATH;
        boolean initial = registration == null;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", initial ? INITIAL_CLIENT_ID : registration.clientId());
        if (initial) {
            params.put("agent_name_hint", AGENT_NAME_HINT);
        }
        params.put("ext_agent_host_id", hostId);
        params.put("redirect_uri", redirectUri);
        params.put("scope", SCOPE);
        params.put("resource", RESOURCE);
        params.put("state", state);
        params.put("nonce", nonce);
        params.put("code_challenge", pkce.challenge());
        params.put("code_challenge_method", "S256");

        String authorizationUrl = endpoints.authorizeUrl() + "?" + formEncode(params);
        return new ChatGptAuthAttempt(authorizationUrl, redirectUri, state, nonce, pkce.verifier(), hostId,
                initial ? null : registration.clientId());
    }

    /** 新しいホストの値（urn:uuid: ＋ランダムな UUID）を作る。初回のサインインの前に1回作って保存する。 */
    public static String newHostId() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    /**
     * 戻りの値を確かめ、code をトークンに交換して資格情報を作る。
     * 確かめに通らないとき（取り消し・state が違う・初回で ID が無い・登録済みと違う ID が戻った・code が無い）は、
     * 交換せずに例外にする。保存はしない（呼び出し側が行う）。
     */
    public ChatGptSignInResult completeSignIn(ChatGptAuthAttempt attempt, ChatGptCallback callback) {
        if (callback.error() != null) {
            if ("access_denied".equals(callback.error())) {
                throw new ChatGptSignInException("サインインがキャンセルされました。もう一度お試しください。");
            }
            throw new ChatGptSignInException("サインインが完了しませんでした（OpenAI の画面でエラーが返りました）。");
        }
        if (!sameText(attempt.state(), callback.state())) {
            throw new ChatGptSignInException(
                    "戻りの state が一致しません。別のサインインの戻りかもしれないため、中止しました。");
        }
        if (isBlank(callback.code())) {
            throw new ChatGptSignInException("戻りに認可コードがありません。もう一度お試しください。");
        }
        String clientId;
        if (attempt.isInitialRegistration()) {
            // dynamic_agent_client は登録の受け付けの印で、発行された ID ではないので使わない
            if (isBlank(callback.clientId()) || INITIAL_CLIENT_ID.equals(callback.clientId())) {
                throw new ChatGptSignInException("登録できませんでした（OpenAI から発行された ID が戻りにありません）。");
            }
            clientId = callback.clientId();
        } else {
            // 登録済みのアカウントを別の ID で置き換えない。戻りに ID があって違うときは中止する
            if (!isBlank(callback.clientId()) && !callback.clientId().equals(attempt.registeredClientId())) {
                throw new ChatGptSignInException(
                        "戻りの ID が、登録済みのアカウントと違います。別のアカウントへの戻りかもしれないため、中止しました。");
            }
            clientId = attempt.registeredClientId();
        }

        ChatGptCredentials credentials = exchangeCode(attempt, callback.code(), clientId);
        return new ChatGptSignInResult(credentials, new ChatGptRegistration(attempt.hostId(), clientId));
    }

    private ChatGptCredentials exchangeCode(ChatGptAuthAttempt attempt, String code, String clientId) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId);
        form.put("code", code);
        form.put("code_verifier", attempt.codeVerifier());
        form.put("redirect_uri", attempt.redirectUri());
        form.put("resource", RESOURCE);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoints.tokenUrl()))
                .timeout(EXCHANGE_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 例外の原因（通信の詳細）は出さず、種類だけを日本語で伝える
            throw new ChatGptSignInException("トークンの交換で通信に失敗しました。通信の状態を確かめて、もう一度お試しください。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatGptSignInException("トークンの交換が中断されました。もう一度お試しください。");
        }
        if (response.statusCode() != 200) {
            // 応答の本文はログにも例外にも出さない（トークンや個人の情報が入りうるため）。
            // 本文から読むのは error の種類だけ。invalid_grant なら、発行 ID で認可をやり直せる印を付ける
            boolean retry = ERROR_INVALID_GRANT.equals(errorTypeOf(response.body()));
            log.warn("ChatGPT のトークン交換が失敗しました（HTTP {}）", response.statusCode());
            throw new ChatGptSignInException("トークンの交換が失敗しました（HTTP " + response.statusCode() + "）。",
                    clientId, retry);
        }
        return toCredentials(response.body());
    }

    /** 失敗の応答から error の種類だけを読む。読めなければ null（本文の中身は出さない）。 */
    private String errorTypeOf(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            return json == null ? null : textOf(json, "error");
        } catch (IOException e) {
            return null;
        }
    }

    /** トークン交換の応答（JSON）から資格情報を作る。期限は今の時刻＋expires_in。 */
    private ChatGptCredentials toCredentials(String body) {
        JsonNode json;
        try {
            json = objectMapper.readTree(body);
        } catch (IOException e) {
            // Jackson の例外の文言には本文の一部が入るので、原因は付けずに種類だけ伝える
            throw new ChatGptSignInException("トークンの応答を読めませんでした。");
        }
        if (json == null || !json.isObject()) {
            throw new ChatGptSignInException("トークンの応答の形が想定と違います。");
        }
        String accessToken = textOf(json, "access_token");
        String refreshToken = textOf(json, "refresh_token");
        String idToken = textOf(json, "id_token");
        String scope = textOf(json, "scope");
        JsonNode expiresIn = json.get("expires_in");
        if (accessToken == null || refreshToken == null || expiresIn == null || !expiresIn.isIntegralNumber()
                || expiresIn.asLong() <= 0) {
            throw new ChatGptSignInException("トークンの応答の形が想定と違います。");
        }
        if (scope == null || !List.of(scope.trim().split("\\s+")).contains(PLAN_SCOPE)) {
            throw new ChatGptSignInException(
                    "プランの利用が許可されていません。Plus・Pro のプランか、ChatGPT の設定を確かめてください。");
        }
        return new ChatGptCredentials(accessToken, refreshToken, idToken,
                clock.instant().plusSeconds(expiresIn.asLong()), scope);
    }

    private static String textOf(JsonNode json, String name) {
        JsonNode node = json.get(name);
        return (node != null && node.isTextual() && !node.asText().isBlank()) ? node.asText() : null;
    }

    /** 項目を form 形式（key=value を & でつなぐ。値は URL エンコード）にする。 */
    static String formEncode(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    /** 時間によらず同じ長さで比べる（state の照合で、文字の一致を少しずつ調べられないように）。 */
    private static boolean sameText(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
