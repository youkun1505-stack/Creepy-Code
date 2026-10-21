package com.creepycode.common.ai;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ダミーモードの AI。本物の AI を呼ばずに、固定の文章を返す（app.ai.mode=dummy のとき。初期値）。
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "dummy", matchIfMissing = true)
public class DummyAiClient implements AiClient {

    // 深掘り質問（P-10）用の固定の返答。本物の AI と同じく「質問の配列（JSON）」の形にしている
    private static final String DUMMY_RESPONSE = """
            ["【ダミー】結婚式で、これだけは譲れないポイントは何ですか？",
             "【ダミー】予算内に収めるために、削ってもよいと思う部分はどこですか？",
             "【ダミー】ゲストにどんな気持ちで帰ってもらいたいですか？"]
            """;

    // AI相談チャット（P-10）用の固定の返事
    private static final String DUMMY_CHAT_RESPONSE = "【ダミー】ご質問ありがとうございます。これはダミーモードの返事です。";

    /** 指示や入力の内容にかかわらず、固定の文章を返す。 */
    @Override
    public String generate(String instructions, String input) {
        return DUMMY_RESPONSE;
    }

    /** やり取りの内容にかかわらず、固定の返事を返す。 */
    @Override
    public String generateReply(String instructions, List<ChatTurn> history) {
        return DUMMY_CHAT_RESPONSE;
    }
}
