package com.creepycode.hearing.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.AiChatMessageRequest;
import com.creepycode.hearing.dto.AiChatMessageView;
import com.creepycode.hearing.service.AiChatService;
import com.creepycode.hearing.service.ChatReply;

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
    // AI の返事の失敗・履歴の読み出しの失敗のときに利用者へ出す文（例外の中身は出さない）
    private static final String AI_FAILURE_MESSAGE = "AI の返事を作れませんでした。もう一度お試しください。";
    private static final String READ_FAILURE_MESSAGE = "相談の履歴を読み込めませんでした。もう一度お試しください。";
    // 返事は出せたが保存できなかったときに、返事に添える案内
    private static final String SAVE_NOTICE = "この返事は保存できませんでした。画面を読み込み直すと消えます。";

    private final AiChatService aiChatService;

    public DeepDiveController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 画面を表示する。ログイン中の顧客の、それまでの相談の履歴を古い順に渡す（chatMessages）。
     */
    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        List<AiChatMessageView> chatMessages = aiChatService.findHistory(loginUser.getUserId()).stream()
                .map(message -> new AiChatMessageView(message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
        model.addAttribute("chatMessages", chatMessages);
        model.addAttribute("maxChatLength", AiChatService.MAX_LENGTH);
        return VIEW;
    }

    /**
     * 利用者の発言を受けて、AI の返事を JSON で返す。
     * 成功：200 と {ok: true, reply: 返事}。
     * 返事は作れたが保存できなかった：200 と {ok: true, reply: 返事, saved: false, notice: 案内}。
     * この場合は失敗の回数を増やさず、数え直しもしない。
     * 入力の誤り（空・1000字超）：400 と {ok: false, message: 理由}（失敗の回数には数えない）。
     * AI の失敗・履歴の読み出しの失敗（DB）：502 と {ok: false, message, giveUp}。
     * giveUp は連続で3回失敗したら true（プランナーへの依頼を案内する）。
     * 利用者への文には、例外の中身（DB の詳しい情報など）を出さず、原因はログに残す。
     */
    @PostMapping("/messages")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendMessage(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody AiChatMessageRequest request, HttpSession session) {
        String content = request == null ? null : request.content();
        try {
            ChatReply result = aiChatService.sendMessage(loginUser.getUserId(), content);
            if (result.saved()) {
                session.removeAttribute(FAILURE_COUNT);
                return ResponseEntity.ok(Map.of("ok", true, "reply", result.reply()));
            }
            // 返事は出すが、保存できなかったので、失敗の回数は変えない
            return ResponseEntity.ok(Map.of("ok", true, "reply", result.reply(),
                    "saved", false, "notice", SAVE_NOTICE));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        } catch (AiException e) {
            int failures = countFailure(session);
            log.warn("AI相談の返事の作成に失敗しました（{}回目）：{}", failures, e.getMessage());
            return failureResponse(AI_FAILURE_MESSAGE, failures);
        } catch (DataAccessException | TransactionException e) {
            // 発言の前の履歴などの読み出しが失敗した（DB に接続できない場合は TransactionException になる）。
            // AI は呼んでいないが、AI の失敗と同じく回数に数える。原因は最後の引数でスタックトレースに残す
            int failures = countFailure(session);
            log.warn("AI相談の履歴の読み出しに失敗しました（{}回目）：{}：{}",
                    failures, e.getClass().getName(), e.getMessage(), e);
            return failureResponse(READ_FAILURE_MESSAGE, failures);
        }
    }

    /** 失敗の回数を1つ増やして、セッションに覚えておき、増えた後の回数を返す。 */
    private int countFailure(HttpSession session) {
        Integer previous = (Integer) session.getAttribute(FAILURE_COUNT);
        int failures = previous == null ? 1 : previous + 1;
        session.setAttribute(FAILURE_COUNT, failures);
        return failures;
    }

    /** 502 の失敗の応答を作る。3回以上続いたときは giveUp を true にする。 */
    private ResponseEntity<Map<String, Object>> failureResponse(String message, int failures) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("ok", false, "message", message, "giveUp", failures >= MAX_FAILURES));
    }
}
