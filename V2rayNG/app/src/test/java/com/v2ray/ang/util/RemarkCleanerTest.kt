package com.v2ray.ang.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemarkCleanerTest {
    @Test
    fun removesTrafficAndTime() {
        assertEquals("STARS-⭐-613", RemarkCleaner.clean("STARS-⭐-613-6.70GB📊-26D,1H⏳"))
    }

    @Test
    fun keepsPlainNames() {
        assertEquals("624-⭐", RemarkCleaner.clean("624-⭐"))
        assertEquals("5G-Server", RemarkCleaner.clean("5G-Server"))
        assertEquals("HK2D", RemarkCleaner.clean("HK2D"))
    }

    @Test
    fun removesPanelStatusMarker() {
        assertEquals("STARS-⭐-612", RemarkCleaner.clean("⛔N/A-STARS-⭐-612"))
        assertEquals("STARS-⭐-612", RemarkCleaner.clean("🚫 N/A STARS-⭐-612"))
        assertEquals("Nalu", RemarkCleaner.clean("Nalu"))
    }

    @Test
    fun neverReturnsAnEmptyName() {
        assertEquals("6GB", RemarkCleaner.clean("6GB"))
    }
}
