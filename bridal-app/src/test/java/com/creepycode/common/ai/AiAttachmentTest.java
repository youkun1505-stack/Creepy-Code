package com.creepycode.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 添付の型（AiAttachment）と、ダミーの AI が添付を受けたときの動きのテスト。 */
class AiAttachmentTest {

    @Test
    void toStringには中身を出さず_ファイル名と種類と大きさだけを出す() {
        AiAttachment attachment = new AiAttachment("座席表.png", "image/png", "SECRET-BYTES".getBytes());

        String text = attachment.toString();

        assertThat(text)
                .contains("座席表.png")
                .contains("image/png")
                .contains("size=12")
                .doesNotContain("SECRET-BYTES");
    }

    @Test
    void ダミーは添付があっても失敗せず_固定の返事を返す() {
        AiClient dummy = new DummyAiClient();
        List<ChatTurn> history = List.of(new ChatTurn("USER", "質問"));
        AiAttachment pdf = new AiAttachment("予算.pdf", "application/pdf", new byte[] {1, 2, 3});

        String withAttachment = dummy.generateReply("指示", history, pdf);
        String withoutAttachment = dummy.generateReply("指示", history, null);

        assertThat(withAttachment).isNotBlank().isEqualTo(withoutAttachment);
    }
}
