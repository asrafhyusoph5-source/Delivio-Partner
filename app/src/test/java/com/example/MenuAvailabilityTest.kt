package com.example

import com.example.data.model.MenuItem
import com.example.data.model.MenuSchedule
import com.example.data.model.ShopMenu
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MenuAvailabilityTest {

    private val timeZoneString = "Asia/Manila"
    private val zoneId = ZoneId.of(timeZoneString)

    @Test
    fun testAlwaysAvailableMenu() {
        val menu = ShopMenu(
            id = "main",
            name = "Main Menu",
            active = true,
            schedule = null
        )
        // With schedule = null, always available when active
        assertTrue(menu.isAvailableNow(System.currentTimeMillis(), timeZoneString))

        // Inactive menu should not be available
        val inactiveMenu = menu.copy(active = false)
        assertFalse(inactiveMenu.isAvailableNow(System.currentTimeMillis(), timeZoneString))
    }

    @Test
    fun testSameDaySchedule() {
        // Schedule Monday 08:00 to 12:00
        val schedule = MenuSchedule(
            days = listOf("mon"),
            start = "08:00",
            end = "12:00"
        )
        val menu = ShopMenu(
            id = "breakfast",
            name = "Breakfast Menu",
            active = true,
            schedule = schedule
        )

        // Monday at 10:00 AM -> Available
        val mon10am = ZonedDateTime.of(2026, 11, 2, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertTrue(menu.isAvailableNow(mon10am, timeZoneString))

        // Monday at 13:00 PM -> Closed
        val mon1pm = ZonedDateTime.of(2026, 11, 2, 13, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertFalse(menu.isAvailableNow(mon1pm, timeZoneString))

        // Tuesday at 10:00 AM -> Closed
        val tue10am = ZonedDateTime.of(2026, 11, 3, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertFalse(menu.isAvailableNow(tue10am, timeZoneString))
    }

    @Test
    fun testOvernightSchedule() {
        // Schedule Friday 20:00 to 04:00 (next day Saturday morning)
        val schedule = MenuSchedule(
            days = listOf("fri"),
            start = "20:00",
            end = "04:00"
        )
        val menu = ShopMenu(
            id = "late_night",
            name = "Late Night Bites",
            active = true,
            schedule = schedule
        )

        // Friday 22:00 -> Available
        val fri10pm = ZonedDateTime.of(2026, 11, 6, 22, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertTrue(menu.isAvailableNow(fri10pm, timeZoneString))

        // Saturday 02:00 AM -> Available (started Friday night)
        val sat2am = ZonedDateTime.of(2026, 11, 7, 2, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertTrue(menu.isAvailableNow(sat2am, timeZoneString))

        // Saturday 05:00 AM -> Closed (ended at 04:00 AM)
        val sat5am = ZonedDateTime.of(2026, 11, 7, 5, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertFalse(menu.isAvailableNow(sat5am, timeZoneString))
    }

    @Test
    fun testMenuItemAvailability() {
        val alwaysMenu = ShopMenu(id = "main", name = "Main", active = true, schedule = null)
        val closedMenu = ShopMenu(id = "secret", name = "Secret", active = false, schedule = null)

        val itemInAlways = MenuItem(
            id = "item_1",
            name = "Burger",
            inStock = true,
            menuIds = listOf("main")
        )
        assertTrue(itemInAlways.isAvailableNow(listOf(alwaysMenu, closedMenu), System.currentTimeMillis(), timeZoneString))

        val soldOutItem = itemInAlways.copy(inStock = false)
        assertFalse(soldOutItem.isAvailableNow(listOf(alwaysMenu, closedMenu), System.currentTimeMillis(), timeZoneString))

        val itemInClosed = itemInAlways.copy(menuIds = listOf("secret"))
        assertFalse(itemInClosed.isAvailableNow(listOf(alwaysMenu, closedMenu), System.currentTimeMillis(), timeZoneString))
    }
}
