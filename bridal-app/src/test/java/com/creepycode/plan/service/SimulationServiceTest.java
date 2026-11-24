package com.creepycode.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.creepycode.plan.dto.SimulationItemDto;
import com.creepycode.plan.dto.SimulationResultDto;
import com.creepycode.plan.entity.PriceMaster;
import com.creepycode.plan.repository.PriceMasterRepository;

/**
 * 料金シミュレーション計算処理（SimulationService）の単体テスト。
 */
class SimulationServiceTest {

    private PriceMasterRepository priceMasterRepository;
    private SimulationService simulationService;

    // テスト用の料金マスタデータ
    private PriceMaster venue;   // 会場使用料：固定 300,000円
    private PriceMaster meal;    // 料理（洋食コース）：変動 15,000円/人
    private PriceMaster drink;   // ドリンク：変動 4,000円/人
    private PriceMaster dress;   // ドレス：固定 250,000円

    @BeforeEach
    void setUp() {
        priceMasterRepository = mock(PriceMasterRepository.class);
        simulationService = new SimulationService(priceMasterRepository);

        venue = createMaster(1L, "会場使用料", "会場", 300000, "N");
        meal = createMaster(2L, "料理（洋食コース）", "料理", 15000, "Y");
        drink = createMaster(3L, "ドリンク（フリードリンク）", "料理", 4000, "Y");
        dress = createMaster(4L, "ウェディングドレス", "衣装", 250000, "N");

        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc())
                .thenReturn(List.of(venue, meal, drink, dress));
    }

    @Test
    void 選択が空のとき_合計金額は0で予算残額は予算そのまま() {
        SimulationResultDto result = simulationService.calculate(3000000, Collections.emptyMap());

        assertEquals(0L, result.getTotalAmount());
        assertEquals(3000000, result.getBudget());
        assertEquals(3000000L, result.getRemainingBudget());
        assertFalse(result.isOverBudget());
        assertEquals(0L, result.getOverAmount());
        assertEquals(4, result.getItems().size());
    }

    @Test
    void 固定アイテムが選択されたとき_数量が1回分のみ加算される() {
        // 会場使用料（ID: 1）を選択
        Map<Long, Integer> selected = Map.of(1L, 1);

        SimulationResultDto result = simulationService.calculate(3000000, selected);

        assertEquals(300000L, result.getTotalAmount());
        assertEquals(2700000L, result.getRemainingBudget());
        assertFalse(result.isOverBudget());
    }

    @Test
    void 固定アイテムで数量に2以上を指定しても_固定のため1回分のみとして計算される() {
        // 会場使用料（固定）に誤って数量 5 を指定
        Map<Long, Integer> selected = Map.of(1L, 5);

        SimulationResultDto result = simulationService.calculate(3000000, selected);

        assertEquals(300000L, result.getTotalAmount());
    }

    @Test
    void 変動アイテムが選択されたとき_単価かける人数で小計が計算される() {
        // 料理（15,000円）× 80名
        Map<Long, Integer> selected = Map.of(2L, 80);

        SimulationResultDto result = simulationService.calculate(3000000, selected);

        // 15,000 * 80 = 1,200,000
        assertEquals(1200000L, result.getTotalAmount());
        assertEquals(1800000L, result.getRemainingBudget());
        assertFalse(result.isOverBudget());
    }

    @Test
    void 固定アイテムと変動アイテムが混在するとき_全体の合計金額が正しく計算される() {
        // 会場(300,000) + 料理(15,000 × 50名 = 750,000) + ドリンク(4,000 × 50名 = 200,000) + ドレス(250,000)
        // 合計: 300,000 + 750,000 + 200,000 + 250,000 = 1,500,000円
        Map<Long, Integer> selected = Map.of(
                1L, 1,
                2L, 50,
                3L, 50,
                4L, 1
        );

        SimulationResultDto result = simulationService.calculate(3000000, selected);

        assertEquals(1500000L, result.getTotalAmount());
        assertEquals(1500000L, result.getRemainingBudget());
        assertFalse(result.isOverBudget());
        assertEquals(0L, result.getOverAmount());
    }

    @Test
    void 合計金額が予算ちょうどのとき_予算残額は0でoverBudgetはfalse() {
        // 予算1,500,000円で合計1,500,000円
        Map<Long, Integer> selected = Map.of(
                1L, 1,
                2L, 50,
                3L, 50,
                4L, 1
        );

        SimulationResultDto result = simulationService.calculate(1500000, selected);

        assertEquals(1500000L, result.getTotalAmount());
        assertEquals(0L, result.getRemainingBudget());
        assertFalse(result.isOverBudget());
        assertEquals(0L, result.getOverAmount());
    }

    @Test
    void 合計金額が予算を超過したとき_残額がマイナスになりoverBudgetがtrueで超過額が算出される() {
        // 予算 1,000,000円 に対して 合計 1,500,000円（500,000円オーバー）
        Map<Long, Integer> selected = Map.of(
                1L, 1,
                2L, 50,
                3L, 50,
                4L, 1
        );

        SimulationResultDto result = simulationService.calculate(1000000, selected);

        assertEquals(1500000L, result.getTotalAmount());
        assertEquals(-500000L, result.getRemainingBudget());
        assertTrue(result.isOverBudget());
        assertEquals(500000L, result.getOverAmount());
    }

    @Test
    void 予算が未設定_null_のとき_エラーにならず合計金額のみ計算され残額はnullになる() {
        Map<Long, Integer> selected = Map.of(1L, 1);

        SimulationResultDto result = simulationService.calculate(null, selected);

        assertEquals(300000L, result.getTotalAmount());
        assertNull(result.getBudget());
        assertNull(result.getRemainingBudget());
        assertFalse(result.isOverBudget());
        assertEquals(0L, result.getOverAmount());
    }

    @Test
    void 負の数量が指定されたとき_0として安全に扱われる() {
        Map<Long, Integer> selected = Map.of(2L, -10);

        SimulationResultDto result = simulationService.calculate(3000000, selected);

        assertEquals(0L, result.getTotalAmount());
    }

    @Test
    void calculateSelectedOnly_選択されたアイテムのみがリストに含まれる() {
        Map<Long, Integer> selected = Map.of(
                1L, 1,
                2L, 50
        );

        SimulationResultDto result = simulationService.calculateSelectedOnly(3000000, selected);

        // 数量 > 0 の会場と料理のみが含まれる
        assertEquals(2, result.getItems().size());
        List<Long> itemIds = result.getItems().stream().map(SimulationItemDto::getItemId).toList();
        assertTrue(itemIds.contains(1L));
        assertTrue(itemIds.contains(2L));
        assertFalse(itemIds.contains(3L));
        assertFalse(itemIds.contains(4L));
        assertEquals(1050000L, result.getTotalAmount());
    }

    private PriceMaster createMaster(Long id, String name, String category, int basePrice, String isVariable) {
        PriceMaster master = new PriceMaster();
        master.setItemId(id);
        master.setItemName(name);
        master.setCategory(category);
        master.setBasePrice(basePrice);
        master.setIsVariable(isVariable);
        return master;
    }
}

