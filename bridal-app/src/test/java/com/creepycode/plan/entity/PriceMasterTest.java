package com.creepycode.plan.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * 料金マスタ Entity（PriceMaster）の単体テスト。
 */
class PriceMasterTest {

    @Test
    void 変動区分の判定_Yのときはtrueを返す() {
        PriceMaster item = new PriceMaster();
        item.setIsVariable("Y");

        assertTrue(item.isVariable());
    }

    @Test
    void 変動区分の判定_小文字yのときもtrueを返す() {
        PriceMaster item = new PriceMaster();
        item.setIsVariable("y");

        assertTrue(item.isVariable());
    }

    @Test
    void 変動区分の判定_Nのときはfalseを返す() {
        PriceMaster item = new PriceMaster();
        item.setIsVariable("N");

        assertFalse(item.isVariable());
    }

    @Test
    void 変動区分の設定_booleanでYとNを設定できる() {
        PriceMaster item = new PriceMaster();

        item.setVariable(true);
        assertEquals("Y", item.getIsVariable());
        assertTrue(item.isVariable());

        item.setVariable(false);
        assertEquals("N", item.getIsVariable());
        assertFalse(item.isVariable());
    }

    @Test
    void 論理削除の判定_deletedAtがnullのときは未削除と判定する() {
        PriceMaster item = new PriceMaster();
        item.setDeletedAt(null);

        assertFalse(item.isDeleted());
    }

    @Test
    void 論理削除の判定_deletedAtがあるときは削除済みと判定する() {
        PriceMaster item = new PriceMaster();
        item.setDeletedAt(LocalDateTime.now());

        assertTrue(item.isDeleted());
    }

    @Test
    void 全プロパティのgetterとsetterが正しく機能する() {
        PriceMaster item = new PriceMaster();
        LocalDateTime now = LocalDateTime.now();

        item.setItemId(1L);
        item.setItemName("会場使用料");
        item.setCategory("会場");
        item.setBasePrice(300000);
        item.setIsVariable("N");
        item.setDescription("挙式・披露宴会場の使用料");
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setDeletedAt(null);

        assertEquals(1L, item.getItemId());
        assertEquals("会場使用料", item.getItemName());
        assertEquals("会場", item.getCategory());
        assertEquals(300000, item.getBasePrice());
        assertEquals("N", item.getIsVariable());
        assertEquals("挙式・披露宴会場の使用料", item.getDescription());
        assertEquals(now, item.getCreatedAt());
        assertEquals(now, item.getUpdatedAt());
        assertNull(item.getDeletedAt());
    }
}

