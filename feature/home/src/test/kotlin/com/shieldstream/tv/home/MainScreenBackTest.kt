package com.shieldstream.tv.home

import com.shieldstream.tv.ui.component.NavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenBackTest {

    @Test
    fun `kids profiles cannot select or restore the calendar or live tabs`() {
        val kidsTabs = availableNavTabs(kidsProfile = true)
        assertFalse(NavTab.CALENDAR in kidsTabs)
        assertFalse(NavTab.LIVE in kidsTabs)
        assertTrue(NavTab.DOCUMENTARIES in kidsTabs)
        assertTrue(NavTab.DOCUMENTARIES in availableNavTabs(kidsProfile = false))
        assertEquals(NavTab.entries, availableNavTabs(kidsProfile = false))
        assertEquals(NavTab.HOME, visibleNavTab(NavTab.CALENDAR, kidsProfile = true))
        assertEquals(NavTab.HOME, visibleNavTab(NavTab.LIVE, kidsProfile = true))
        assertEquals(NavTab.DOCUMENTARIES, visibleNavTab(NavTab.DOCUMENTARIES, kidsProfile = true))
        assertEquals(NavTab.DOCUMENTARIES, visibleNavTab(NavTab.DOCUMENTARIES, kidsProfile = false))
        assertEquals(NavTab.CALENDAR, visibleNavTab(NavTab.CALENDAR, kidsProfile = false))
        assertEquals(NavTab.LIVE, visibleNavTab(NavTab.LIVE, kidsProfile = false))
    }

    @Test
    fun `exit confirmation requires the top navigation bar to be focused`() {
        assertTrue(shouldRequestExit(categoryOpen = false, navFocused = true))
        assertFalse(shouldRequestExit(categoryOpen = false, navFocused = false))
        assertFalse(shouldRequestExit(categoryOpen = true, navFocused = true))
        assertFalse(shouldRequestExit(categoryOpen = true, navFocused = false))
    }
}
