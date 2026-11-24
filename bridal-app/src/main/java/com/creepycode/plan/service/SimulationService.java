package com.creepycode.plan.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.plan.dto.SimulationItemDto;
import com.creepycode.plan.dto.SimulationResultDto;
import com.creepycode.plan.entity.PriceMaster;
import com.creepycode.plan.repository.PriceMasterRepository;

/**
 * リアルタイム料金シミュレーション（P-13）の計算処理。
 * 顧客が選択したオプションアイテムと数量から、合計予定金額（累計）や予算残額を計算する。
 */
@Service
public class SimulationService {

    private final PriceMasterRepository priceMasterRepository;

    public SimulationService(PriceMasterRepository priceMasterRepository) {
        this.priceMasterRepository = priceMasterRepository;
    }

    /**
     * 有効な全アイテムに対して、選択数量に応じた小計および全体の合計・予算残額を計算する。
     * 画面の一覧表示と同時に計算結果を返す場合に適している。
     *
     * @param budget 顧客の予算（円単位、未設定の場合は null）
     * @param selectedQuantities 各アイテムIDごとの選択数量（アイテムID -> 数量）
     * @return シミュレーション計算結果
     */
    @Transactional(readOnly = true)
    public SimulationResultDto calculate(Integer budget, Map<Long, Integer> selectedQuantities) {
        Map<Long, Integer> quantities = selectedQuantities != null ? selectedQuantities : Collections.emptyMap();
        List<PriceMaster> masterList = priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc();

        List<SimulationItemDto> itemDtos = new ArrayList<>();
        long totalAmount = 0L;

        for (PriceMaster master : masterList) {
            int quantity = resolveQuantity(master, quantities.get(master.getItemId()));
            SimulationItemDto itemDto = new SimulationItemDto(
                    master.getItemId(),
                    master.getItemName(),
                    master.getCategory(),
                    master.getBasePrice(),
                    quantity,
                    master.isVariable()
            );
            itemDtos.add(itemDto);
            totalAmount += itemDto.getSubtotal();
        }

        return new SimulationResultDto(itemDtos, totalAmount, budget);
    }

    /**
     * 選択された（数量が 1 以上の）アイテムのみを対象に合計および予算残額を計算する。
     *
     * @param budget 顧客の予算（円単位、未設定の場合は null）
     * @param selectedQuantities 各アイテムIDごとの選択数量（アイテムID -> 数量）
     * @return 選択アイテムのみを含んだシミュレーション計算結果
     */
    @Transactional(readOnly = true)
    public SimulationResultDto calculateSelectedOnly(Integer budget, Map<Long, Integer> selectedQuantities) {
        SimulationResultDto fullResult = calculate(budget, selectedQuantities);
        List<SimulationItemDto> selectedItems = fullResult.getItems().stream()
                .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
                .toList();

        return new SimulationResultDto(selectedItems, fullResult.getTotalAmount(), budget);
    }

    /**
     * アイテムの特性（固定／変動）に応じた実効数量を算出する。
     * - 数量が未指定（null）または負の数の場合は 0 とする。
     * - 固定アイテム（変動なし）の場合、1 以上の数量は 1 として扱う（多重選択不可の基本オプション）。
     */
    private int resolveQuantity(PriceMaster master, Integer specifiedQuantity) {
        if (specifiedQuantity == null || specifiedQuantity <= 0) {
            return 0;
        }
        if (!master.isVariable()) {
            return 1;
        }
        return specifiedQuantity;
    }
}

