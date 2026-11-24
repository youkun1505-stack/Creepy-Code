package com.creepycode.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.creepycode.plan.dto.PriceRevisionSummaryDto;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanOption;
import com.creepycode.plan.entity.PriceMaster;
import com.creepycode.plan.repository.PlanOptionRepository;
import com.creepycode.plan.repository.PlanRepository;
import com.creepycode.plan.repository.PriceMasterRepository;

/**
 * 価格改定サービス（PriceRevisionService）の単体テスト。
 * 改定の検出、合意済みプランの保護、最新価格更新を検証する。
 */
@ExtendWith(MockitoExtension.class)
class PriceRevisionServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlanOptionRepository planOptionRepository;

    @Mock
    private PriceMasterRepository priceMasterRepository;

    private PriceRevisionService priceRevisionService;

    @BeforeEach
    void setUp() {
        priceRevisionService = new PriceRevisionService(planRepository, planOptionRepository, priceMasterRepository);
    }

    @Test
    @DisplayName("未合意プランで料金マスタが変更されている場合、改定あり・差額が正しく検出されること")
    void checkRevisions_whenUnagreedPlanHasRevisedItems_detectsRevisionsCorrectly() {
        // プラン準備（未合意）
        Plan plan = new Plan();
        plan.setPlanId(10L);
        plan.setUserId(1L);
        plan.setStatus(Plan.STATUS_CUSTOMER_DRAFT_REVIEW);
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));

        // オプション準備（旧単価: チャペル 180,000円, コース 12,000円）
        PlanOption opt1 = new PlanOption(10L, 1L, 1, 180000, false);
        opt1.setOptionId(101L);
        PlanOption opt2 = new PlanOption(10L, 2L, 50, 12000, true);
        opt2.setOptionId(102L);
        when(planOptionRepository.findAllByPlanIdOrderByOptionIdAsc(10L)).thenReturn(List.of(opt1, opt2));

        // 料金マスタ準備（新単価: チャペル 200,000円（+20,000）, コース 12,000円（変更なし））
        PriceMaster pm1 = new PriceMaster();
        pm1.setItemId(1L);
        pm1.setItemName("チャペル挙式");
        pm1.setCategory("会場");
        pm1.setBasePrice(200000);
        pm1.setIsVariable("N");

        PriceMaster pm2 = new PriceMaster();
        pm2.setItemId(2L);
        pm2.setItemName("フレンチコース");
        pm2.setCategory("料理");
        pm2.setBasePrice(12000);
        pm2.setIsVariable("Y");

        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc()).thenReturn(List.of(pm1, pm2));

        PriceRevisionSummaryDto result = priceRevisionService.checkRevisions(10L);

        assertTrue(result.isHasRevisions(), "改定ありと判定されること");
        assertEquals(1, result.getRevisedItemCount(), "改定対象アイテムは1件であること");
        assertEquals(180000L + (12000L * 50), result.getTotalOldAmount(), "旧合計金額が正しいこと");
        assertEquals(200000L + (12000L * 50), result.getTotalNewAmount(), "新合計金額が正しいこと");
        assertEquals(20000L, result.getTotalDiffAmount(), "差額が+20,000円であること");
    }

    @Test
    @DisplayName("未合意プランで料金マスタが変更されていない場合、hasRevisionsがfalseであること")
    void checkRevisions_whenNoItemsRevised_hasRevisionsIsFalse() {
        Plan plan = new Plan();
        plan.setPlanId(10L);
        plan.setUserId(1L);
        plan.setStatus(Plan.STATUS_CUSTOMER_DRAFT_REVIEW);
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));

        PlanOption opt1 = new PlanOption(10L, 1L, 1, 200000, false);
        opt1.setOptionId(101L);
        when(planOptionRepository.findAllByPlanIdOrderByOptionIdAsc(10L)).thenReturn(List.of(opt1));

        PriceMaster pm1 = new PriceMaster();
        pm1.setItemId(1L);
        pm1.setItemName("チャペル挙式");
        pm1.setCategory("会場");
        pm1.setBasePrice(200000); // 変更なし
        pm1.setIsVariable("N");

        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc()).thenReturn(List.of(pm1));

        PriceRevisionSummaryDto result = priceRevisionService.checkRevisions(10L);

        assertFalse(result.isHasRevisions(), "改定なしと判定されること");
        assertEquals(0, result.getRevisedItemCount());
        assertEquals(0L, result.getTotalDiffAmount());
    }

    @Test
    @DisplayName("合意済みプランの場合、マスタ価格が異なっていても改定なし（金額凍結）として扱われること")
    void checkRevisions_whenPlanIsAgreed_revisionsAreIgnored() {
        Plan plan = new Plan();
        plan.setPlanId(10L);
        plan.setUserId(1L);
        plan.setStatus(Plan.STATUS_AGREED);
        plan.setAgreedAt(LocalDateTime.now().minusDays(1));
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));

        PlanOption opt1 = new PlanOption(10L, 1L, 1, 150000, false); // 旧単価
        opt1.setOptionId(101L);
        when(planOptionRepository.findAllByPlanIdOrderByOptionIdAsc(10L)).thenReturn(List.of(opt1));

        PriceMaster pm1 = new PriceMaster();
        pm1.setItemId(1L);
        pm1.setItemName("チャペル挙式");
        pm1.setCategory("会場");
        pm1.setBasePrice(200000); // 新単価（改定されている）
        pm1.setIsVariable("N");

        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc()).thenReturn(List.of(pm1));

        PriceRevisionSummaryDto result = priceRevisionService.checkRevisions(10L);

        assertTrue(result.isAgreed(), "合意済みであること");
        assertFalse(result.isHasRevisions(), "合意済みのため価格改定対象外（false）となること");
        assertEquals(0L, result.getTotalDiffAmount(), "差額は0であること");
    }

    @Test
    @DisplayName("未合意プランの最新価格更新：オプション単価が最新の基本価格で上書き保存されること")
    void updateToLatestPrices_whenUnagreedPlan_updatesOptionUnitPrices() {
        Plan plan = new Plan();
        plan.setPlanId(10L);
        plan.setUserId(1L);
        plan.setStatus(Plan.STATUS_CUSTOMER_DRAFT_REVIEW);
        plan.setUpdatedAt(LocalDateTime.now().minusDays(2));
        when(planRepository.findByPlanIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        PlanOption opt1 = new PlanOption(10L, 1L, 1, 180000, false);
        opt1.setOptionId(101L);
        when(planOptionRepository.findAllByPlanIdOrderByOptionIdAsc(10L)).thenReturn(List.of(opt1));

        PriceMaster pm1 = new PriceMaster();
        pm1.setItemId(1L);
        pm1.setItemName("チャペル挙式");
        pm1.setCategory("会場");
        pm1.setBasePrice(200000);
        pm1.setIsVariable("N");
        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc()).thenReturn(List.of(pm1));

        PriceRevisionSummaryDto updated = priceRevisionService.updateToLatestPrices(10L, 1L);

        assertEquals(200000, opt1.getUnitPrice(), "オプション単価が新価格200,000円に更新されていること");
        verify(planOptionRepository).saveAll(anyList());
        verify(planRepository).save(plan);
        assertFalse(updated.isHasRevisions(), "更新後は改定差分がなくなること");
    }

    @Test
    @DisplayName("合意済みプランの最新価格更新：例外がスローされ更新されないこと")
    void updateToLatestPrices_whenPlanIsAgreed_throwsIllegalStateException() {
        Plan plan = new Plan();
        plan.setPlanId(10L);
        plan.setUserId(1L);
        plan.setStatus(Plan.STATUS_AGREED);
        plan.setAgreedAt(LocalDateTime.now());
        when(planRepository.findByPlanIdAndUserId(10L, 1L)).thenReturn(Optional.of(plan));

        assertThrows(IllegalStateException.class, () -> {
            priceRevisionService.updateToLatestPrices(10L, 1L);
        });

        verify(planOptionRepository, never()).saveAll(anyList());
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("存在しないプランまたは権限外プランの場合、例外がスローされること")
    void updateToLatestPrices_whenNotFound_throwsIllegalArgumentException() {
        when(planRepository.findByPlanIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            priceRevisionService.updateToLatestPrices(999L, 1L);
        });
    }
}
