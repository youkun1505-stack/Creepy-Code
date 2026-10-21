package com.creepycode.auth.controller;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.creepycode.auth.dto.LoginForm;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginService.Account;

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
    // 種別ごとの行き先。顧客はハブ（いまはログイン機能が無いので、ハブの仮の URL）
    // TODO: ログイン機能ができたら、顧客はログイン後のトップページ（/）へ進める（しょたこん）
    private static final String URL_CUSTOMER_HOME = "/after-login-tmp";
    // TODO: プランナーダッシュボード（P-15）・管理者ダッシュボード（P-18）ができたら、本当の URL に合わせる（しょたこん）
    private static final String URL_PLANNER_HOME = "/planner/dashboard";
    private static final String URL_ADMIN_HOME = "/admin/dashboard";
    // どちらが間違っているかは教えない（基本設計書 6-2）
    private static final String FAILED_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** ログイン画面を表示する。 */
    @GetMapping
    public String show(Model model) {
        model.addAttribute("loginForm", new LoginForm());
        addLinks(model);
        return VIEW;
    }

    /** メールアドレスとパスワードを確かめ、利用者の種別に合わせた画面へ進める。合わないときは、同じ画面にエラーを出す。 */
    @PostMapping
    public String login(@Valid @ModelAttribute LoginForm loginForm, BindingResult bindingResult,
            Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return showAgain(loginForm, model);
        }

        Optional<Account> account = loginService.authenticate(loginForm.getEmail(), loginForm.getPassword());
        if (account.isEmpty()) {
            bindingResult.reject("failed", FAILED_MESSAGE);
            return showAgain(loginForm, model);
        }

        // ハブのヘッダーに、ログインした人の名前を出すために渡す（TopController が受け取る。ログイン機能ができたら不要）
        redirectAttributes.addFlashAttribute("flashMemberName", account.get().name());
        return "redirect:" + homeUrl(account.get());
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

    /** 利用者の種別に合わせた、ログイン後の行き先を返す。 */
    private String homeUrl(Account account) {
        return switch (account.role()) {
            case CUSTOMER -> URL_CUSTOMER_HOME;
            case PLANNER -> URL_PLANNER_HOME;
            case ADMIN -> URL_ADMIN_HOME;
        };
    }
}
