package com.creepycode.auth.controller;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.creepycode.auth.dto.LoginForm;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * ログイン画面（P-03）。
 * ログインできたら、利用者の種別に合わせた画面へ進める（基本設計書 6-2・8-2）。
 */
@Controller
@RequestMapping("/auth/login")
public class LoginController {

    private static final String VIEW = "auth/login";
    private static final String URL_REGISTER = "/auth/register";
    // TODO: パスワードリセット申請（P-04）ができたら、本当の URL に合わせる（しょたこん）
    private static final String URL_PASSWORD_RESET = "/auth/password-reset";
    // どちらが間違っているかは教えない（基本設計書 6-2）
    private static final String FAILED_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** ログイン画面を表示する。すでにログインしているときは、種別ごとの画面へ進める。 */
    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        if (loginUser != null) {
            return "redirect:" + loginService.homeUrl(loginUser.getRole());
        }
        model.addAttribute("loginForm", new LoginForm());
        addLinks(model);
        return VIEW;
    }

    /** メールアドレスとパスワードを確かめ、ログインして種別に合わせた画面へ進める。合わないときは、同じ画面にエラーを出す。 */
    @PostMapping
    public String login(@AuthenticationPrincipal LoginUser currentUser,
            @Valid @ModelAttribute LoginForm loginForm, BindingResult bindingResult,
            Model model, HttpServletRequest request, HttpServletResponse response) {
        // ログイン中に別の人としてログインし直すと、前の人のセッションの情報が残るので受け付けない（先にログアウトしてもらう）
        if (currentUser != null) {
            return "redirect:" + loginService.homeUrl(currentUser.getRole());
        }
        if (bindingResult.hasErrors()) {
            return showAgain(loginForm, model);
        }

        Optional<LoginUser> loginUser = loginService.authenticate(loginForm.getEmail(), loginForm.getPassword());
        if (loginUser.isEmpty()) {
            bindingResult.reject("failed", FAILED_MESSAGE);
            return showAgain(loginForm, model);
        }

        loginService.startSession(loginUser.get(), request, response);
        return "redirect:" + loginService.homeUrl(loginUser.get().getRole());
    }

    /** 入力し直してもらうために、同じ画面をもう一度出す。パスワードは画面に戻さない。 */
    private String showAgain(LoginForm form, Model model) {
        form.setPassword("");
        addLinks(model);
        return VIEW;
    }

    /** 画面のリンク先（会員登録・パスワードを忘れた方）を Model に入れる。 */
    private void addLinks(Model model) {
        model.addAttribute("registerUrl", URL_REGISTER);
        model.addAttribute("passwordResetUrl", URL_PASSWORD_RESET);
    }
}
