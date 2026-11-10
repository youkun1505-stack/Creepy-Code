package com.creepycode.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** デモログインでセッション表示名として使う入力内容。 */
public class DemoLoginForm {

    @NotBlank(message = "名前を入力してください")
    @Size(max = 100, message = "名前は100文字以内で入力してください")
    private String name = "田中太郎";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
