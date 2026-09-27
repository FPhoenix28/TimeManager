package com.example.timemanager.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchFieldTest {
    @Test
    fun blankQueryMatchesEveryApp() {
        assertTrue(matchesAppSearch("微信", "com.tencent.mm", "  "))
    }

    @Test
    fun chineseNameAndPackageNameAreSearchable() {
        assertTrue(matchesAppSearch("微信", "com.tencent.mm", " 微信 "))
        assertTrue(matchesAppSearch("微信", "com.tencent.mm", "com.tencent.mm"))
    }

    @Test
    fun englishNameMatchingIgnoresCase() {
        assertTrue(matchesAppSearch("Bilibili", "tv.danmaku.bili", "bili"))
    }

    @Test
    fun unrelatedQueryDoesNotMatch() {
        assertFalse(matchesAppSearch("微信", "com.tencent.mm", "bili"))
    }
}
