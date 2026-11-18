package com.creepycode.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * パスワード再設定画面（P-05）の入力フォーム。
 * 基本設計書 6-2 に基づくパスワード要件（半角英数字混合8文字以上72文字以内）。
 */
public class PasswordResetConfirmForm {

    @NotBlank(message = "トークンが不正です")
    private String token;

    @NotBlank(message = "新しいパスワードを入力してください")
    @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,}$",
            message = "パスワードは8文字以上で、半角の英字と数字を両方含めてください")
    @Size(max = 72, message = "パスワードは72文字以内で入力してください")
    private String password;

    @NotBlank(message = "確認用のパスワードを入力してください")
    private String passwordConfirm;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }
}

