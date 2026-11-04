package com.creepycode.common.ai.chatgpt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.creepycode.common.exception.AiException;

/**
 * Sign in with ChatGPT のアクセストークンを、AI を呼ぶ前に確かめ、必要なら更新する。
 * <ul>
 * <li>{@link #usable(Duration)}：使えるトークンを返す。期限の5分前からは更新する（期限が切れていれば必ず更新する）。</li>
 * <li>{@link #afterUnauthorized(String, Duration)}：AI が 401 を返したトークンを更新する。
 * 失敗したトークンがすでに新しいものに替わっていれば、通信せず新しいものを返す。</li>
 * </ul>
 * 更新の通信は呼び出し側のスレッドではなく、別のスレッドで1つにまとめて行う（同時の呼び出しは同じ結果を待つ）。
 * 呼び出し側が時間切れ・中断しても、通信と保存は最後まで行う（更新用トークンは使うと替わるので、結果を失うとサインインし直しになるため）。
 * 通信自体にも時間の上限があり、上限を過ぎたら打ち切って失敗にする（失敗のあとは、短い間隔のあいだ新しい通信を始めない）。
 * 読み込みの結果はメモリに持ち、資格情報ファイルの更新時刻が変わったときだけ読み直す（毎回 PowerShell を呼ばない）。
 * トークン・更新の応答の本文は、ログと例外の文言に出さない。
 */
