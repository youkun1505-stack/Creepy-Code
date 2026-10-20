package com.creepycode.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.creepycode.auth.dto.RegisterForm;
import com.creepycode.auth.service.RegisterService;

import jakarta.validation.Valid;

/**
 * 会員登録画面（P-02）。
 * 登録が終わったら、そのままログイン後のトップページ（P-01 のハブ）へ進め、完了の案内を出す。
 */
@Controller
@RequestMapping("/auth/register")
public class RegisterController {

    private static final String VIEW = "auth/register";
    // 登録したら、すぐにログイン後のハブへ進める（いまはログイン機能が無いので、ハブの仮の URL の「まだ何もしていない」状態）
    // TODO: ログイン機能ができたら、ここで登録した会員を自動でログインさせ、ログイン後のトップページ（/）へ進める（しょたこん）
    private static final String URL_AFTER_REGISTER = "/after-login-tmp";
    private static final String URL_LOGIN = "/auth/login";
    // ハブ（P-01）の上に一度だけ出す案内
    private static final String COMPLETE_MESSAGE = "会員登録が完了しました。まずはかんたんな質問からはじめましょう。";

    private final RegisterService registerService;

    public RegisterController(RegisterService registerService) {
        this.registerService = registerService;
    }

    /** 会員登録画面を表示する。 */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        model.addAttribute("loginUrl", URL_LOGIN);
        return VIEW;
    }

    /** 入力を確かめて会員を登録し、ログイン後のハブへ進める。誤りがあるときは、同じ画面にエラーを出す。 */
    @PostMapping
    public String register(@Valid @ModelAttribute RegisterForm registerForm, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        checkPasswordConfirm(registerForm, bindingResult);
        // メールアドレスの形が正しいときだけ、登録済みかを確かめる（エラーを2つ重ねて出さないため）
        if (!bindingResult.hasFieldErrors("email") && registerService.isEmailRegistered(registerForm.getEmail())) {
            bindingResult.rejectValue("email", "registered",
                    "このメールアドレスはすでに登録されています。ログインしてください");
        }

        if (bindingResult.hasErrors()) {
            // パスワードは画面に戻さず、入力し直してもらう（氏名とメールアドレスは残す）
            registerForm.setPassword("");
            registerForm.setPasswordConfirm("");
            model.addAttribute("loginUrl", URL_LOGIN);
            return VIEW;
        }

        registerService.register(registerForm);
        redirectAttributes.addFlashAttribute("flashMessage", COMPLETE_MESSAGE);
        // ハブのヘッダーに、登録した氏名を出すために渡す（TopController が受け取る。ログイン機能ができたら不要）
        redirectAttributes.addFlashAttribute("flashMemberName", registerForm.getName().strip());
        return "redirect:" + URL_AFTER_REGISTER;
    }

    /** 確認用のパスワードが、パスワードと同じかを確かめる。違うときは確認用の欄にエラーを付ける。 */
    private void checkPasswordConfirm(RegisterForm form, BindingResult bindingResult) {
        // 確認用が未入力のときは、@NotBlank のメッセージだけを出す
        if (!StringUtils.hasText(form.getPasswordConfirm())) {
            return;
        }
        if (!form.getPasswordConfirm().equals(form.getPassword())) {
            bindingResult.rejectValue("passwordConfirm", "mismatch", "パスワードと確認用のパスワードが一致しません");
        }
    }
}
