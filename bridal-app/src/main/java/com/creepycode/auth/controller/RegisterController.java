package com.creepycode.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.auth.service.RegisterService;
import com.creepycode.common.exception.EmailAlreadyRegisteredException;

import jakarta.validation.Valid;

/**
 * 会員登録画面（P-02）。
 * 登録後はログイン画面へ進め、登録有無を推測できない共通の案内を出す。
 */
@Controller
@RequestMapping("/auth/register")
public class RegisterController {

    private static final String VIEW = "auth/register";
    private static final String URL_LOGIN = "/auth/login";
    private static final String COMPLETE_MESSAGE = "登録を受け付けました。ログイン画面をご確認ください。";

    private final RegisterService registerService;
    private final LoginService loginService;

    public RegisterController(RegisterService registerService, LoginService loginService) {
        this.registerService = registerService;
        this.loginService = loginService;
    }

    /** 会員登録画面を表示する。すでにログインしているときは、種別ごとの画面へ進める（会員登録は非会員だけ。8-1）。 */
    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        if (loginUser != null) {
            return "redirect:" + loginService.homeUrl(loginUser.getRole());
        }
        model.addAttribute("registerForm", new RegisterForm());
        model.addAttribute("loginUrl", URL_LOGIN);
        return VIEW;
    }

    /** 入力を確かめて会員を登録し、ログインした状態でハブへ進める。誤りがあるときは、同じ画面にエラーを出す。 */
    @PostMapping
    public String register(@AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute RegisterForm registerForm, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        // ログイン中の人は登録できない（別の会員を作って、ログインし直すのを防ぐ）
        if (loginUser != null) {
            return "redirect:" + loginService.homeUrl(loginUser.getRole());
        }

        checkPasswordConfirm(registerForm, bindingResult);
        if (bindingResult.hasErrors()) {
            return showAgain(registerForm, model);
        }

        try {
            registerService.register(registerForm);
        } catch (EmailAlreadyRegisteredException e) {
            // 登録済みかどうかを画面から推測できないよう、新規登録と同じ案内を返す
        }

        redirectAttributes.addFlashAttribute("flashMessage", COMPLETE_MESSAGE);
        return "redirect:" + URL_LOGIN;
    }

    /** 入力し直してもらうために、同じ画面をもう一度出す。パスワードは画面に戻さない（氏名とメールアドレスは残す）。 */
    private String showAgain(RegisterForm form, Model model) {
        form.setPassword("");
        form.setPasswordConfirm("");
        model.addAttribute("loginUrl", URL_LOGIN);
        return VIEW;
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
