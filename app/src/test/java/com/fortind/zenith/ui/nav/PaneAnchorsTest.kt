package com.fortind.zenith.ui.nav

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PaneAnchorsTest {
    @Test
    fun nothingSavedMeansNoInitialAnchor() {
        assertEquals(NoSavedAnchor, restoredAnchorIndex(null))
    }

    @Test
    fun aSavedIndexIsRestored() {
        assertEquals(2, restoredAnchorIndex(2))
    }

    @Test
    fun anIndexThisListNoLongerHasIsIgnored() {
        assertEquals(NoSavedAnchor, restoredAnchorIndex(PaneExpansionAnchors.size))
        assertEquals(NoSavedAnchor, restoredAnchorIndex(-3))
    }

    @Test
    fun anchorsAreFoundByValue() {
        assertEquals(2, anchorIndexOf(PaneExpansionAnchor.Proportion(0.5f)))
        assertEquals(PaneExpansionAnchors.lastIndex, anchorIndexOf(DetailCollapsedAnchor))
    }

    @Test
    fun anUnknownOrMissingAnchorHasNoIndex() {
        assertNull(anchorIndexOf(PaneExpansionAnchor.Proportion(0.3f)))
        assertNull(anchorIndexOf(null))
    }
}
