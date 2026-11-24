package com.creepycode.plan.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.plan.dto.PriceRevisionItemDto;
import com.creepycode.plan.dto.PriceRevisionSummaryDto;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanOption;
import com.creepycode.plan.entity.PriceMaster;
import com.creepycode.plan.repository.PlanOptionRepository;
import com.creepycode.plan.repository.PlanRepository;
import com.creepycode.plan.repository.PriceMasterRepository;

/**
 * 価格改定の検出および最新価格への更新処理（P-13）。
 * 料金マスタ改定時、未合意プランのオプション価格差分を検出し、最新価格への一括更新を提供する。
 * 基本設計書：合意済みプランは改定の影響を受けない。
 */
@Service
public class PriceRevisionService {

    private final PlanRepository planRepository;
    private final PlanOptionRepository planOptionRepository;
    private final PriceMasterRepository priceMasterRepository;

    public PriceRevisionService(PlanRepository planRepository,
            PlanOptionRepository planOptionRepository,
            PriceMasterRepository priceMasterRepository) {
        this.planRepository = planRepository;
        this.planOptionRepository = planOptionRepository;
        this.priceMasterRepository = priceMasterRepository;
    }

    /**
     * 指定プランの価格改定状況を判定する。
     * 各オプションの UNIT_PRICE と料金マスタの BASE_PRICE を突き合わせる。
     */
    @Transactional(readOnly = true)
    public PriceRevisionSummaryDto checkRevisions(Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("指定されたプランが存在しません。planId: " + planId));

        return buildRevisionSummary(plan);
    }

    /**
     * 顧客の最新プランの価格改定状況を取得する。プランが存在しない場合は空のサマリーを返す。
     */
    @Transactional(readOnly = true)
    public Optional<PriceRevisionSummaryDto> checkRevisionsForUser(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return planRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(this::buildRevisionSummary);
    }

    /**
     * 未合意プランのオプション価格を、最新の料金マスタ基本価格に一括更新する。
     * 合意済みの場合は改定不可（例外をスロー）。
     */
    @Transactional
    public PriceRevisionSummaryDto updateToLatestPrices(Long planId, Long userId) {
        Plan plan = planRepository.findByPlanIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("対象のプランが存在しないか、操作権限がありません。planId: " + planId));

        if (plan.isAgreed()) {
            throw new IllegalStateException("合意済みのプランは価格改定できません（金額は凍結されています）。");
        }

        List<PlanOption> options = planOptionRepository.findAllByPlanIdOrderByOptionIdAsc(planId);
        if (options.isEmpty()) {
            return buildRevisionSummary(plan);
        }

        // 有効な料金マスタを取得
        Map<Long, PriceMaster> masterMap = priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc().stream()
                .collect(Collectors.toMap(PriceMaster::getItemId, Function.identity()));

        // 各オプションの UNIT_PRICE を最新の BASE_PRICE で更新
        for (PlanOption option : options) {
            PriceMaster master = masterMap.get(option.getItemId());
            if (master != null) {
                option.setUnitPrice(master.getBasePrice());
            }
        }
        planOptionRepository.saveAll(options);

        // プランの更新日時を記録
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);

        return buildRevisionSummary(plan);
    }

    /** プランとオプションから価格改定サマリーを生成するヘルパー。 */
    private PriceRevisionSummaryDto buildRevisionSummary(Plan plan) {
        List<PlanOption> options = planOptionRepository.findAllByPlanIdOrderByOptionIdAsc(plan.getPlanId());

        // 料金マスタの全アイテム（論理削除除外）を取得
        Map<Long, PriceMaster> masterMap = priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc().stream()
                .collect(Collectors.toMap(PriceMaster::getItemId, Function.identity()));

        List<PriceRevisionItemDto> itemDtos = new ArrayList<>();
        for (PlanOption option : options) {
            PriceMaster master = masterMap.get(option.getItemId());
            String itemName = master != null ? master.getItemName() : "（不明なオプション）";
            String category = master != null ? master.getCategory() : "その他";
            int newUnitPrice = master != null ? master.getBasePrice() : option.getUnitPrice();

            PriceRevisionItemDto dto = new PriceRevisionItemDto(
                    option.getOptionId(),
                    option.getItemId(),
                    itemName,
                    category,
                    option.getQuantity(),
                    option.getUnitPrice(),
                    newUnitPrice,
                    option.isVariable()
            );
            itemDtos.add(dto);
        }

        return new PriceRevisionSummaryDto(plan.getPlanId(), plan.isAgreed(), itemDtos);
    }
}
