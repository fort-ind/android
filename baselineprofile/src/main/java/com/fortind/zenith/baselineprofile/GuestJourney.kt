package com.fortind.zenith.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.fortind.zenith"

private const val BROWSE_AS_GUEST = "Continue w/o an account"
private const val UI_TIMEOUT_MS = 5_000L
private val DESTINATIONS = listOf("Games", "Profile", "Settings", "Home")

fun MacrobenchmarkScope.browseAsGuest() {
    device.wait(Until.hasObject(By.text(BROWSE_AS_GUEST)), UI_TIMEOUT_MS)
    checkNotNull(device.findObject(By.text(BROWSE_AS_GUEST))) { "Login screen did not appear" }.click()
    device.waitForIdle()

    DESTINATIONS.forEach { label ->
        device.wait(Until.hasObject(By.text(label)), UI_TIMEOUT_MS)
        checkNotNull(device.findObject(By.text(label))) { "Destination $label did not appear" }.click()
        device.waitForIdle()
    }
}
