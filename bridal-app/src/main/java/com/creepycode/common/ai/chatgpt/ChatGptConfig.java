package com.creepycode.common.ai.chatgpt;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sign in with ChatGPT の部品を Spring に登録する（app.ai.mode=chatgpt のときだけ）。
 * 組み立て方は ChatGptSignInMain（コマンド）と同じにしている。dummy モードでは何も作らない。
 */
@Configuration
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "chatgpt")
public class ChatGptConfig {

    /** 資格情報と登録情報の保存先（既定の保存先と Windows の暗号化）。 */
    @Bean
    public ChatGptCredentialStore chatGptCredentialStore() {
        return new ChatGptCredentialStore(ChatGptCredentialStore.defaultDirectory(), new WindowsDpapiProtector());
    }

    /** サインインの操作（自動サインインと、今後のトークン更新で使う）。 */
    @Bean
    public ChatGptSignIn chatGptSignIn(ChatGptCredentialStore store) {
        return new ChatGptSignIn(
                store,
                new ChatGptOAuthClient(ChatGptEndpoints.official()),
                new IdTokenVerifier(),
                ChatGptSignInMain.CALLBACK_TIMEOUT,
                Clock.systemUTC(),
                System.out);
    }

    /** 起動したときの自動サインイン（app.ai.auto-sign-in=false なら作らない）。 */
    @Bean
    @ConditionalOnProperty(name = "app.ai.auto-sign-in", havingValue = "true", matchIfMissing = true)
    public ChatGptAutoSignIn chatGptAutoSignIn(ChatGptCredentialStore store, ChatGptSignIn signIn) {
        return new ChatGptAutoSignIn(store, signIn);
    }
}
