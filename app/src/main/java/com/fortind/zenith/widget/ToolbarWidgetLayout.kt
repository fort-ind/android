package com.fortind.zenith.widget

import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
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
    val search: WidgetTap,
    val post: WidgetTap,
    val notifications: WidgetTap,
    val profile: WidgetTap,
    val games: WidgetTap,
    val signIn: WidgetTap,
)

enum class WidgetShape(val radius: Dp, @param:DrawableRes val legacyDrawable: Int) {
    Full(100.dp, R.drawable.widget_shape_full),
    Medium(16.dp, R.drawable.widget_shape_medium),
}

enum class CellTone(
    @param:DrawableRes val full: Int,
    @param:DrawableRes val medium: Int,
    @param:ColorRes val content: Int,
    @param:LayoutRes val textLayout: Int,
) {
    Accent(R.drawable.widget_cell_accent_full, R.drawable.widget_cell_accent_medium, R.color.widget_on_accent, R.layout.widget_toolbar_cell_text_accent),
    Container(R.drawable.widget_cell_container_full, R.drawable.widget_cell_container_medium, R.color.widget_on_container, R.layout.widget_toolbar_cell_text_container),
    Plain(R.drawable.widget_cell_plain_full, R.drawable.widget_cell_plain_medium, R.color.widget_on_surface, R.layout.widget_toolbar_cell_text_plain),
    ;

    fun background(shape: WidgetShape): Int = when (shape) {
        WidgetShape.Full -> full
        WidgetShape.Medium -> medium
    }
}

private class ItemStyle(
    val container: ColorProvider?,
    val content: ColorProvider,
    val shape: WidgetShape,
    val tone: CellTone,
)

@Composable
fun ToolbarWidgetContent(signedIn: Boolean, labels: ToolbarLabels, actions: ToolbarActions) {
    val plan = planToolbar(LocalSize.current, signedIn)
    val whole = plan.wholeSurface
    val target = whole?.let { SurfaceTarget(it.description(labels), it.tap(actions)) }
    WidgetSurface(target, plan.horizontalPadding, plan.verticalPadding) {
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

class SurfaceTarget(val description: String, val tap: WidgetTap)

@Composable
fun WidgetSurface(
    target: SurfaceTarget?,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    content: @Composable () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (target == null) {
            Scaffold(
                modifier = GlanceModifier.padding(vertical = verticalPadding),
                horizontalPadding = horizontalPadding,
                content = content,
            )
        } else {
            Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(android.R.dimen.system_app_widget_background_radius)) {
                Scaffold(
                    modifier = GlanceModifier.padding(vertical = verticalPadding),
                    horizontalPadding = horizontalPadding,
                    content = content,
                )
                HoverSurface(target)
            }
        }
    } else {
        val interaction = if (target == null) {
            GlanceModifier
        } else {
            GlanceModifier
                .semantics { contentDescription = target.description }
                .clickable(target.tap.action())
        }
        Box(
            modifier = interaction
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
    val leading = text != null && item == ToolbarItem.Search
    if (clickable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        HoverButton(
            icon = item.icon,
            text = text,
            tone = style.tone,
            shape = style.shape,
            leading = leading,
            description = item.description(labels),
            tap = item.tap(actions),
            modifier = GlanceModifier.fillMaxSize(),
        )
    } else {
        GlanceCell(item, style, text, leading, if (clickable) item.description(labels) else null, if (clickable) item.tap(actions) else null)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun HoverSurface(target: SurfaceTarget) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_hover_surface)
    views.setContentDescription(R.id.widget_hover_surface, target.description)
    views.setOnClickPendingIntent(R.id.widget_hover_surface, target.tap.pendingIntent(context))
    AndroidRemoteViews(views, GlanceModifier.fillMaxSize())
}

const val NO_ICON = 0

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun HoverButton(
    @DrawableRes icon: Int,
    text: String?,
    tone: CellTone,
    shape: WidgetShape,
    leading: Boolean,
    description: String,
    tap: WidgetTap,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    val views = if (text == null) {
        RemoteViews(context.packageName, R.layout.widget_toolbar_cell).apply {
            setImageViewResource(R.id.widget_cell_icon, icon)
            setColorStateList(R.id.widget_cell_icon, "setImageTintList", tone.content)
        }
    } else {
        RemoteViews(context.packageName, tone.textLayout).apply {
            setTextViewText(R.id.widget_cell_label, text)
            setTextViewCompoundDrawablesRelative(R.id.widget_cell_label, icon, 0, 0, 0)
            if (leading) {
                setTextViewTextSize(R.id.widget_cell_label, TypedValue.COMPLEX_UNIT_SP, 16f)
                setViewLayoutWidth(R.id.widget_cell_label, ViewGroup.LayoutParams.MATCH_PARENT.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                setInt(R.id.widget_cell_label, "setGravity", Gravity.START or Gravity.CENTER_VERTICAL)
            }
        }
    }
    views.setInt(R.id.widget_cell, "setBackgroundResource", tone.background(shape))
    views.setContentDescription(R.id.widget_cell, description)
    views.setOnClickPendingIntent(R.id.widget_cell, tap.pendingIntent(context))
    AndroidRemoteViews(views, modifier)
}

@Composable
private fun GlanceCell(item: ToolbarItem, style: ItemStyle, text: String?, leading: Boolean, description: String?, tap: WidgetTap?) {
    val interaction = if (description != null && tap != null) {
        GlanceModifier
            .semantics { contentDescription = description }
            .clickable(tap.action())
    } else {
        GlanceModifier
    }
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
        !filled -> ItemStyle(container = null, content = colors.onSurface, shape = shape, tone = CellTone.Plain)
        emphasized -> ItemStyle(container = colors.tertiary, content = colors.onTertiary, shape = shape, tone = CellTone.Accent)
        else -> ItemStyle(container = colors.secondaryContainer, content = colors.onSecondaryContainer, shape = shape, tone = CellTone.Container)
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

private fun ToolbarItem.tap(actions: ToolbarActions): WidgetTap = when (this) {
    ToolbarItem.Search -> actions.search
    ToolbarItem.Post -> actions.post
    ToolbarItem.Notifications -> actions.notifications
    ToolbarItem.Profile -> actions.profile
    ToolbarItem.Games -> actions.games
    ToolbarItem.SignIn -> actions.signIn
}
