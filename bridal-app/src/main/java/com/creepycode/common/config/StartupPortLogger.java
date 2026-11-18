package com.creepycode.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 起動が終わったときに、実際に開いたポート番号とブラウザで開く URL をログに出す。
 *
 * 設定値の決め打ちではなく、実際に決まったポートを使う。server.port=0（空きポートを自動で選ぶ）のときも正しい番号が出る。
 * ポートが決まるのは WebServerInitializedEvent のあとなので、このイベントを受け取って出す。
 */
@Component
public class StartupPortLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupPortLogger.class);

    private final String contextPath;

    /** context-path は設定がなければ空（ルートで開く）。 */
    public StartupPortLogger(@Value("${server.servlet.context-path:}") String contextPath) {
        this.contextPath = normalizeContextPath(contextPath);
    }

    /** 起動したポートを受け取り、ポート番号と開く URL をログに出す。 */
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        log.info(buildMessage(port));
    }

    /** ログに出す1行の文章を作る。ポート番号と、ブラウザで開く URL を含む。 */
    String buildMessage(int port) {
        return "bridal-app is ready on port " + port
                + ": http://localhost:" + port + contextPath;
    }

    /** context-path の前後の / をそろえる。空や「/」のときは空文字（ルートで開く）にする。 */
    static String normalizeContextPath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return "";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
