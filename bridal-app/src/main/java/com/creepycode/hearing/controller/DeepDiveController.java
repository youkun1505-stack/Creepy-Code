package com.creepycode.hearing.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.ai.AiAttachment;
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
     * 入力の誤り（空・1000字超）：400 と {ok: false, message: 理由}（失敗の回数には数えない）。
     * AI の失敗：502 と {ok: false, message, giveUp}。giveUp は連続で3回失敗したら true（プランナーへの依頼を案内する）。
     */
    @PostMapping("/messages")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendMessage(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody AiChatMessageRequest request, HttpSession session) {
        String content = request == null ? null : request.content();
        return reply(loginUser.getUserId(), content, null, session);
    }

    /**
     * 利用者の発言と、添付ファイル1件（任意）を、multipart/form-data で受けて、AI の返事を JSON で返す。
     * 形式は JSON の送信と同じ。添付は PNG・JPEG・PDF のみ、1件まで、10MB まで。
     * 添付の誤り（2件以上・空・種類違い・10MB 超え）は、AI を呼ばず 400 と理由を返す。
     */
    @PostMapping(value = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendMessageWithFile(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "content", required = false) String content,
            @RequestParam(name = "file", required = false) List<MultipartFile> files, HttpSession session) {
        AiAttachment attachment;
        try {
            attachment = toAttachment(files);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
        return reply(loginUser.getUserId(), content, attachment, session);
    }

    /** AI に発言を渡して、返事を JSON で返す（JSON の送信と添付つきの送信で共通）。 */
    private ResponseEntity<Map<String, Object>> reply(Long userId, String content, AiAttachment attachment,
            HttpSession session) {
        try {
            // 添付が無いときは、今までと同じ2引数の呼び出しにする
            String reply = attachment == null ? aiChatService.sendMessage(userId, content)
                    : aiChatService.sendMessage(userId, content, attachment);
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

    /**
     * 届いたファイルを1件の添付にする。ファイル欄が空（名前が無い）のときは添付なし（null）。
     * 2件以上・中身を読めないときは IllegalArgumentException を投げる。
     */
    private static AiAttachment toAttachment(List<MultipartFile> files) {
        if (files == null) {
            return null;
        }
        // ファイルを選んでいない欄は、名前も中身も空で届くので数えない
        List<MultipartFile> chosen = files.stream()
                .filter(file -> (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                        || file.getSize() > 0)
                .toList();
        if (chosen.isEmpty()) {
            return null;
        }
        if (chosen.size() > 1) {
            throw new IllegalArgumentException("添付は1回に1ファイルまでです。");
        }
        MultipartFile file = chosen.get(0);
        try {
            return new AiAttachment(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("ファイルを読み込めませんでした。もう一度選んでください。");
        }
    }
}
