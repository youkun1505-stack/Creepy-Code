package com.creepycode.plan.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.plan.service.PlanGenerationResult;
import com.creepycode.plan.service.PlanGenerationService;

import jakarta.servlet.http.HttpSession;

/**
 * AIプラン生成中の画面（P-11）。画面を開くと、JavaScript（plan.js）が生成を頼み、終わったらマイプラン（P-12）へ進む。
 * /plan/** はプランナーも使う画面（料金シミュレーションなど）があるので、顧客かどうかはここで確かめる。
 */
@Controller
@RequestMapping("/plan/generating")
public class PlanGenerationController {

    private static final String VIEW = "plan/generating";
    private static final String URL_TOP = "/";
    private static final String URL_MY_PLAN = "/plan/myplan";
    // 生成の失敗が続いた回数を、セッションに覚えておくときの名前
    static final String FAILURE_COUNT = "planGenerationFailureCount";
    // 基本設計書 6-5：3回連続で失敗したら、プランナーへの手動作成の依頼を案内する
    static final int MAX_FAILURES = 3;

    private final PlanGenerationService generationService;

    public PlanGenerationController(PlanGenerationService generationService) {
        this.generationService = generationService;
    }

    /** 生成中の画面を表示する。顧客でない人、STEP4 まで答えていない顧客はトップ（ハブ）へ戻す。 */
    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser.getRole() != UserRole.CUSTOMER || !generationService.isReady(loginUser.getUserId())) {
            return "redirect:" + URL_TOP;
        }
        return VIEW;
    }

    /**
     * プランを作って、結果を JSON で返す（画面の JavaScript が呼ぶ）。
     * - できた・もうできていた：{status: "done", next: マイプランの URL}（失敗の回数を消す）
     * - 別の呼び出しが作っている最中：{status: "running"}（少し待ってから、もう一度呼んでもらう。失敗には数えない）
     * - 失敗した：{status: "failed", giveUp: 3回続けて失敗したら true}
     * - 作れない（STEP4 まで答えていない）：{status: "redirect", next: トップの URL}
     * - プランナーに送った後：{status: "redirect", next: マイプランの URL}
     * 顧客でない人は 403。
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generate(@AuthenticationPrincipal LoginUser loginUser,
            HttpSession session) {
        if (loginUser.getRole() != UserRole.CUSTOMER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        PlanGenerationResult result = generationService.generate(loginUser.getUserId());
        return ResponseEntity.ok(switch (result) {
            case GENERATED, ALREADY_GENERATED -> {
                session.removeAttribute(FAILURE_COUNT);
                yield Map.of("status", "done", "next", URL_MY_PLAN);
            }
            case IN_PROGRESS -> Map.of("status", "running");
            case FAILED -> {
                Integer previous = (Integer) session.getAttribute(FAILURE_COUNT);
                int failures = previous == null ? 1 : previous + 1;
                session.setAttribute(FAILURE_COUNT, failures);
                yield Map.of("status", "failed", "giveUp", failures >= MAX_FAILURES);
            }
            case NOT_READY -> Map.of("status", "redirect", "next", URL_TOP);
            case SENT_TO_PLANNER -> Map.of("status", "redirect", "next", URL_MY_PLAN);
        });
    }
}
