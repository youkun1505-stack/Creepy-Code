package com.creepycode.hearing.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.DeepDiveForm;
import com.creepycode.hearing.dto.DeepDiveQa;
import com.creepycode.hearing.entity.OnboardingAnswer;
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

    private final DeepDiveService deepDiveService;

    public DeepDiveController(DeepDiveService deepDiveService) {
        this.deepDiveService = deepDiveService;
    }

    /** 画面を表示する。質問がまだ無いときは「生成中」の表示になり、画面側から質問の生成を呼び出す。 */
    @GetMapping
    public String show(Principal principal, Model model) {
        OnboardingAnswer answer = deepDiveService.findOrCreateAnswer(principal.getName());
        List<DeepDiveQa> qaList = deepDiveService.readQaList(answer);
        DeepDiveForm form = new DeepDiveForm();
        form.setAnswers(qaList.stream().map(qa -> qa.answer() == null ? "" : qa.answer()).toList());
        model.addAttribute("deepDiveForm", form);
        addQuestions(model, qaList);
        return VIEW;
    }

    /** AI に質問を作らせる（画面の「生成中」表示から呼ばれる）。結果は JSON で返す。 */
    @PostMapping("/questions")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generate(Principal principal, HttpSession session) {
        OnboardingAnswer answer = deepDiveService.findOrCreateAnswer(principal.getName());
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
    public String submit(Principal principal, @ModelAttribute DeepDiveForm deepDiveForm, Model model) {
        OnboardingAnswer answer = deepDiveService.findOrCreateAnswer(principal.getName());
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
