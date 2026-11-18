package com.creepycode.common.ai.chatgpt;

/**
 * サインインを1回試すときの控え（認可 URL・state・nonce・verifier など）。
 * 戻りの値と照らすために、ブラウザを開く前に作って保持する。秘密の項目は toString に出さない。
 *
 * @param authorizationUrl   ブラウザで開く認可 URL（state・nonce・challenge を含む）
 * @param redirectUri        戻り先の URL（http://127.0.0.1:{port}/auth/callback）
 * @param state              state（秘密。戻りの値と一致するか確かめる）
 * @param nonce              nonce（秘密。ID トークンの確認で使う）
 * @param codeVerifier       code_verifier（秘密。トークン交換で送る）
 * @param hostId             ホストごとの登録の値（ext_agent_host_id）
 * @param registeredClientId 登録済みの client_id。初回の登録のときは null（戻りで発行される）
 */
public record ChatGptAuthAttempt(
        String authorizationUrl,
        String redirectUri,
        String state,
        String nonce,
        String codeVerifier,
        String hostId,
        String registeredClientId) {

    /** 初回の登録（発行された client_id を戻りで受け取る）かどうか。 */
    public boolean isInitialRegistration() {
        return registeredClientId == null;
    }

    /** 秘密の項目は出さない。 */
    @Override
    public String toString() {
        return "ChatGptAuthAttempt[authorizationUrl=***, redirectUri=" + redirectUri + ", state=***, nonce=***, "
                + "codeVerifier=***, hostId=" + hostId + ", registeredClientId=" + registeredClientId + "]";
    }
}
