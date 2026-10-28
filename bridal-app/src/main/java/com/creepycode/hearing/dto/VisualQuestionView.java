package com.creepycode.hearing.dto;

/**
 * Step2（P-07）の、テイスト1つ分の問い。
 * baseImage は1回目に1枚ずつ見せる画像。leftImage・rightImage は、2回目に「どちらが近い？」と並べて見せる2枚
 * （画像がそろっていないときは null。そのテイストは2回目を出さない）。
 * tasteKey は、1回目で「好き」を選んだテイストの2回目を JS で出すための目印（画面には出さない）。
 */
public record VisualQuestionView(String tasteKey, String tasteLabel, VisualImageView baseImage,
        VisualImageView leftImage, VisualImageView rightImage) {

    /** 2回目（どちらが近い？）を出せるか。 */
    public boolean hasPair() {
        return leftImage != null && rightImage != null;
    }
}
