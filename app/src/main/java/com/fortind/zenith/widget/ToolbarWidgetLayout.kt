package com.fortind.zenith.widget

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fortind.zenith.R

data class ToolbarLabels(
    val search: String,
    val post: String,
    val notifications: String,
    val profile: String,
    val games: String,
    val signIn: String,
    val signInDescription: String,
)

class ToolbarActions(
    val search: Action,
    val post: Action,
    val notifications: Action,
    val profile: Action,
    val games: Action,
    val signIn: Action,
)

enum class WidgetShape(val radius: Dp, @param:DrawableRes val legacyDrawable: Int) {
    Full(100.dp, R.drawable.widget_shape_full),
    Medium(16.dp, R.drawable.widget_shape_medium),
}

private class ItemStyle(
    val container: ColorProvider?,
    val content: ColorProvider,
    val shape: WidgetShape,
)

@Composable
fun ToolbarWidgetContent(signedIn: Boolean, labels: ToolbarLabels, actions: ToolbarActions) {
    val plan = planToolbar(LocalSize.current, signedIn)
    val whole = plan.wholeSurface
    val surfaceModifier = if (whole == null) {
        GlanceModifier
    } else {
        GlanceModifier
            .semantics { contentDescription = whole.description(labels) }
            .clickable(whole.action(actions))
    }
    WidgetSurface(surfaceModifier, plan.horizontalPadding, plan.verticalPadding) {
        val cell: @Composable (ToolbarItem) -> Unit = { item ->
            ToolbarCell(item, plan, signedIn, labels, actions, clickable = whole == null)
        }
        when (plan.layout) {
            ToolbarLayout.SideBarGrid -> Row(modifier = GlanceModifier.fillMaxSize()) {
                Box(modifier = GlanceModifier.fillMaxHeight().width(ToolbarDimens.LeadSize)) { plan.lead?.let { cell(it) } }
                Spacer(modifier = GlanceModifier.fillMaxHeight().width(ToolbarDimens.Spacing))
                ToolbarGrid(plan.rows, GlanceModifier.fillMaxHeight().defaultWeight(), cell)
            }
            ToolbarLayout.HeaderGrid -> Column(modifier = GlanceModifier.fillMaxSize()) {
                Box(modifier = GlanceModifier.fillMaxWidth().height(ToolbarDimens.LeadSize)) { plan.lead?.let { cell(it) } }
                Spacer(modifier = GlanceModifier.fillMaxWidth().height(ToolbarDimens.Spacing))
                ToolbarGrid(plan.rows, GlanceModifier.fillMaxWidth().defaultWeight(), cell)
            }
            ToolbarLayout.Row, ToolbarLayout.Column, ToolbarLayout.Grid ->
                ToolbarGrid(plan.rows, GlanceModifier.fillMaxSize(), cell)
        }
    }
}

@Composable
fun WidgetSurface(
    modifier: GlanceModifier,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Scaffold(
            modifier = modifier.padding(vertical = verticalPadding),
            horizontalPadding = horizontalPadding,
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    imageProvider = ImageProvider(R.drawable.widget_shape_surface),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
                )
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            content()
        }
    }
}

@Composable
private fun ToolbarGrid(
    rows: List<List<ToolbarItem>>,
    modifier: GlanceModifier,
    cell: @Composable (ToolbarItem) -> Unit,
) {
    val half = ToolbarDimens.Spacing / 2
    Column(modifier = modifier) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .padding(
                        top = if (rowIndex == 0) 0.dp else half,
                        bottom = if (rowIndex == rows.lastIndex) 0.dp else half,
                    ),
            ) {
                row.forEachIndexed { index, item ->
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .padding(
                                start = if (index == 0) 0.dp else half,
                                end = if (index == row.lastIndex) 0.dp else half,
                            ),
                    ) {
                        cell(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarCell(
    item: ToolbarItem,
    plan: ToolbarPlan,
    signedIn: Boolean,
    labels: ToolbarLabels,
    actions: ToolbarActions,
    clickable: Boolean,
) {
    val style = item.style(plan.filled, signedIn)
    val text = when {
        item == ToolbarItem.Search && plan.lead == item && plan.searchText -> labels.search
        item == ToolbarItem.SignIn && plan.signInText -> labels.signIn
        else -> null
    }
    val interaction = if (clickable) {
        GlanceModifier
            .semantics { contentDescription = item.description(labels) }
            .clickable(item.action(actions))
    } else {
        GlanceModifier
    }
    val leading = text != null && item == ToolbarItem.Search
    Row(
        horizontalAlignment = if (leading) Alignment.Start else Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxSize()
            .shapedBackground(style.container, style.shape)
            .padding(horizontal = if (text != null) 16.dp else 0.dp)
            .then(interaction),
    ) {
        Image(
            provider = ImageProvider(item.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(style.content),
            modifier = GlanceModifier.size(ToolbarDimens.IconSize),
        )
        if (text != null) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = text,
                maxLines = 1,
                style = TextStyle(color = style.content, fontSize = if (leading) 16.sp else 14.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
fun GlanceModifier.shapedBackground(color: ColorProvider?, shape: WidgetShape): GlanceModifier = when {
    color == null -> cornerRadius(shape.radius)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> background(color).cornerRadius(shape.radius)
    else -> background(imageProvider = ImageProvider(shape.legacyDrawable), colorFilter = ColorFilter.tint(color))
}

@Composable
private fun ToolbarItem.style(filled: Boolean, signedIn: Boolean): ItemStyle {
    val colors = GlanceTheme.colors
    val emphasized = if (signedIn) this == ToolbarItem.Search else this == ToolbarItem.SignIn
    val shape = if (this == ToolbarItem.Search || this == ToolbarItem.SignIn) WidgetShape.Full else WidgetShape.Medium
    return when {
        !filled -> ItemStyle(container = null, content = colors.onSurface, shape = shape)
        emphasized -> ItemStyle(container = colors.tertiary, content = colors.onTertiary, shape = shape)
        else -> ItemStyle(container = colors.secondaryContainer, content = colors.onSecondaryContainer, shape = shape)
    }
}

private val ToolbarItem.icon: Int
    @DrawableRes get() = when (this) {
        ToolbarItem.Search -> R.drawable.ic_widget_search
        ToolbarItem.Post -> R.drawable.ic_widget_post
        ToolbarItem.Notifications -> R.drawable.ic_widget_notifications
        ToolbarItem.Profile -> R.drawable.ic_widget_profile
        ToolbarItem.Games -> R.drawable.ic_widget_games
        ToolbarItem.SignIn -> R.drawable.ic_widget_sign_in
    }

private fun ToolbarItem.description(labels: ToolbarLabels): String = when (this) {
    ToolbarItem.Search -> labels.search
    ToolbarItem.Post -> labels.post
    ToolbarItem.Notifications -> labels.notifications
    ToolbarItem.Profile -> labels.profile
    ToolbarItem.Games -> labels.games
    ToolbarItem.SignIn -> labels.signInDescription
}

private fun ToolbarItem.action(actions: ToolbarActions): Action = when (this) {
    ToolbarItem.Search -> actions.search
    ToolbarItem.Post -> actions.post
    ToolbarItem.Notifications -> actions.notifications
    ToolbarItem.Profile -> actions.profile
    ToolbarItem.Games -> actions.games
    ToolbarItem.SignIn -> actions.signIn
}
