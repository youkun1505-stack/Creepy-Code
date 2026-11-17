package com.creepycode.plan.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.creepycode.plan.entity.PriceMaster;

/**
 * 料金マスタ Repository（PriceMasterRepository）のインターフェース呼び出しテスト。
 */
class PriceMasterRepositoryTest {

    private PriceMasterRepository priceMasterRepository;

    @BeforeEach
    void setUp() {
        priceMasterRepository = mock(PriceMasterRepository.class);
    }

    @Test
    void 有効な全件取得_呼び出しができる() {
        PriceMaster item1 = new PriceMaster();
        item1.setItemId(1L);
        item1.setItemName("会場使用料");

        PriceMaster item2 = new PriceMaster();
        item2.setItemId(2L);
        item2.setItemName("料理（洋食コース）");

        when(priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc())
                .thenReturn(List.of(item1, item2));

        List<PriceMaster> list = priceMasterRepository.findAllByDeletedAtIsNullOrderByItemIdAsc();

        assertEquals(2, list.size());
        assertEquals("会場使用料", list.get(0).getItemName());
        verify(priceMasterRepository).findAllByDeletedAtIsNullOrderByItemIdAsc();
    }

    @Test
    void カテゴリ別取得_呼び出しができる() {
        PriceMaster item = new PriceMaster();
        item.setItemId(2L);
        item.setItemName("料理（洋食コース）");
        item.setCategory("料理");

        when(priceMasterRepository.findByCategoryAndDeletedAtIsNullOrderByItemIdAsc("料理"))
                .thenReturn(List.of(item));

        List<PriceMaster> list = priceMasterRepository.findByCategoryAndDeletedAtIsNullOrderByItemIdAsc("料理");

        assertEquals(1, list.size());
        assertEquals("料理", list.get(0).getCategory());
        verify(priceMasterRepository).findByCategoryAndDeletedAtIsNullOrderByItemIdAsc("料理");
    }

    @Test
    void ID検索_存在するときは値が取れる() {
        PriceMaster item = new PriceMaster();
        item.setItemId(1L);
        item.setItemName("会場使用料");

        when(priceMasterRepository.findByItemIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(item));

        Optional<PriceMaster> result = priceMasterRepository.findByItemIdAndDeletedAtIsNull(1L);

        assertTrue(result.isPresent());
        assertEquals("会場使用料", result.get().getItemName());
    }

    @Test
    void ID検索_存在しないときはemptyが返る() {
        when(priceMasterRepository.findByItemIdAndDeletedAtIsNull(999L))
                .thenReturn(Optional.empty());

        Optional<PriceMaster> result = priceMasterRepository.findByItemIdAndDeletedAtIsNull(999L);

        assertFalse(result.isPresent());
    }
}

