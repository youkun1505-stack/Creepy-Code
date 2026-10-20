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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.AiChatMessageRequest;
import com.creepycode.hearing.dto.AiChatMessageView;
import com.creepycode.hearing.dto.DeepDiveForm;
import com.creepycode.hearing.dto.DeepDiveQa;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.service.AiChatService;
import com.creepycode.hearing.service.DeepDiveService;

import jakarta.servlet.http.HttpSession;

/**
 * AI深掘り質問画面（P-10）。
 */
@Controller
@RequestMapping("/hearing/deepdive")
public class DeepDiveController {

    private static final Logger log = LoggerFactory.getLogger(DeepDiveController.class);

    private static final String VIEW = "hearing/deepdive";
    // 質問の生成に続けて失敗した回数を、セッションに覚えておくときの名前
    private static final String FAILURE_COUNT = "deepDiveFailureCount";
    // 基本設計書 6-5：3回連続で失敗したら、プランナーへの手動作成の依頼を案内する
    private static final int MAX_FAILURES = 3;
    private static final int MAX_ANSWER_LENGTH = 1000;
    // AI相談チャットの発言の上限（字）。AiChatService.MAX_LENGTH と同じ値（Service は別パッケージで非公開のため、ここにも置く）
    private static final int MAX_CHAT_LENGTH = 1000;

    private final DeepDiveService deepDiveService;
    private final AiChatService aiChatService;
    // TODO: ログイン機能（別担当）ができたら、ログイン中の顧客の USER_ID に置き換える（Claude）
    private final Long userId;

    public DeepDiveController(DeepDiveService deepDiveService, AiChatService aiChatService,
            @Value("${app.hearing.dev-user-id}") Long userId) {
        this.deepDiveService = deepDiveService;
        this.aiChatService = aiChatService;
        this.userId = userId;
    }

    /**
     * 画面を表示する。それまでの相談の履歴を古い順に渡す（chatMessages）。
     * 仮の回答は作らない（findOrCreateAnswer を呼ばない）。
     */
    @GetMapping
    public String show(Model model) {
        List<AiChatMessageView> chatMessages = aiChatService.findHistory(userId).stream()
                .map(message -> new AiChatMessageView(message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
        model.addAttribute("chatMessages", chatMessages);
        model.addAttribute("maxChatLength", MAX_CHAT_LENGTH);
        // 古い質問フォームの画面（F-4 で作り直すまで）が壊れないように、空の入力を渡しておく
        model.addAttribute("deepDiveForm", new DeepDiveForm());
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

    /** AI に質問を作らせる（画面の「生成中」表示から呼ばれる）。結果は JSON で返す。 */
    @PostMapping("/questions")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generate(HttpSession session) {
        OnboardingAnswer answer = deepDiveService.findOrCreateAnswer(userId);
        try {
            deepDiveService.generateQuestions(answer);
            session.removeAttribute(FAILURE_COUNT);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (AiException e) {
            Integer previous = (Integer) session.getAttribute(FAILURE_COUNT);
            int failures = previous == null ? 1 : previous + 1;
            session.setAttribute(FAILURE_COUNT, failures);
            log.warn("深掘り質問の生成に失敗しました（{}回目）：{}", failures, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("ok", false, "giveUp", failures >= MAX_FAILURES));
        }
    }

    /** 回答を保存する。 */
    @PostMapping
    public String submit(@ModelAttribute DeepDiveForm deepDiveForm, Model model) {
        OnboardingAnswer answer = deepDiveService.findOrCreateAnswer(userId);
        List<DeepDiveQa> qaList = deepDiveService.readQaList(answer);
        List<String> answers = deepDiveForm.getAnswers();
        if (qaList.isEmpty() || answers.size() != qaList.size()) {
            // 質問がまだ無い、または質問と回答の数が合わない（画面が古いなど）ときは、表示からやり直す
            return "redirect:/hearing/deepdive";
        }
        if (answers.stream().anyMatch(text -> text == null || text.isBlank())) {
            model.addAttribute("errorMessage", "すべての質問に回答してください。");
        } else if (answers.stream().anyMatch(text -> text.length() > MAX_ANSWER_LENGTH)) {
            model.addAttribute("errorMessage", "回答は1問につき" + MAX_ANSWER_LENGTH + "文字以内で入力してください。");
        }
        if (model.containsAttribute("errorMessage")) {
            addQuestions(model, qaList);
            return VIEW;
        }
        deepDiveService.saveAnswers(answer, answers);
        // TODO: AIプラン生成中画面（P-11）ができたら、そちらへ進める（Claude）
        return "redirect:/hearing/deepdive?saved";
    }

    private void addQuestions(Model model, List<DeepDiveQa> qaList) {
        model.addAttribute("questions", qaList.stream().map(DeepDiveQa::question).toList());
        model.addAttribute("generating", qaList.isEmpty());
        model.addAttribute("maxAnswerLength", MAX_ANSWER_LENGTH);
    }
}
