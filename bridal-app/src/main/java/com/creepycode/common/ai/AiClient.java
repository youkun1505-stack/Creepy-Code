package com.creepycode.common.ai;

import java.time.Duration;
import java.util.List;

/**
 * AI に文章を作らせる窓口。
 * 設定 app.ai.mode によって、ダミー（固定の文章）か本物（ChatGPT のサインイン情報で Responses API を直接呼ぶ）かが切り替わる。
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
     * AI に文章を作らせる。返事全体（トークンの更新・やり直しを含む）の制限時間を timeout で決める。
     * 制限時間を過ぎたら、時間切れの AiException を投げる。
     * 長く待ってよい処理（プラン生成など）だけがこちらを使い、AI に相談のチャットは使わない。
     *
     * @param instructions AI への指示（役割や出力の形式）
     * @param input        AI に渡す文章
     * @param timeout      返事全体の制限時間
     * @return AI が作った文章
     */
    String generate(String instructions, String input, Duration timeout);

    /**
     * それまでのやり取り（発言の並び・古い順）を渡して、AI の返事を作らせる。
     * 最後の発言は利用者の質問である想定。失敗したときは AiException を投げる。
     *
     * @param instructions AI への指示（役割や出力の形式）
     * @param history      やり取りの発言（古い順。最後が利用者の質問）
     * @return AI の返事の文章
     */
    String generateReply(String instructions, List<ChatTurn> history);

    /**
     * それまでのやり取りに加えて、最後の発言に付いた添付ファイルを渡して、AI の返事を作らせる。
     * 添付は、この呼び出しの1回だけ AI に渡す（保存はしない）。attachment が null のときは、添付なしの
     * generateReply(instructions, history) と同じ返事になる。
     * 対応しない種類の添付は、AI に送らずに AiException を投げる。失敗したときも AiException を投げる。
     *
     * @param instructions AI への指示（役割や出力の形式）
     * @param history      やり取りの発言（古い順。最後が利用者の質問）
     * @param attachment   最後の発言に付いた添付（無いときは null）
     * @return AI の返事の文章
     */
    String generateReply(String instructions, List<ChatTurn> history, AiAttachment attachment);
}
