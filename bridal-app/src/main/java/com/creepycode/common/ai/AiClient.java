package com.creepycode.common.ai;

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
}
