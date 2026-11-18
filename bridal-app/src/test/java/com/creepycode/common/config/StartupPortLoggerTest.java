package com.creepycode.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.server.WebServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 起動時のポート番号ログ（StartupPortLogger）のテスト。
 * 実サーバーは立てず、ポートを返す偽物のイベントを使う（8080 番を使う別の起動と衝突しないため）。
 */
class StartupPortLoggerTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(StartupPortLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private WebServerInitializedEvent eventWithPort(int port) {
        WebServer server = mock(WebServer.class);
        when(server.getPort()).thenReturn(port);
        WebServerInitializedEvent event = mock(WebServerInitializedEvent.class);
        when(event.getWebServer()).thenReturn(server);
        return event;
    }

    @Test
    void 実際に開いたポート番号と_URLをログに出す() {
        new StartupPortLogger("").onApplicationEvent(eventWithPort(8080));

        List<ILoggingEvent> logs = appender.list;
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getLevel().toString()).isEqualTo("INFO");
        assertThat(logs.get(0).getFormattedMessage())
                .contains("port 8080")
                .contains("http://localhost:8080")
                .doesNotContain("http://localhost:8080/");
    }

    @Test
    void ログの文に日本語を含まず_英語と記号だけで出す() {
        new StartupPortLogger("/bridal").onApplicationEvent(eventWithPort(8081));

        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .isEqualTo("bridal-app is ready on port 8081: http://localhost:8081/bridal");
        // 半角の英数字と記号（ASCII の表示できる文字と空白）だけであることを確かめる
        assertThat(message).matches("[\\x20-\\x7E]+");
    }

    @Test
    void 設定の決め打ちではなく_実際に決まったポートを出す() {
        // server.port=0 のときは、空きポートの番号が出る
        new StartupPortLogger("").onApplicationEvent(eventWithPort(54321));

        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("port 54321")
                .contains("http://localhost:54321")
                .doesNotContain("8080");
    }

    @Test
    void context_pathがあれば_URLにも付ける() {
        new StartupPortLogger("/bridal").onApplicationEvent(eventWithPort(9000));

        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("http://localhost:9000/bridal");
    }

    @Test
    void context_pathの書き方が違っても_URLは同じ形になる() {
        assertThat(StartupPortLogger.normalizeContextPath(null)).isEmpty();
        assertThat(StartupPortLogger.normalizeContextPath("")).isEmpty();
        assertThat(StartupPortLogger.normalizeContextPath("/")).isEmpty();
        assertThat(StartupPortLogger.normalizeContextPath("bridal")).isEqualTo("/bridal");
        assertThat(StartupPortLogger.normalizeContextPath("/bridal")).isEqualTo("/bridal");
    }
}
