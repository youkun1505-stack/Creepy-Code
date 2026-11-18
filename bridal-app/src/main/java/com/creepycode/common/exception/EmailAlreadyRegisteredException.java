package com.creepycode.common.exception;

/**
 * 会員登録で、そのメールアドレスがすでに登録されていたときの例外。
 * 登録の直前の確認をすり抜けて、同じメールアドレスが同時に登録されたとき（USERS の UNIQUE 制約に当たったとき）に投げる。
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyRegisteredException(Throwable cause) {
        super("このメールアドレスはすでに登録されています", cause);
    }
}
