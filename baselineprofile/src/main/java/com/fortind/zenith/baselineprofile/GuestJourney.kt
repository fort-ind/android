package com.fortind.zenith.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.fortind.zenith"

private const val BROWSE_AS_GUEST = "Continue w/o an account"
private const val UI_TIMEOUT_MS = 5_000L
private const val FEED_TIMEOUT_MS = 15_000L
private const val STALE_ATTEMPTS = 3
private const val FEED_LOOKS = 3
private const val NOTE = "note"
private const val FEED_LIST = "feed:list"
private const val SEARCH_FIELD = "search:field"
private const val NOTE_AVATAR = "note:avatar"
private const val NOTE_TIME = "note:time"
private const val PERSON_MEDIA_TAB = "person:tab:Media"
private val DESTINATIONS = listOf("nav:GAMES", "nav:NOTIFICATIONS", "nav:PROFILE", "nav:SETTINGS", "nav:HOME")
private val PAGE_BACK = By.pkg(PACKAGE_NAME).desc("Back")
private val POPULAR_HASHTAGS = By.pkg(PACKAGE_NAME).text("Popular hashtags")
private val HASHTAG_ROW = By.pkg(PACKAGE_NAME).textStartsWith("#")

fun MacrobenchmarkScope.enterAsGuest() {
    clickRequired(By.text(BROWSE_AS_GUEST))
}

fun MacrobenchmarkScope.awaitFeed() {
    check(device.wait(Until.hasObject(By.res(FEED_LIST)), UI_TIMEOUT_MS)) { "Feed did not appear" }
    device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)
    device.waitForIdle()
}

fun MacrobenchmarkScope.browseAsGuest() {
    enterAsGuest()
    DESTINATIONS.forEach { tag -> clickRequired(By.res(tag)) }
}

fun MacrobenchmarkScope.browseFeed() {
    if (!device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)) return
    flingFeed(times = 3)
    revealTopBar()
    clickRequired(By.res("timeline:Global"))
    if (device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)) flingFeed(times = 2)
    revealTopBar()
    clickRequired(By.res("timeline:Local"))
    browseThread()
    browsePerson()
    browseDiscovery()
}

private fun MacrobenchmarkScope.browseThread() {
    if (openFromFeed(By.res(NOTE_TIME))) leavePage()
}

private fun MacrobenchmarkScope.browsePerson() {
    if (!openFromFeed(By.res(NOTE_AVATAR))) return
    tap(By.res(PERSON_MEDIA_TAB), FEED_TIMEOUT_MS)
    leavePage()
}

private fun MacrobenchmarkScope.browseDiscovery() {
    revealTopBar()
    clickRequired(By.res(SEARCH_FIELD))
    check(device.wait(Until.hasObject(POPULAR_HASHTAGS), UI_TIMEOUT_MS)) { "Search did not open" }
    if (tap(HASHTAG_ROW, FEED_TIMEOUT_MS)) {
        check(device.wait(Until.gone(POPULAR_HASHTAGS), FEED_TIMEOUT_MS)) { "Hashtag page did not open" }
        device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)
        device.waitForIdle()
    }
    goBack()
}

private fun MacrobenchmarkScope.openFromFeed(entry: BySelector): Boolean {
    repeat(FEED_LOOKS) {
        if (tap(entry, UI_TIMEOUT_MS)) {
            check(device.wait(Until.hasObject(PAGE_BACK), FEED_TIMEOUT_MS)) { "$entry did not open a page" }
            return true
        }
        gestureOnFeed { it.scroll(Direction.DOWN, 0.5f) }
    }
    return false
}

private fun MacrobenchmarkScope.leavePage() {
    goBack()
    check(device.wait(Until.gone(PAGE_BACK), FEED_TIMEOUT_MS)) { "Back did not close the page" }
}

private fun MacrobenchmarkScope.clickRequired(selector: BySelector) {
    check(tap(selector, UI_TIMEOUT_MS)) { "$selector did not appear" }
}

private fun MacrobenchmarkScope.tap(selector: BySelector, timeoutMs: Long): Boolean = retryingStale {
    val target = device.wait(Until.findObject(selector), timeoutMs)
    target?.click()
    target != null
}

private fun MacrobenchmarkScope.flingFeed(times: Int) {
    repeat(times) { gestureOnFeed { it.fling(Direction.DOWN) } }
}

private fun MacrobenchmarkScope.revealTopBar() {
    gestureOnFeed { it.scroll(Direction.UP, 0.5f) }
}

private fun MacrobenchmarkScope.gestureOnFeed(gesture: (UiObject2) -> Unit) {
    retryingStale {
        val list = device.findObject(By.res(FEED_LIST))
        list?.setGestureMargin(device.displayWidth / 5)
        list?.let(gesture)
        list != null
    }
}

private fun MacrobenchmarkScope.retryingStale(action: () -> Boolean): Boolean {
    repeat(STALE_ATTEMPTS) {
        try {
            val done = action()
            device.waitForIdle()
            return done
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
    return false
}

private fun MacrobenchmarkScope.goBack() {
    device.pressBack()
    device.waitForIdle()
}
