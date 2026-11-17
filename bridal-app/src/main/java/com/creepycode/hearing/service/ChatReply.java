package com.creepycode.hearing.service;

/**
 * AI相談の1回のやり取りの結果。
 * reply は AI の返事。saved は、利用者の発言と返事を DB に保存できたかどうか（false のときは返事だけ画面に出す）。
 */
public record ChatReply(String reply, boolean saved) {
}
