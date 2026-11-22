package com.fortind.zenith.auth

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutWipeTest {

    @Test
    fun onlyLeavingAnAccountCountsAsSigningOut() {
        assertTrue(signedOutFromAccount(SessionState.SignedIn(null), SessionState.SignedOut))
        assertFalse(signedOutFromAccount(SessionState.Restoring, SessionState.SignedOut))
        assertFalse(signedOutFromAccount(SessionState.Guest, SessionState.SignedOut))
        assertFalse(signedOutFromAccount(null, SessionState.SignedOut))
        assertFalse(signedOutFromAccount(SessionState.SignedIn(null), SessionState.Guest))
    }

    @Test
    fun ownedFoldersAreDeletedAndTheRestOfTheCacheIsKept() {
        val cache = Files.createTempDirectory("wipe").toFile()
        try {
            SignOutCacheFolders.forEach { name -> File(cache, "$name/nested").mkdirs(); File(cache, "$name/nested/file").writeText("x") }
            File(cache, "instance").mkdirs()
            File(cache, "instance/meta.json").writeText("{}")

            deleteSignOutCacheFolders(cache)

            SignOutCacheFolders.forEach { assertFalse(File(cache, it).exists()) }
            assertTrue(File(cache, "instance/meta.json").isFile)
        } finally {
            cache.deleteRecursively()
        }
    }
}
