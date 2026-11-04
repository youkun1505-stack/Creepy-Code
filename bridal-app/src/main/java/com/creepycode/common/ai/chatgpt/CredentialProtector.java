package com.creepycode.common.ai.chatgpt;

/**
 * 資格情報を暗号化・復号する部品。本物は WindowsDpapiProtector（Windows の DPAPI）。
 * テストでは偽の部品に差し替える。
 */
public interface CredentialProtector {

    /**
     * データを暗号化する。失敗したときは IllegalStateException を投げる（中身は例外に入れない）。
     *
     * @param plain 暗号化する前のデータ
     * @return 暗号化したデータ
     */
    byte[] protect(byte[] plain);

    /**
     * 暗号化したデータを元に戻す。失敗したときは IllegalStateException を投げる（中身は例外に入れない）。
     *
     * @param encrypted 暗号化したデータ
     * @return 元のデータ
     */
    byte[] unprotect(byte[] encrypted);
}
