package com.fortind.zenith.games

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesTest {

    @Test
    fun addingPutsTheNewestFavoriteFirst() {
        val (urls, edit) = toggleFavorite(listOf("a", "b"), "c")

        assertEquals(listOf("c", "a", "b"), urls)
        assertEquals(FavoriteEdit("c", added = true, index = 0), edit)
    }

    @Test
    fun removingRemembersWhereTheFavoriteWas() {
        val (urls, edit) = toggleFavorite(listOf("a", "b", "c"), "b")

        assertEquals(listOf("a", "c"), urls)
        assertEquals(FavoriteEdit("b", added = false, index = 1), edit)
    }

    @Test
    fun revertingARemovalRestoresTheOriginalPosition() {
        val (removed, edit) = toggleFavorite(listOf("a", "b", "c"), "b")

        assertEquals(listOf("a", "b", "c"), revertFavorite(removed, edit))
    }

    @Test
    fun revertingAnAddRemovesIt() {
        val (added, edit) = toggleFavorite(listOf("a"), "b")

        assertEquals(listOf("a"), revertFavorite(added, edit))
    }

    @Test
    fun revertingARemovalClampsToTheEndWhenTheListShrank() {
        val edit = FavoriteEdit("z", added = false, index = 5)

        assertEquals(listOf("a", "z"), revertFavorite(listOf("a"), edit))
    }

    @Test
    fun revertingARemovalDoesNotDuplicateAFavoriteThatCameBack() {
        val edit = FavoriteEdit("a", added = false, index = 1)

        assertEquals(listOf("a", "b"), revertFavorite(listOf("a", "b"), edit))
    }
}
