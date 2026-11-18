package com.creepycode.common.ai.chatgpt;

/**
 * サインインが成功したときの結果。保存は呼び出し側（戻り先のサーバーとコマンド）が行う。
 *
 * @param credentials 交換で得た資格情報（トークン一式）
 * @param registration 登録情報（hostId と、使った client_id）
 */
public record ChatGptSignInResult(ChatGptCredentials credentials, ChatGptRegistration registration) {
}
