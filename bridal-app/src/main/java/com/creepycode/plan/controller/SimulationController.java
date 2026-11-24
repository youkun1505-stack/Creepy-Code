package com.creepycode.plan.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.creepycode.auth.service.LoginUser;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.dto.SimulationResultDto;
import com.creepycode.plan.entity.PriceMaster;
import com.creepycode.plan.repository.PriceMasterRepository;
import com.creepycode.plan.service.SimulationService;

/**
 * リアルタイム料金シミュレーション画面（P-13）。
 * 顧客が式の内容を選ぶと、概算の予定金額や予算残額がリアルタイムに分かる。
 */
@Controller
@RequestMapping("/plan/simulation")
public class SimulationController {

    private static final String VIEW = "plan/simulation";

    // 予算が未設定のときの初期値（基本設計書：一般的な初期予算 3,000,000 円）
    private static final int DEFAULT_BUDGET = 3000000;

    private final SimulationService simulationService;
    private final PriceMasterRepository priceMasterRepository;
    private final OnboardingAnswerRepository onboardingAnswerRepository;

    public SimulationController(SimulationService simulationService,
            PriceMasterRepository priceMasterRepository,
            OnboardingAnswerRepository onboardingAnswerRepository) {
        this.simulationService = simulationService;
        this.priceMasterRepository = priceMasterRepository;
        this.onboardingAnswerRepository = onboardingAnswerRepository;
    }

    /** 料金シミュレーション画面を表示する。 */
    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        // 顧客の事前ヒアリング回答から予算を取得する（未回答または予算未設定ならデフォルト値）
        Integer budget = resolveBudget(loginUser);

        // 有効な料金マスタを全件取得
        List<PriceMaster> masterList = priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc();

        // 画面で見やすいようにカテゴリ別（「会場」「料理」「衣装」「演出」など）にグループ化
        Map<String, List<PriceMaster>> categorizedItems = masterList.stream()
                .collect(Collectors.groupingBy(
                        PriceMaster::getCategory,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 初期シミュレーション結果（選択数 0 の状態）
        SimulationResultDto initialResult = simulationService.calculate(budget, Collections.emptyMap());

        model.addAttribute("budget", budget);
        model.addAttribute("categorizedItems", categorizedItems);
        model.addAttribute("initialResult", initialResult);

        return VIEW;
    }

    /** 顧客の予算を取得する。事前ヒアリング回答があればその予算、なければ初期値を返す。 */
    private Integer resolveBudget(LoginUser loginUser) {
        if (loginUser == null) {
            return DEFAULT_BUDGET;
        }
        return onboardingAnswerRepository.findFirstByUserIdOrderByCreatedAtDesc(loginUser.getUserId())
                .map(OnboardingAnswer::getBudget)
                .filter(b -> b != null && b > 0)
                .orElse(DEFAULT_BUDGET);
    }
}
