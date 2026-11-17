package com.creepycode.common.ai;

/**
 * AI に一緒に渡す添付ファイル1件。保存はせず、その回の送信のときだけ AI に渡す。
 * 種類（mimeType）は image/png・image/jpeg・application/pdf のいずれか（対応の判断は呼び出す側の AI クライアントが行う）。
 * 中身（data）は利用者のファイルそのものなので、toString には出さない。
 *
 * @param fileName 利用者が選んだファイル名（AI に渡す filename にも使う）
 * @param mimeType 種類（MIME）
 * @param data     中身のバイト列
 */
public record AiAttachment(String fileName, String mimeType, byte[] data) {

    /** 中身（data）と内容が分かる値は出さず、ファイル名・種類・大きさだけを返す。 */
    @Override
    public String toString() {
        return "AiAttachment[fileName=" + fileName + ", mimeType=" + mimeType
                + ", size=" + (data == null ? 0 : data.length) + "]";
    }
}
