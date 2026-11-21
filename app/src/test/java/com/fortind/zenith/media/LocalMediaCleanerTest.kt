package com.fortind.zenith.media

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMediaCleanerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var cache: File

    @Before
    fun setUp() {
        cache = temp.newFolder("cache")
    }

    private fun fileIn(vararg path: String): File =
        File(cache, path.joinToString(File.separator)).also {
            it.parentFile?.mkdirs()
            it.writeText("data")
        }

    private fun uriOf(file: File): String = file.toURI().toString()

    private fun media(file: File) = LocalMedia(uriOf(file), file.name, "image/jpeg", file.length())

    @Test
    fun aFileInOneOfTheComposersTempFoldersIsOwned() {
        listOf("captures", "staged", "compressed", "sanitized").forEach { folder ->
            val file = fileIn(folder, "a.jpg")

            assertEquals(file.canonicalFile, ownedCacheFile(uriOf(file), cache))
        }
    }

    @Test
    fun namesWithSpacesAndSymbolsStillResolve() {
        val file = fileIn("captures", "my photo #1 (copy).jpg")

        assertEquals(file.canonicalFile, ownedCacheFile(uriOf(file), cache))
    }

    @Test
    fun otherCacheFoldersAreLeftAlone() {
        listOf("instance", "remote", "image_cache").forEach { folder ->
            assertNull(ownedCacheFile(uriOf(fileIn(folder, "x.bin")), cache))
        }
    }

    @Test
    fun aFolderThatOnlySharesANamePrefixIsLeftAlone() {
        assertNull(ownedCacheFile(uriOf(fileIn("captures2", "x.jpg")), cache))
        assertNull(ownedCacheFile(uriOf(fileIn("staged-old", "x.jpg")), cache))
    }

    @Test
    fun aFileOutsideTheCacheIsLeftAlone() {
        val elsewhere = temp.newFile("elsewhere.jpg")

        assertNull(ownedCacheFile(uriOf(elsewhere), cache))
    }

    @Test
    fun dotDotCannotEscapeAnOwnedFolder() {
        fileIn("captures", "a.jpg")
        val target = fileIn("instance", "meta.json")
        val sneaky = uriOf(File(File(cache, "captures"), "../instance/meta.json"))

        assertTrue(target.exists())
        assertNull(ownedCacheFile(sneaky, cache))
    }

    @Test
    fun onlyFileUrisCanBeOwned() {
        assertNull(ownedCacheFile("content://media/a.jpg", cache))
        assertNull(ownedCacheFile("https://files/a.jpg", cache))
    }

    @Test
    fun aMalformedUriIsNotOwnedAndDoesNotThrow() {
        assertNull(ownedCacheFile("file:///a b", cache))
        assertNull(ownedCacheFile("", cache))
    }

    @Test
    fun aFolderIsNeverOwned() {
        fileIn("captures", "a.jpg")

        assertNull(ownedCacheFile(uriOf(File(cache, "captures")), cache))
    }

    @Test
    fun releasingDeletesTheFileAndNothingElse() = runTest {
        val mine = fileIn("captures", "a.jpg")
        val sibling = fileIn("captures", "b.jpg")
        val elsewhere = temp.newFile("elsewhere.jpg")
        val cleaner = CacheMediaCleaner { cache }

        cleaner.release(media(mine))
        cleaner.release(media(elsewhere))

        assertFalse(mine.exists())
        assertTrue(sibling.exists())
        assertTrue(elsewhere.exists())
    }

    @Test
    fun releasingAMissingFileIsHarmless() = runTest {
        val gone = File(File(cache, "captures"), "gone.jpg")

        CacheMediaCleaner { cache }.release(media(gone))

        assertFalse(gone.exists())
    }
}
