package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 起動時の自動サインイン（ChatGptAutoSignIn）のテスト。
 * 保存先は @TempDir の中だけ使い、ブラウザは開かず URL を記録するだけにする。外のネットワークは使わない。
 * 戻りは来ないので、短い時間で時間切れにさせる（本番の5分は使わない）。
 */
class ChatGptAutoSignInTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneOffset.UTC);
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    @TempDir
    Path tempDir;

    private final List<String> browserUrls = new CopyOnWriteArrayList<>();
    private ChatGptCredentialStore store;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // テストでは暗号化をしない（DPAPI は PowerShell を使うため。本物の暗号化は別のテストで確かめる）
        store = new ChatGptCredentialStore(tempDir, new CredentialProtector() {
            @Override
            public byte[] protect(byte[] plain) {
                return plain;
            }

            @Override
            public byte[] unprotect(byte[] encrypted) {
                return encrypted;
            }
        });
        logs = new ListAppender<>();
        logs.start();
        logger = (Logger) LoggerFactory.getLogger(ChatGptAutoSignIn.class);
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void 未サインインのときは_ブラウザを開いて_127の戻り先でサインインを始める() throws Exception {
        int port = freePort();
        ChatGptAutoSignIn autoSignIn = new ChatGptAutoSignIn(store, signIn(SHORT_TIMEOUT), port);

        Thread thread = autoSignIn.start();
        thread.join(WAIT.toMillis());

        assertThat(thread.isAlive()).isFalse();
        assertThat(browserUrls).hasSize(1);
        assertThat(browserUrls.get(0)).contains("127.0.0.1%3A" + port).doesNotContain("localhost");
    }

    @Test
    void サインイン済み_期限内_のときは_サインインを始めず_ブラウザも開かない() throws Exception {
        store.save(new ChatGptCredentials("access-test", "refresh-test", null,
                Instant.now().plusSeconds(3600), "scope-test"));
        ChatGptAutoSignIn autoSignIn = new ChatGptAutoSignIn(store, signIn(SHORT_TIMEOUT), freePort());

        Thread thread = autoSignIn.start();
        thread.join(WAIT.toMillis());

        assertThat(thread.isAlive()).isFalse();
        assertThat(browserUrls).isEmpty();
        assertThat(warnMessages()).isEmpty();
    }

    @Test
    void サインイン済み_期限切れ_でも_自動では始めず_ブラウザも開かない() throws Exception {
        store.save(new ChatGptCredentials("access-test", "refresh-test", null,
                Instant.now().minusSeconds(3600), "scope-test"));
        ChatGptAutoSignIn autoSignIn = new ChatGptAutoSignIn(store, signIn(SHORT_TIMEOUT), freePort());

        Thread thread = autoSignIn.start();
        thread.join(WAIT.toMillis());

        assertThat(thread.isAlive()).isFalse();
        assertThat(browserUrls).isEmpty();
    }

    @Test
    void 時間切れのときは_アプリを止めず_理由と案内を1行だけログに出す() throws Exception {
        ChatGptAutoSignIn autoSignIn = new ChatGptAutoSignIn(store, signIn(SHORT_TIMEOUT), freePort());

        Thread thread = autoSignIn.start();
        thread.join(WAIT.toMillis());

        assertThat(thread.isAlive()).isFalse();
        assertThat(browserUrls).hasSize(1);
        List<String> warnings = warnMessages();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("時間内にサインインが完了しませんでした")
                .contains("ai-account.cmd sign-in を実行してください")
                .doesNotContain("\n");
    }

    @Test
    void ポートが使用中のときは_ブラウザを開かず_案内をログに出す() throws Exception {
        int port = freePort();
        try (ServerSocket occupied = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            ChatGptAutoSignIn autoSignIn = new ChatGptAutoSignIn(store, signIn(SHORT_TIMEOUT), port);

            Thread thread = autoSignIn.start();
            thread.join(WAIT.toMillis());

            assertThat(thread.isAlive()).isFalse();
            assertThat(browserUrls).isEmpty();
            assertThat(warnMessages()).hasSize(1);
            assertThat(warnMessages().get(0))
                    .contains("使用中")
                    .contains("ai-account.cmd sign-in を実行してください");
        }
    }

    @Test
    void スレッドはデーモンで_止めると待ち受けの受け口が閉じ_スレッドが終わる() throws Exception {
        int port = freePort();
        // 戻りを待たせたままにするため、時間切れは長くしておく
        ChatGptAutoSignIn autoSignIn = new ChatGptAutoSignIn(store, signIn(Duration.ofMinutes(5)), port);

        Thread thread = autoSignIn.start();
        assertThat(thread.isDaemon()).isTrue();
        waitUntilBrowserOpened();

        autoSignIn.stop();
        thread.join(WAIT.toMillis());

        assertThat(thread.isAlive()).isFalse();
        // 受け口が閉じているので、同じポートをもう一度使える
        try (ServerSocket again = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            assertThat(again.isBound()).isTrue();
        }
        assertThat(warnMessages()).isEmpty();
        assertThat(infoMessages()).anyMatch(message -> message.contains("中断"));
    }

    /** 認可 URL を開く部品（ブラウザは開かず、URL を記録するだけ）を持つサインインの操作を作る。 */
    private ChatGptSignIn signIn(Duration callbackTimeout) {
        ChatGptSignIn.Browser recordOnly = url -> {
            browserUrls.add(url);
            return true;
        };
        return new ChatGptSignIn(store, new ChatGptOAuthClient(ChatGptEndpoints.official()), new IdTokenVerifier(),
                recordOnly, callbackTimeout, CLOCK,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private List<String> warnMessages() {
        return messages(Level.WARN);
    }

    private List<String> infoMessages() {
        return messages(Level.INFO);
    }

    private List<String> messages(Level level) {
        return logs.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** サインインがブラウザを開く所まで進むのを待つ。 */
    private void waitUntilBrowserOpened() throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (browserUrls.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(browserUrls).hasSize(1);
    }

    /** 空いている番号を1つ借りて、すぐ返す（テストのポートに使う）。 */
    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
