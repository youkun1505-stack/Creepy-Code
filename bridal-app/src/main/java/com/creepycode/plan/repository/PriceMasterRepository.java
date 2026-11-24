package com.creepycode.plan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.plan.entity.PriceMaster;

/**
 * 料金マスタ（T-06 PRICE_MASTER）の DB アクセス。
 * 削除済み（DELETED_AT あり）のデータは原則として除外して検索する。
 */
public interface PriceMasterRepository extends JpaRepository<PriceMaster, Long> {

    /** 有効な料金マスタをすべて、アイテムID順で取得する（シミュレーション画面での一覧表示用）。 */
    List<PriceMaster> findAllByDeletedAtIsNullOrderByItemIdAsc();

    /** 指定カテゴリの有効な料金マスタを、アイテムID順で取得する。 */
    List<PriceMaster> findByCategoryAndDeletedAtIsNullOrderByItemIdAsc(String category);

    /** アイテムIDで有効な料金マスタを取得する。 */
    Optional<PriceMaster> findByItemIdAndDeletedAtIsNull(Long itemId);
}

