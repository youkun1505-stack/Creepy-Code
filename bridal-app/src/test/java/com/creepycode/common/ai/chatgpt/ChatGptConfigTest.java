package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 設定（app.ai.mode・app.ai.auto-sign-in）ごとに、サインインの部品が作られるかを確かめる。
 * 起動はしない（ApplicationReadyEvent は出さない）ので、ブラウザも開かず、通信もしない。
 */
class ChatGptConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ChatGptConfig.class);

    @Test
    void chatgptモードでは_サインインの部品と自動サインインを作る() {
        runner.withPropertyValues("app.ai.mode=chatgpt").run(context -> {
            assertThat(context).hasSingleBean(ChatGptSignIn.class);
            assertThat(context).hasSingleBean(ChatGptAutoSignIn.class);
        });
    }

    @Test
    void auto_sign_inをfalseにすると_サインインの部品だけを作り_自動サインインは作らない() {
        runner.withPropertyValues("app.ai.mode=chatgpt", "app.ai.auto-sign-in=false").run(context -> {
            assertThat(context).hasSingleBean(ChatGptSignIn.class);
            assertThat(context).doesNotHaveBean(ChatGptAutoSignIn.class);
        });
    }

    @Test
    void dummyモードでは_サインインの部品も自動サインインも作らない() {
        runner.withPropertyValues("app.ai.mode=dummy").run(context -> {
            assertThat(context).doesNotHaveBean(ChatGptSignIn.class);
            assertThat(context).doesNotHaveBean(ChatGptAutoSignIn.class);
        });
    }

    // 指定が無い場合は、環境変数 APP_AI_MODE（Spring は app.ai.mode をこの名前からも読む）の値が効いてしまうため、
    // 空の値を入れて「chatgpt 以外」の場合を確かめる（空は dummy と同じく、何も作らない）
    @Test
    void モードが空のとき_chatgpt_以外として_何も作らない() {
        runner.withPropertyValues("app.ai.mode=").run(context -> {
            assertThat(context).doesNotHaveBean(ChatGptSignIn.class);
            assertThat(context).doesNotHaveBean(ChatGptAutoSignIn.class);
        });
    }
}
