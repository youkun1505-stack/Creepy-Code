package com.creepycode.hearing.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.AiChatMessageRequest;
import com.creepycode.hearing.dto.AiChatMessageView;
import com.creepycode.hearing.service.AiChatService;

import jakarta.servlet.http.HttpSession;

/**
 * AIに相談する画面（P-10）。チャット形式で、利用者の発言に AI が答える。
 */
@Controller
@RequestMapping("/hearing/deepdive")
public class DeepDiveController {

    private static final Logger log = LoggerFactory.getLogger(DeepDiveController.class);

    private static final String VIEW = "hearing/deepdive";
    // AI の返事の失敗が続いた回数を、セッションに覚えておくときの名前
    private static final String FAILURE_COUNT = "deepDiveFailureCount";
    // 基本設計書 6-5：3回連続で失敗したら、プランナーへの手動作成の依頼を案内する
    private static final int MAX_FAILURES = 3;

    private final AiChatService aiChatService;
    // TODO: ログイン機能（別担当）ができたら、ログイン中の顧客の USER_ID に置き換える（Claude）
    private final Long userId;

    public DeepDiveController(AiChatService aiChatService, @Value("${app.hearing.dev-user-id}") Long userId) {
        this.aiChatService = aiChatService;
        this.userId = userId;
    }

    /**
     * 画面を表示する。それまでの相談の履歴を古い順に渡す（chatMessages）。
     */
    @GetMapping
    public String show(Model model) {
        List<AiChatMessageView> chatMessages = aiChatService.findHistory(userId).stream()
                .map(message -> new AiChatMessageView(message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
        model.addAttribute("chatMessages", chatMessages);
        model.addAttribute("maxChatLength", AiChatService.MAX_LENGTH);
        return VIEW;
    }

    /**
     * 利用者の発言を受けて、AI の返事を JSON で返す。
     * 成功：200 と {ok: true, reply: 返事}。
     * 入力の誤り（空・1000字超）：400 と {ok: false, message: 理由}（失敗の回数には数えない）。
     * AI の失敗：502 と {ok: false, message, giveUp}。giveUp は連続で3回失敗したら true（プランナーへの依頼を案内する）。
     */
    @PostMapping("/messages")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody AiChatMessageRequest request,
            HttpSession session) {
        String content = request == null ? null : request.content();
        try {
            String reply = aiChatService.sendMessage(userId, content);
            session.removeAttribute(FAILURE_COUNT);
            return ResponseEntity.ok(Map.of("ok", true, "reply", reply));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        } catch (AiException e) {
            Integer previous = (Integer) session.getAttribute(FAILURE_COUNT);
            int failures = previous == null ? 1 : previous + 1;
            session.setAttribute(FAILURE_COUNT, failures);
            log.warn("AI相談の返事の作成に失敗しました（{}回目）：{}", failures, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("ok", false,
                            "message", "AI の返事を作れませんでした。もう一度お試しください。",
                            "giveUp", failures >= MAX_FAILURES));
        }
    }
}
