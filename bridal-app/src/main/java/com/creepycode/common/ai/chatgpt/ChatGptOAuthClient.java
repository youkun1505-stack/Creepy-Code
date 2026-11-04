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
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    /** 公式の失敗の種類 invalid_client（アプリの登録の設定の誤り）。資格情報は消さない。 */
    static final String INVALID_CLIENT = "invalid_client";
    /** 公式が挙げた、資格情報を消してよい6つの失敗の種類（errors-and-recovery）。 */
    static final Set<String> REJECTED_REFRESH_ERRORS = Set.of("invalid_grant", "invalid_refresh_token",
            "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused");
    /** ログに出してよい失敗の種類の名前（上の6つと invalid_client）。これ以外の値は出さない。 */
    private static final Set<String> KNOWN_REFRESH_ERRORS = Set.of("invalid_grant", "invalid_refresh_token",
            "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused",
            INVALID_CLIENT);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(30);
    /** トークン更新の通信の制限時間（呼び出し側の全体の制限時間より短くする）。 */
    static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(20);

    private final ChatGptEndpoints endpoints;
    private final HttpClient httpClient;
    private final Clock clock;
    private final SecureRandom random;
    private final Duration refreshTimeout;
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
        this(endpoints, httpClient, clock, random, REFRESH_TIMEOUT);
    }

    /**
     * @param refreshTimeout 更新の通信全体（本文を含む）の制限時間（テストでは短くする）
     */
    ChatGptOAuthClient(ChatGptEndpoints endpoints, HttpClient httpClient, Clock clock, SecureRandom random,
            Duration refreshTimeout) {
        this.endpoints = endpoints;
        this.httpClient = httpClient;
        this.clock = clock;
        this.random = random;
        this.refreshTimeout = refreshTimeout;
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
                clock.instant().plusSeconds(expiresIn.asLong()), scope,
                parseEarliest(json.get("earliest_refresh_at")));
    }

    /**
     * アクセストークンを更新する（form 形式の POST。送る項目は grant_type・client_id・refresh_token・resource の4つだけ。scope は送らない）。
     * 応答の新しい更新用トークンは、呼び出し側が保存してから使う（この部品は保存しない）。
     * 応答に id_token・scope が無いときは前の値を残す。scope があってプランの scope が無いときは例外にする。
     * 応答の本文はログにも例外にも出さない。ログに出すのは、公式の失敗の種類の名前（既知のものだけ）と HTTP の番号。
     *
     * @param registration 登録情報（発行された client_id を使う）
     * @param current      今の資格情報（更新用トークンと、前の id_token・scope を使う）
     * @throws RefreshException 更新できなかったとき（種類で、資格情報を消すかどうかが決まる）
     */
    public ChatGptCredentials refresh(ChatGptRegistration registration, ChatGptCredentials current) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", registration.clientId());
        form.put("refresh_token", current.refreshToken());
        form.put("resource", RESOURCE);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoints.tokenUrl()))
                .timeout(refreshTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = sendRefresh(request);
        if (response.statusCode() != 200) {
            String error = knownRefreshError(response.body());
            log.warn("ChatGPT のトークン更新が失敗しました（HTTP {}・種類：{}）", response.statusCode(),
                    error == null ? "不明" : error);
            if (error != null && REJECTED_REFRESH_ERRORS.contains(error)) {
                throw new RefreshException(RefreshException.Kind.REJECTED,
                        "サインインの情報が使えなくなりました。サインインし直してください。");
            }
            if (INVALID_CLIENT.equals(error)) {
                throw new RefreshException(RefreshException.Kind.INVALID_CLIENT,
                        "アプリの登録の設定が合わないため、サインインを更新できませんでした。");
            }
            throw new RefreshException(RefreshException.Kind.FAILED,
                    "サインインを更新できませんでした（HTTP " + response.statusCode() + "）。");
        }
        return toRefreshed(response.body(), current);
    }

    /**
     * 更新の通信を、本文を含めて制限時間の中で行う。HttpRequest.timeout は本文の途中の停止には効かないため、
     * 送信は非同期にして、制限時間を過ぎたら通信を打ち切る（呼び出し側のスレッドを戻すため）。
     */
    private HttpResponse<String> sendRefresh(HttpRequest request) {
        CompletableFuture<HttpResponse<String>> pending = httpClient.sendAsync(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return pending.get(refreshTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new RefreshException(RefreshException.Kind.FAILED,
                    "トークンの更新が制限時間内に終わりませんでした。時間をおいてから、もう一度お試しください。");
        } catch (ExecutionException e) {
            // 通信の失敗は資格情報を消さない（一時的な失敗かもしれないため）
            throw new RefreshException(RefreshException.Kind.FAILED,
                    "トークンの更新で通信に失敗しました。時間をおいてから、もう一度お試しください。");
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new RefreshException(RefreshException.Kind.FAILED, "トークンの更新が中断されました。");
        }
    }

    /**
     * 更新の成功の応答（JSON）から資格情報を作る。前の値を残す項目（id_token・scope）はここで決める。
     * 応答に新しい refresh_token があれば、検査に失敗しても、使える項目で作った資格情報を RefreshException に入れて返す。
     * 公式は、更新用トークンは使うと替わると定めており（古いものはもう使えない）、新しいものを失うとサインインし直しになるため。
     */
    private ChatGptCredentials toRefreshed(String body, ChatGptCredentials current) {
        JsonNode json;
        try {
            json = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new RefreshException(RefreshException.Kind.FAILED, "トークンの更新の応答を読めませんでした。");
        }
        if (json == null || !json.isObject()) {
            throw new RefreshException(RefreshException.Kind.FAILED, "トークンの更新の応答の形が想定と違います。");
        }
        String accessToken = textOf(json, "access_token");
        String refreshToken = textOf(json, "refresh_token");
        JsonNode expiresIn = json.get("expires_in");
        boolean usableExpiry = expiresIn != null && expiresIn.isIntegralNumber() && expiresIn.asLong() > 0;
        // 応答に ID トークンが無いときは前の値を残す。nonce の確認はできないので、ここでは確かめない
        String idToken = textOf(json, "id_token");
        if (idToken == null) {
            idToken = current.idToken();
        }
        String responseScope = textOf(json, "scope");
        String scope = responseScope == null ? current.scope() : responseScope;

        if (accessToken == null || refreshToken == null || !usableExpiry) {
            // 新しい更新用トークンだけは、使える項目（今のアクセストークン・期限）と一緒に保存する
            ChatGptCredentials salvage = refreshToken == null ? null : new ChatGptCredentials(current.accessToken(),
                    refreshToken, current.idToken(), current.expiresAt(), current.scope(),
                    current.earliestRefreshAt());
            throw new RefreshException(RefreshException.Kind.FAILED, "トークンの更新の応答の形が想定と違います。",
                    salvage);
        }
        ChatGptCredentials refreshed = new ChatGptCredentials(accessToken, refreshToken, idToken,
                clock.instant().plusSeconds(expiresIn.asLong()), scope,
                parseEarliest(json.get("earliest_refresh_at")));
        if (responseScope != null && !List.of(responseScope.trim().split("\\s+")).contains(PLAN_SCOPE)) {
            // プランの許可が無い（公式は、サインインは保ったままプランの利用を無効として扱うと定めている）。新しい資格情報は保存させる
            throw new RefreshException(RefreshException.Kind.FAILED,
                    "プランの利用が許可されていません。Plus・Pro のプランか、ChatGPT の設定を確かめてください。", refreshed);
        }
        return refreshed;
    }

    /** 失敗の応答から、既知の失敗の種類の名前だけを返す。error が文字の形と error.code の形の両方を読む。知らない値・読めないときは null。 */
    private String knownRefreshError(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json == null || !json.isObject()) {
                return null;
            }
            JsonNode error = json.get("error");
            String name = null;
            if (error != null && error.isTextual()) {
                name = error.asText();
            } else if (error != null && error.isObject()) {
                name = textOf(error, "code");
            }
            return name != null && KNOWN_REFRESH_ERRORS.contains(name) ? name : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * earliest_refresh_at を読む。形は公式に説明がないので、数値（秒。1970年からの秒として読む）と ISO の文字の両方を読む。
     * 読めなければ null（無いものとして扱う）。
     */
    static Instant parseEarliest(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return Instant.ofEpochSecond(node.asLong());
            }
            if (node.isTextual()) {
                String text = node.asText().trim();
                try {
                    return Instant.parse(text);
                } catch (DateTimeParseException e) {
                    return OffsetDateTime.parse(text).toInstant();
                }
            }
            return null;
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String textOf(JsonNode json, String name) {
        JsonNode node = json.get(name);
        return (node != null && node.isTextual() && !node.asText().isBlank()) ? node.asText() : null;
    }

    /**
     * トークンの更新が失敗したときの例外。種類で、資格情報を消すかどうかが決まる。
     * 公式の失敗の種類（REJECTED）のときだけ、呼び出し側が資格情報を消す。通信・503・invalid_client では消さない。
     */
    public static class RefreshException extends RuntimeException {

        /** 失敗の種類。 */
        public enum Kind {
            /** 更新用トークンが使えなくなった（資格情報を消して、サインインし直してもらう） */
            REJECTED,
            /** アプリの登録の設定の誤り（資格情報は消さない） */
            INVALID_CLIENT,
            /** 一時的な失敗・応答の形の誤りなど（資格情報は消さない） */
            FAILED
        }

        private final Kind kind;
        private final ChatGptCredentials salvage;

        public RefreshException(Kind kind, String message) {
            this(kind, message, null);
        }

        /**
         * @param salvage 検査に失敗した応答から作った、保存すべき新しい資格情報（無ければ null）
         */
        RefreshException(Kind kind, String message, ChatGptCredentials salvage) {
            super(message);
            this.kind = kind;
            this.salvage = salvage;
        }

        public Kind kind() {
            return kind;
        }

        /** 保存すべき新しい資格情報（新しい更新用トークンを含む）。無ければ null。 */
        ChatGptCredentials salvage() {
            return salvage;
        }
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
