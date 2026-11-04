package com.creepycode.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * パスワードリセット申請（P-04）の入力フォーム。
 */
public class PasswordResetRequestForm {

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式で入力してください（例：hanako@example.com）")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

