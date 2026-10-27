package com.creepycode.common.ai;

import java.util.List;

/**
 * AI に文章を作らせる窓口。
 * 設定 app.ai.mode によって、ダミー（固定の文章）か本物（中継プログラム経由）かが切り替わる。
 */
public interface AiClient {

    /**
     * AI に文章を作らせる。失敗したときは AiException を投げる。
     *
     * @param instructions AI への指示（役割や出力の形式）
     * @param input        AI に渡す文章
     * @return AI が作った文章
     */
    String generate(String instructions, String input);

    /**
     * それまでのやり取り（発言の並び・古い順）を渡して、AI の返事を作らせる。
     * 最後の発言は利用者の質問である想定。失敗したときは AiException を投げる。
     *
     * @param instructions AI への指示（役割や出力の形式）
     * @param history      やり取りの発言（古い順。最後が利用者の質問）
     * @return AI の返事の文章
     */
    String generateReply(String instructions, List<ChatTurn> history);
}
