package com.creepycode.plan.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.creepycode.auth.service.LoginUser;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.dto.PriceRevisionSummaryDto;
import com.creepycode.plan.dto.SimulationResultDto;
import com.creepycode.plan.entity.PriceMaster;
import com.creepycode.plan.repository.PriceMasterRepository;
import com.creepycode.plan.service.PriceRevisionService;
import com.creepycode.plan.service.SimulationService;

/**
 * リアルタイム料金シミュレーション画面（P-13）。
 * 顧客が式の内容を選ぶと、概算の予定金額や予算残額がリアルタイムに分かる。
 * また、料金マスタ改定時は旧価格・新価格・差額の提示と最新価格への更新機能を提供する。
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
    private final PriceRevisionService priceRevisionService;

    public SimulationController(SimulationService simulationService,
            PriceMasterRepository priceMasterRepository,
            OnboardingAnswerRepository onboardingAnswerRepository,
            PriceRevisionService priceRevisionService) {
        this.simulationService = simulationService;
        this.priceMasterRepository = priceMasterRepository;
        this.onboardingAnswerRepository = onboardingAnswerRepository;
        this.priceRevisionService = priceRevisionService;
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

        // 顧客の既存プランがあれば価格改定状況を取得
        PriceRevisionSummaryDto revisionSummary = null;
        if (loginUser != null) {
            Optional<PriceRevisionSummaryDto> optSummary = priceRevisionService.checkRevisionsForUser(loginUser.getUserId());
            if (optSummary.isPresent()) {
                revisionSummary = optSummary.get();
            }
        }

        model.addAttribute("budget", budget);
        model.addAttribute("categorizedItems", categorizedItems);
        model.addAttribute("initialResult", initialResult);
        model.addAttribute("revisionSummary", revisionSummary);

        return VIEW;
    }

    /** 未合意プランのオプション価格を最新の料金マスタ基本価格に一括更新する。 */
    @PostMapping("/price-revision/update")
    public String updateToLatestPrices(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam("planId") Long planId,
            RedirectAttributes redirectAttributes) {
        if (loginUser == null) {
            return "redirect:/auth/login";
        }

        try {
            priceRevisionService.updateToLatestPrices(planId, loginUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "プランのオプションを最新の価格に更新しました。");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "プランの更新に失敗しました。対象のプランが見つかりません。");
        }

        return "redirect:/plan/simulation";
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
