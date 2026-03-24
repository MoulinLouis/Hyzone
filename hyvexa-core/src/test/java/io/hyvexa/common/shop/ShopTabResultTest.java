package io.hyvexa.common.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShopTabResultTest {

    @Test
    void noneInstanceHasCorrectTypeAndNullKey() {
        assertEquals(ShopTabResult.Type.NONE, ShopTabResult.NONE.getType());
        assertNull(ShopTabResult.NONE.getConfirmKey());
    }

    @Test
    void refreshInstanceHasCorrectTypeAndNullKey() {
        assertEquals(ShopTabResult.Type.REFRESH, ShopTabResult.REFRESH.getType());
        assertNull(ShopTabResult.REFRESH.getConfirmKey());
    }

    @Test
    void showConfirmHasCorrectTypeAndKey() {
        ShopTabResult result = ShopTabResult.showConfirm("purchase_glow");
        assertEquals(ShopTabResult.Type.SHOW_CONFIRM, result.getType());
        assertEquals("purchase_glow", result.getConfirmKey());
    }

    @Test
    void hideConfirmHasCorrectTypeAndNullKey() {
        ShopTabResult result = ShopTabResult.hideConfirm();
        assertEquals(ShopTabResult.Type.HIDE_CONFIRM, result.getType());
        assertNull(result.getConfirmKey());
    }

    @Test
    void showConfirmWithNullKeyStillWorks() {
        ShopTabResult result = ShopTabResult.showConfirm(null);
        assertEquals(ShopTabResult.Type.SHOW_CONFIRM, result.getType());
        assertNull(result.getConfirmKey());
    }
}
