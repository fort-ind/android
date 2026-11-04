package com.fortind.zenith.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.fortind.zenith"

private const val BROWSE_AS_GUEST = "Continue w/o an account"
private const val UI_TIMEOUT_MS = 5_000L
private const val FEED_TIMEOUT_MS = 15_000L
private const val NOTE = "note"
private const val FEED_LIST = "feed:list"
private const val NOTE_AVATAR = "note:avatar"
private const val PERSON_MEDIA_TAB = "person:tab:Media"
private val DESTINATIONS = listOf("nav:GAMES", "nav:NOTIFICATIONS", "nav:PROFILE", "nav:SETTINGS", "nav:HOME")

fun MacrobenchmarkScope.browseAsGuest() {
    device.wait(Until.hasObject(By.text(BROWSE_AS_GUEST)), UI_TIMEOUT_MS)
    checkNotNull(device.findObject(By.text(BROWSE_AS_GUEST))) { "Login screen did not appear" }.click()
    device.waitForIdle()

    DESTINATIONS.forEach { tag ->
        device.wait(Until.hasObject(By.res(tag)), UI_TIMEOUT_MS)
        checkNotNull(device.findObject(By.res(tag))) { "Destination $tag did not appear" }.click()
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.browseFeed() {
    if (!device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)) return
    flingFeed(times = 3)
    device.findObject(By.res("timeline:Global"))?.click()
    if (device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)) flingFeed(times = 2)
    device.findObject(By.res("timeline:Local"))?.click()
    device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)
    device.findObject(By.res(NOTE))?.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
    browsePerson()
    browseDiscovery()
}

private fun MacrobenchmarkScope.browseDiscovery() {
    device.findObject(By.res("search:field"))?.click() ?: return
    if (!device.wait(Until.hasObject(By.textStartsWith("#")), FEED_TIMEOUT_MS)) {
        device.pressBack()
        device.waitForIdle()
        return
    }
    device.findObject(By.textStartsWith("#"))?.click()
    device.wait(Until.hasObject(By.res(NOTE)), FEED_TIMEOUT_MS)
    device.pressBack()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.browsePerson() {
    device.findObject(By.res(NOTE_AVATAR))?.click() ?: return
    if (!device.wait(Until.hasObject(By.res(PERSON_MEDIA_TAB)), FEED_TIMEOUT_MS)) return
    device.findObject(By.res(PERSON_MEDIA_TAB))?.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.flingFeed(times: Int) {
    val list = device.findObject(By.res(FEED_LIST)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(times) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
