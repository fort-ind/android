package com.fortind.zenith.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackStacksTest {

    @Test
    fun everyTabStartsOnItsRoot() {
        val stacks = AppBackStacks()

        AppDestination.entries.forEach { assertEquals(listOf(it.root), stacks.stack(it).toList()) }
        assertEquals(AppDestination.HOME, stacks.tab)
        assertTrue(stacks.atHomeRoot)
    }

    @Test
    fun eachTabKeepsItsOwnStack() {
        val stacks = AppBackStacks()
        stacks.push(AppRoute.Thread("a"))
        stacks.select(AppDestination.NOTIFICATIONS)
        stacks.push(AppRoute.Thread("b"))

        stacks.select(AppDestination.HOME)

        assertEquals(AppRoute.Thread("a"), stacks.top)
        assertEquals(AppRoute.Thread("b"), stacks.stack(AppDestination.NOTIFICATIONS).last())
    }

    @Test
    fun pushingTheCurrentRouteAgainIsIgnored() {
        val stacks = AppBackStacks()
        stacks.push(AppRoute.Thread("a"))
        stacks.push(AppRoute.Thread("a"))

        assertEquals(2, stacks.current.size)
    }

    @Test
    fun reselectingATabPopsToItsRoot() {
        val stacks = AppBackStacks()
        stacks.push(AppRoute.Thread("a"))
        stacks.push(AppRoute.Thread("b"))

        stacks.select(AppDestination.HOME)

        assertEquals(listOf<AppRoute>(AppRoute.Home), stacks.current.toList())
    }

    @Test
    fun backPopsThenLeavesThroughHomeThenStops() {
        val stacks = AppBackStacks()
        stacks.select(AppDestination.PROFILE)
        stacks.push(AppRoute.Person("p"))

        assertTrue(stacks.back())
        assertEquals(AppRoute.Profile, stacks.top)
        assertTrue(stacks.back())
        assertEquals(AppDestination.HOME, stacks.tab)
        assertFalse(stacks.back())
    }

    @Test
    fun popNeverEmptiesAStackOrSwitchesTab() {
        val stacks = AppBackStacks()
        stacks.select(AppDestination.GAMES)

        assertFalse(stacks.pop())
        assertEquals(AppDestination.GAMES, stacks.tab)
        assertEquals(1, stacks.current.size)
    }

    @Test
    fun openThreadIdIsTheDeepestThreadInTheCurrentTab() {
        val stacks = AppBackStacks()
        assertNull(stacks.openThreadId)

        stacks.push(AppRoute.Thread("a"))
        stacks.push(AppRoute.Thread("b"))
        assertEquals("b", stacks.openThreadId)

        stacks.select(AppDestination.GAMES)
        assertNull(stacks.openThreadId)
    }

    @Test
    fun editProfileOpensSettingsAndHandlingClearsTheRequest() {
        val stacks = AppBackStacks()

        stacks.openEditProfile()
        assertEquals(AppDestination.SETTINGS, stacks.tab)
        assertEquals(AppRoute.Settings(openEditProfile = true), stacks.top)

        stacks.editProfileHandled()
        assertEquals(AppRoute.Settings(), stacks.top)
    }

    @Test
    fun savedStateRestoresEveryStackAndTheTab() {
        val stacks = AppBackStacks()
        stacks.push(AppRoute.Thread("a"))
        stacks.select(AppDestination.PROFILE)
        stacks.push(AppRoute.Person("p"))
        stacks.push(AppRoute.Thread("b"))

        val saved = with(AppBackStacks.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(stacks) }!!
        val restored = AppBackStacks.restore(saved)

        assertEquals(AppDestination.PROFILE, restored.tab)
        assertEquals(listOf(AppRoute.Profile, AppRoute.Person("p"), AppRoute.Thread("b")), restored.current.toList())
        assertEquals(listOf(AppRoute.Home, AppRoute.Thread("a")), restored.stack(AppDestination.HOME).toList())
    }

    @Test
    fun corruptSavedStateFallsBackToRoots() {
        val restored = AppBackStacks.restore(listOf(listOf("NOPE"), listOf("mystery", "thread:bad id"), emptyList()))

        assertEquals(AppDestination.HOME, restored.tab)
        assertEquals(listOf<AppRoute>(AppRoute.Home), restored.stack(AppDestination.HOME).toList())
    }

    @Test
    fun routesRoundTripAndRejectUnsafeNoteIds() {
        val routes = listOf(
            AppRoute.Home, AppRoute.Games, AppRoute.Notifications, AppRoute.Profile, AppRoute.Settings(),
            AppRoute.Settings(openEditProfile = true), AppRoute.Person("p1"), AppRoute.Thread("9abc"),
        )
        routes.forEach { assertEquals(it, AppRoute.decode(it.encode())) }

        assertNull(AppRoute.decode("thread:../../etc"))
        assertNull(AppRoute.decode("thread:"))
        assertFalse(AppRoute.isValidNoteId("a b"))
    }
}
