package com.creepycode.auth.controller;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.creepycode.auth.dto.PasswordResetConfirmForm;
import com.creepycode.auth.dto.PasswordResetRequestForm;
import com.creepycode.auth.service.PasswordResetService;

import jakarta.validation.Valid;

/**
 * パスワードリセット申請（P-04）および再設定（P-05）の画面。
 */
@Controller
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private static final String VIEW_REQUEST = "auth/password-reset-request";
    private static final String VIEW_REQUESTED = "auth/password-reset-requested";
    private static final String VIEW_CONFIRM = "auth/password-reset-confirm";
    private static final String VIEW_COMPLETED = "auth/password-reset-completed";
    private static final String VIEW_INVALID = "auth/password-reset-invalid";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** パスワードリセット申請画面（P-04）を表示する。 */
    @GetMapping
    public String showRequestForm(@ModelAttribute PasswordResetRequestForm form) {
        return VIEW_REQUEST;
    }

    /** パスワードリセット申請を受け付け、案内送信（またはログ出力）を行う。 */
    @PostMapping
    public String handleRequest(@Valid @ModelAttribute PasswordResetRequestForm form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return VIEW_REQUEST;
        }

        Optional<String> tokenOpt = passwordResetService.requestPasswordReset(form.getEmail());
        // 発表・動作確認用：トークンが発行された場合は画面にもリンクを出す（基本設計書 v1.6 の代替仕様）
        tokenOpt.ifPresent(token -> {
            model.addAttribute("demoResetUrl", "/auth/password-reset/confirm?token=" + token);
        });

        return VIEW_REQUESTED;
    }

    /** パスワード再設定画面（P-05）を表示する。トークンが無効ならエラー画面を返す。 */
    @GetMapping("/confirm")
    public String showConfirmForm(@RequestParam(name = "token", required = false) String token,
            @ModelAttribute PasswordResetConfirmForm form, Model model) {
        if (!passwordResetService.validateToken(token)) {
            return VIEW_INVALID;
        }
        form.setToken(token);
        return VIEW_CONFIRM;
    }

    /** 新しいパスワードを検証して保存する。 */
    @PostMapping("/confirm")
    public String handleConfirm(@Valid @ModelAttribute PasswordResetConfirmForm form,
            BindingResult bindingResult, Model model) {
        // トークンがそもそも有効かを再確認
        if (!passwordResetService.validateToken(form.getToken())) {
            return VIEW_INVALID;
        }

        // パスワードと確認用パスワードの一致チェック
        if (form.getPassword() != null && form.getPasswordConfirm() != null
                && !form.getPassword().equals(form.getPasswordConfirm())) {
            bindingResult.rejectValue("passwordConfirm", "mismatch", "確認用のパスワードが一致しません");
        }

        if (bindingResult.hasErrors()) {
            return VIEW_CONFIRM;
        }

        boolean success = passwordResetService.resetPassword(form.getToken(), form.getPassword());
        if (!success) {
            return VIEW_INVALID;
        }

        return VIEW_COMPLETED;
    }
}

