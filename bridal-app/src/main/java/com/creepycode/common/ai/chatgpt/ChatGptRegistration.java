package com.creepycode.common.ai.chatgpt;

import java.util.Objects;

/**
 * Sign in with ChatGPT の登録情報。秘密ではないので、資格情報とは別の（暗号化しない）ファイルに残す。
 *
 * @param hostId   ホストごとの登録の値（ext_agent_host_id）
 * @param clientId 公式の仕組みで発行された client_id。初回の登録の結果
 */
public record ChatGptRegistration(String hostId, String clientId) {

    public ChatGptRegistration {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(clientId, "clientId");
    }
}
