package com.creepycode.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 会員登録画面（P-02）の入力内容。
 * 入力の決まりは基本設計書 6-2 と T-01 USERS（氏名は100文字、メールアドレスは255文字まで）に合わせている。
 */
public class RegisterForm {

    @NotBlank(message = "氏名を入力してください")
    @Size(max = 100, message = "氏名は100文字以内で入力してください")
    // @NotBlank は全角の空白を空白とみなさないので、全角の空白を含む空白だけの氏名をここで弾く
    // （半角の空白だけ・未入力は @NotBlank に任せ、同じメッセージが2つ並ばないようにしている）
    @Pattern(regexp = "^\\s*$|.*[^\\s\\u3000].*", message = "氏名を入力してください")
    private String name;

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形で入力してください（例：hanako@example.com）")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    private String email;

    // 半角の英字・数字・記号で8文字以上、そのうち英字と数字を1文字ずつ以上含む（基本設計書 6-2）
    // 未入力のときは @NotBlank のメッセージだけを出したいので、空の文字は ^$ で通している
    @NotBlank(message = "パスワードを入力してください")
    @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,}$",
            message = "パスワードは8文字以上で、半角の英字と数字を両方含めてください")
    // BCrypt は先頭の72バイトまでしか使わず、それより長いとハッシュ化できないため上限を付ける（半角だけなので72文字＝72バイト）
    @Size(max = 72, message = "パスワードは72文字以内で入力してください")
    private String password;

    // パスワードと同じかどうかは、RegisterController で確かめる
    @NotBlank(message = "確認用のパスワードを入力してください")
    private String passwordConfirm;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }
}
