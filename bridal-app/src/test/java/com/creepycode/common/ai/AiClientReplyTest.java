package com.creepycode.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * やり取りを渡して返事を受け取る呼び出し（generateReply）のテスト。
 * 本物の中継プログラムは呼ばず、履歴を入力の文章にまとめる処理だけを確かめる。
 */
class AiClientReplyTest {

    @Test
    void ダミーモードは_やり取りにかかわらず固定の返事を返す() {
        String reply = new DummyAiClient().generateReply("指示", List.of(new ChatTurn("USER", "質問")));

        assertThat(reply).isNotBlank().startsWith("【ダミー】");
    }

    @Test
    void 中継プログラム用の入力文章は_利用者とAIの発言を古い順に並べる() {
        List<ChatTurn> history = List.of(
                new ChatTurn("USER", "最初の質問"),
                new ChatTurn("AI", "最初の返事"),
                new ChatTurn("USER", "今回の質問\n2行目"));

        String text = RelayAiClient.toInputText(history);

        assertThat(text).contains("利用者：最初の質問\nAI：最初の返事\n利用者：今回の質問\n2行目\n");
        assertThat(text.indexOf("最初の質問")).isLessThan(text.indexOf("最初の返事"));
        assertThat(text.indexOf("最初の返事")).isLessThan(text.indexOf("今回の質問"));
    }
}
