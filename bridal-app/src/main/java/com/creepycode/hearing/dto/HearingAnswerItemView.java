package com.creepycode.hearing.dto;

/**
 * 「かんたん質問の答え」の1項目（AI相談の画面に、読むだけで出す）。
 *
 * @param label 項目の名前（例：理想のカップル像）
 * @param value 答えの中身（例：まだ決まっていない）
 */
public record HearingAnswerItemView(String label, String value) {
}
