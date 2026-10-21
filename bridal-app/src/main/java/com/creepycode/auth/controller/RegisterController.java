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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 会員登録画面（P-02）。
 * 登録が終わったら、そのままログインした状態にしてログイン後のトップページ（P-01 のハブ）へ進め、完了の案内を出す。
 */
@Controller
@RequestMapping("/auth/register")
public class RegisterController {

    private static final String VIEW = "auth/register";
    // 登録したら、すぐにログイン後のトップページ（P-01 のハブ）へ進める
    private static final String URL_AFTER_REGISTER = "/";
    private static final String URL_LOGIN = "/auth/login";
    // ハブ（P-01）の上に一度だけ出す案内
    private static final String COMPLETE_MESSAGE = "会員登録が完了しました。まずはかんたんな質問からはじめましょう。";
    private static final String REGISTERED_MESSAGE = "このメールアドレスはすでに登録されています。ログインしてください";

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
            Model model, RedirectAttributes redirectAttributes,
            HttpServletRequest request, HttpServletResponse response) {
        // ログイン中の人は登録できない（別の会員を作って、ログインし直すのを防ぐ）
        if (loginUser != null) {
            return "redirect:" + loginService.homeUrl(loginUser.getRole());
        }

        checkPasswordConfirm(registerForm, bindingResult);
        // メールアドレスの形が正しいときだけ、登録済みかを確かめる（エラーを2つ重ねて出さないため）
        if (!bindingResult.hasFieldErrors("email") && registerService.isEmailRegistered(registerForm.getEmail())) {
            bindingResult.rejectValue("email", "registered", REGISTERED_MESSAGE);
        }
        if (bindingResult.hasErrors()) {
            return showAgain(registerForm, model);
        }

        LoginUser registered;
        try {
            registered = registerService.register(registerForm);
        } catch (EmailAlreadyRegisteredException e) {
            // 上の確認のあとに、同じメールアドレスが登録されたとき
            bindingResult.rejectValue("email", "registered", REGISTERED_MESSAGE);
            return showAgain(registerForm, model);
        }

        // 登録した会員を、ログイン画面を通さずにそのままログインさせる（P-02 の仕様変更）
        loginService.startSession(registered, request, response);
        redirectAttributes.addFlashAttribute("flashMessage", COMPLETE_MESSAGE);
        return "redirect:" + URL_AFTER_REGISTER;
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
