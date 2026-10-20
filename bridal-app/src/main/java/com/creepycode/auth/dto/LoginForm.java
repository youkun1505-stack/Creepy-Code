package com.creepycode.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * ログイン画面（P-03）の入力内容。
 * メールアドレスの形などは確かめない（間違っていれば、ログインに失敗したときの共通のメッセージを出す）。
 */
public class LoginForm {

    @NotBlank(message = "メールアドレスを入力してください")
    private String email;

    @NotBlank(message = "パスワードを入力してください")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