public class ChatGptTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ChatGptTokenProvider.class);

    /** 期限がこの時間より近づいたら、期限の前に更新する（「期限が近い」）。 */
    static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
    /** 期限の前の更新を待つ時間の上限。これを過ぎたら、期限内の今のトークンで進める（更新は裏で続く）。 */
    static final Duration PROACTIVE_WAIT = Duration.ofSeconds(5);
    /** 更新が失敗したあと、新しい更新を始めるまでの間隔（失敗の繰り返しで公式に負荷をかけないため）。 */
    static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(3);

    static final String MSG_NOT_SIGNED_IN = "ChatGPT にサインインしていません。サインインしてから、もう一度お試しください。";
    static final String MSG_EXPIRED = "ChatGPT のサインインの期限が切れました。サインインし直してください。";
    static final String MSG_REJECTED = "ChatGPT のサインインが無効になりました。サインインし直してください。";
    static final String MSG_REFRESH_FAILED = "ChatGPT のサインインを更新できませんでした。時間をおいてから、もう一度お試しください。";
    static final String MSG_REFRESH_TIMEOUT = "ChatGPT のサインインの更新が制限時間内に終わりませんでした。時間をおいてから、もう一度お試しください。";
    static final String MSG_REFRESH_INTERRUPTED = "ChatGPT のサインインの更新が中断されました。時間をおいてから、もう一度お試しください。";
    static final String MSG_SAVE_FAILED = "更新したサインイン情報を保存できませんでした。時間をおいてから、もう一度お試しください。";

    private static final String REFRESH_THREAD_NAME = "chatgpt-token-refresh";

    private final ChatGptCredentialStore store;
    private final ChatGptOAuthClient oauth;
    private final Clock clock;
    private final Duration retryInterval;

    /** 以下は lock の中でだけ読み書きする。 */
    private final Object lock = new Object();
    /** 今の資格情報（読み込んだものか、保存したもの）。無いときは null。 */
    private ChatGptCredentials cached;
    /** cached を読んだときの、資格情報ファイルの更新時刻。読めなかったときは null（次の呼び出しで読み直す）。 */
    private Instant cachedModifiedAt;
    /** 進行中の更新（同時の呼び出しは、これを待つ）。無いときは null。 */
    private CompletableFuture<ChatGptCredentials> refreshing;
    /** 直前の更新が失敗したか。失敗のあとは retryAllowedAtNanos まで新しい更新を始めない。 */
    private boolean hasFailure;
    private long retryAllowedAtNanos;

    /**
     * @param store 資格情報と登録情報の保存先（本物は既定の保存先と Windows の暗号化）
     * @param oauth 更新の通信（本物は公式の接続先）
     * @param clock 期限の判定に使う時刻
     */
    public ChatGptTokenProvider(ChatGptCredentialStore store, ChatGptOAuthClient oauth, Clock clock) {
        this(store, oauth, clock, DEFAULT_RETRY_INTERVAL);
    }

    /**
     * @param retryInterval 更新が失敗したあと、新しい更新を始めるまでの間隔（テストでは短くする）
     */
    ChatGptTokenProvider(ChatGptCredentialStore store, ChatGptOAuthClient oauth, Clock clock,
            Duration retryInterval) {
        this.store = store;
        this.oauth = oauth;
        this.clock = clock;
        this.retryInterval = retryInterval;
    }

    /**
     * 使えるトークンを返す。期限まで余裕があれば、更新せずに返す。
     * 期限の5分前からは更新する（まだ期限内で、更新が失敗・時間切れのときは、今のトークンを返す。待つのは最大 PROACTIVE_WAIT）。
     * 期限が切れていれば、更新できなければ例外にする（待つのは maxWait まで）。
     *
     * @param maxWait 更新を待つ時間の上限（呼び出し側の全体の制限時間の残り）
     * @throws AiException サインインしていない・期限切れで更新できない・更新に失敗した・時間切れのとき
     */
    public ChatGptCredentials usable(Duration maxWait) {
        CompletableFuture<ChatGptCredentials> waiting;
        boolean expired;
        synchronized (lock) {
            ChatGptCredentials current = currentLocked().orElseThrow(() -> new AiException(MSG_NOT_SIGNED_IN));
            Instant now = clock.instant();
            expired = !current.expiresAt().isAfter(now);
            if (!expired && !needsRefreshBeforeExpiry(current, now)) {
                return current;
            }
            Optional<ChatGptRegistration> registration = store.loadRegistration();
            if (registration.isEmpty()) {
                // 登録情報が無いときは、通信も消去もしない。期限内なら今のトークンを使い、切れたらサインインし直してもらう
                if (expired) {
                    throw new AiException(MSG_EXPIRED);
                }
                return current;
            }
            waiting = startOrJoinRefreshLocked(current, registration.get());
        }
        Duration wait = expired || maxWait.compareTo(PROACTIVE_WAIT) < 0 ? maxWait : PROACTIVE_WAIT;
        try {
            return await(waiting, wait);
        } catch (AiException e) {
            if (expired) {
                throw e;
            }
            // 期限の前の更新が失敗・時間切れでも、まだ期限内の今のトークンは使える
            synchronized (lock) {
                Optional<ChatGptCredentials> now = currentLocked();
                if (now.isPresent() && now.get().expiresAt().isAfter(clock.instant())) {
                    return now.get();
                }
            }
            throw e;
        }
    }

    /**
     * AI が 401 を返したときに、そのトークンを更新して新しいものを返す。
     * 渡されたトークンがすでに別の呼び出しで替わっていれば、通信せずに新しいものを返す（同時の401でも更新は1回）。
     * 401 のときは earliest_refresh_at を見ずに更新する（拒まれたトークンはもう使えないため）。
     * 登録情報が無いときは、更新せずに例外にする。
     *
     * @param rejectedAccessToken AI に拒まれたアクセストークン
     * @param maxWait             更新を待つ時間の上限（呼び出し側の全体の制限時間の残り）
     * @throws AiException 更新できなかったとき（資格情報を消したときを含む）
     */
    public ChatGptCredentials afterUnauthorized(String rejectedAccessToken, Duration maxWait) {
        CompletableFuture<ChatGptCredentials> waiting;
        synchronized (lock) {
            ChatGptCredentials current = currentLocked().orElseThrow(() -> new AiException(MSG_REJECTED));
            if (!current.accessToken().equals(rejectedAccessToken)) {
                // 別の呼び出しがすでに更新した。通信せずに新しいものを返す
                return current;
            }
            ChatGptRegistration registration = store.loadRegistration()
                    .orElseThrow(() -> new AiException(MSG_REJECTED));
            waiting = startOrJoinRefreshLocked(current, registration);
        }
        return await(waiting, maxWait);
    }

    /** 期限の5分前より後で、earliest_refresh_at より前ではないなら、更新する。 */
    private static boolean needsRefreshBeforeExpiry(ChatGptCredentials credentials, Instant now) {
        return !now.isBefore(credentials.expiresAt().minus(REFRESH_BEFORE_EXPIRY))
                && earliestAllows(credentials, now);
    }

    /** earliest_refresh_at の時刻より前ではないか（無い・読めないときは許す）。期限の前の更新だけで使う。 */
    private static boolean earliestAllows(ChatGptCredentials credentials, Instant now) {
        return credentials.earliestRefreshAt() == null || !now.isBefore(credentials.earliestRefreshAt());
    }

    /**
     * 資格情報を読む。ファイルの更新時刻が変わったときだけ読み直す（毎回 PowerShell を呼ばない）。
     * ファイルが無いときは、メモリの分も空にする（サインアウトに追いつく）。
     * ファイルはあるのに読めなかったときは、更新時刻を覚えない（次の呼び出しでもう一度読む）。lock の中で呼ぶ。
     */
    private Optional<ChatGptCredentials> currentLocked() {
        Optional<Instant> modifiedAt = store.credentialsModifiedAt();
        if (modifiedAt.isEmpty()) {
            cached = null;
            cachedModifiedAt = null;
            return Optional.empty();
        }
        if (cachedModifiedAt == null || !modifiedAt.get().equals(cachedModifiedAt)) {
            Optional<ChatGptCredentials> loaded = store.load();
            if (loaded.isPresent()) {
                cached = loaded.get();
                cachedModifiedAt = modifiedAt.get();
            } else {
                cached = null;
                cachedModifiedAt = null;
            }
        }
        return Optional.ofNullable(cached);
    }

    /**
     * 進行中の更新があればそれを返し、なければ新しく始める。更新の通信は別のスレッドで行う。lock の中で呼ぶ。
     * 直前の更新が失敗して間隔が空いていないときは、通信を始めずに失敗を返す。
     */
    private CompletableFuture<ChatGptCredentials> startOrJoinRefreshLocked(ChatGptCredentials base,
            ChatGptRegistration registration) {
        if (refreshing != null) {
            return refreshing;
        }
        if (hasFailure && System.nanoTime() - retryAllowedAtNanos < 0) {
            return CompletableFuture.failedFuture(new AiException(MSG_REFRESH_FAILED));
        }
        CompletableFuture<ChatGptCredentials> future = new CompletableFuture<>();
        refreshing = future;
        Thread worker = new Thread(() -> runRefresh(base, registration, future), REFRESH_THREAD_NAME);
        worker.setDaemon(true);
        try {
            worker.start();
        } catch (RuntimeException | Error e) {
            // 起動できなかったときも進行中の印を残さない（残すと以後の呼び出しがすべて待たされるため）
            refreshing = null;
            future.completeExceptionally(new AiException(MSG_REFRESH_FAILED, e));
        }
        return future;
    }

    /**
     * 更新を1回行い、結果を保存してから future に届ける（呼び出し側の時間切れ・中断に関係なく最後まで行う）。
     * 保存できたら新しいものを、できなければ例外を届ける。
     */
    private void runRefresh(ChatGptCredentials base, ChatGptRegistration registration,
            CompletableFuture<ChatGptCredentials> future) {
        ChatGptCredentials result = null;
        AiException failure = null;
        try {
            ChatGptCredentials refreshed = oauth.refresh(registration, base);
            result = persist(refreshed);
            log.info("ChatGPT のサインイン情報を更新しました");
        } catch (ChatGptOAuthClient.RefreshException e) {
            if (e.salvage() != null) {
                // 応答の新しい更新用トークンは、検査に失敗しても使えないと困るので先に保存する（古いものはもう使えないため）
                keepSalvaged(e.salvage());
            }
            failure = onRefreshFailed(e);
        } catch (AiException e) {
            failure = e;
        } catch (RuntimeException e) {
            log.warn("ChatGPT のサインイン情報の更新で想定外の失敗がありました（{}）", e.getClass().getSimpleName());
            failure = new AiException(MSG_REFRESH_FAILED, e);
        } finally {
            synchronized (lock) {
                refreshing = null;
                hasFailure = result == null;
                retryAllowedAtNanos = System.nanoTime() + retryInterval.toNanos();
            }
            if (result != null) {
                future.complete(result);
            } else {
                future.completeExceptionally(failure != null ? failure : new AiException(MSG_REFRESH_FAILED));
            }
        }
    }

    /** 検査に失敗した応答から作った資格情報を保存する。保存できなければ、その失敗は元の失敗に任せる（ログは persist が出す）。 */
    private void keepSalvaged(ChatGptCredentials salvaged) {
        try {
            persist(salvaged);
        } catch (AiException e) {
            // 保存の失敗は persist がログに出している。元の更新の失敗を返すため、ここでは何もしない
        }
    }

    /** 新しい資格情報を保存し、メモリにも置く。保存に失敗したら例外にする（メモリには置いたままにし、今の起動では使えるようにする）。 */
    private ChatGptCredentials persist(ChatGptCredentials refreshed) {
        synchronized (lock) {
            cached = refreshed;
            try {
                store.save(refreshed);
            } catch (RuntimeException e) {
                log.warn("更新したサインイン情報を保存できませんでした（{}）", e.getClass().getSimpleName());
                throw new AiException(MSG_SAVE_FAILED, e);
            }
            cachedModifiedAt = store.credentialsModifiedAt().orElse(null);
        }
        return refreshed;
    }

    /**
     * 更新の失敗を、利用者に出す例外にする。REJECTED のときだけ資格情報を消す（登録情報は残す）。
     * 公式の失敗の種類の名前と、消したかどうかだけをログに出す。
     */
    private AiException onRefreshFailed(ChatGptOAuthClient.RefreshException e) {
        switch (e.kind()) {
            case REJECTED -> {
                synchronized (lock) {
                    try {
                        store.clear();
                    } catch (RuntimeException clearFailure) {
                        log.warn("サインイン情報を消せませんでした（{}）", clearFailure.getClass().getSimpleName());
                    }
                    cached = null;
                    cachedModifiedAt = null;
                }
                log.warn("ChatGPT のサインイン情報を消しました（更新用の情報が使えなくなったため）。サインインし直してください。");
                return new AiException(MSG_REJECTED, e);
            }
            case INVALID_CLIENT -> {
                log.warn("ChatGPT のサインイン情報は消しませんでした（アプリの登録の設定が合いません）");
                return new AiException(e.getMessage(), e);
            }
            default -> {
                log.warn("ChatGPT のサインイン情報は消しませんでした（更新の失敗：{}）", e.kind());
                // FAILED の文言は、更新の部品が作った利用者向けの文（応答の本文は含まない）なのでそのまま使う
                return new AiException(e.getMessage(), e);
            }
        }
    }

    /** 更新の結果を、制限時間まで待つ。時間切れ・中断のときは例外にする（更新そのものは止めない）。 */
    private static ChatGptCredentials await(CompletableFuture<ChatGptCredentials> future, Duration maxWait) {
        long nanos = Math.max(0L, maxWait.toNanos());
        try {
            return future.get(nanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new AiException(MSG_REFRESH_TIMEOUT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(MSG_REFRESH_INTERRUPTED, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AiException aiException) {
                throw aiException;
            }
            throw new AiException(MSG_REFRESH_FAILED, e.getCause());
        }
    }
}
