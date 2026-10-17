package com.example.widgetmemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PureLogicTest {
    @Test fun alphaKeepsColorAndScalesOpacity() {
        assertEquals(0x7F112233, withAlpha(0xFF112233.toInt(), 50))
        assertEquals(0xFF112233.toInt(), withAlpha(0xFF112233.toInt(), 100))
        assertEquals(0x00112233, withAlpha(0xFF112233.toInt(), 0))
    }

    @Test fun alphaIsClamped() {
        assertEquals(0xFF000000.toInt(), withAlpha(0xFF000000.toInt(), 500))
        assertEquals(0x00000000, withAlpha(0xFF000000.toInt(), -5))
    }

    @Test fun compactSizeRules() {
        assertTrue(isCompactSize(110, 110))
        assertTrue(isCompactSize(300, 90))
        assertFalse(isCompactSize(250, 250))
        assertFalse(isCompactSize(0, 0)) // 크기를 모를 때는 보통 레이아웃
    }
}
