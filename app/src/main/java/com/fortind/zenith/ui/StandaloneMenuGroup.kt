package com.fortind.zenith.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val SelectableItemInset = 4.dp

@Composable
fun StandaloneMenuGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(0, 1),
        contentPadding = PaddingValues(vertical = SelectableItemInset),
        modifier = modifier,
        content = content,
    )
}
