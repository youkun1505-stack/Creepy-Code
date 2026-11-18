package com.creepycode.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 制限時間を渡して文章を作らせる呼び方（generate の3つ目の引数）のテスト。
 * 本物の AI は呼ばず、ダミーが新しい呼び方にも同じ固定の文章で応えることを確かめる。
 */
class AiClientTimeoutTest {

    @Test
    void ダミーモードは_制限時間を渡しても_今までと同じ固定の文章を返す() {
        AiClient dummy = new DummyAiClient();

        String short30 = dummy.generate("指示", "入力", Duration.ofSeconds(30));
        String long10 = dummy.generate("指示", "入力", Duration.ofMinutes(10));

        assertThat(short30).isNotBlank().isEqualTo(dummy.generate("指示", "入力"));
        assertThat(long10).isEqualTo(short30);
    }
}
