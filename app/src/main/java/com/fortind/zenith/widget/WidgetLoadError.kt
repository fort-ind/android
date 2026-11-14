package com.fortind.zenith.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fortind.zenith.R

data class ErrorLabels(
    val title: String,
    val retry: String,
    val retryDescription: String,
    val openApp: String,
)

private val MessageMinWidth = 240.dp
private val MessageMinHeight = 128.dp

fun errorShowsMessage(size: DpSize): Boolean = size.width >= MessageMinWidth && size.height >= MessageMinHeight

@Composable
fun WidgetLoadError(labels: ErrorLabels, retry: WidgetTap, openApp: WidgetTap) {
    val size = LocalSize.current
    val colors = GlanceTheme.colors
    if (!errorShowsMessage(size)) {
        val roomy = size.width >= ToolbarDimens.FilledMinSize && size.height >= ToolbarDimens.FilledMinSize
        WidgetSurface(
            target = SurfaceTarget(labels.retryDescription, retry),
            horizontalPadding = if (roomy) ToolbarDimens.Padding else 0.dp,
            verticalPadding = if (roomy) ToolbarDimens.Padding else 0.dp,
        ) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_retry),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.onSurface),
                    modifier = GlanceModifier.size(ToolbarDimens.IconSize),
                )
            }
        }
        return
    }
    WidgetSurface(null, ToolbarDimens.Padding, ToolbarDimens.Padding) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = labels.title,
                maxLines = 2,
                style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                modifier = GlanceModifier.fillMaxWidth(),
            )
            Spacer(modifier = GlanceModifier.height(ToolbarDimens.Spacing))
            Row(modifier = GlanceModifier.fillMaxWidth().height(ToolbarDimens.MinTarget)) {
                ErrorButton(labels.retry, CellTone.Accent, colors.tertiary, colors.onTertiary, retry, GlanceModifier.defaultWeight())
                Spacer(modifier = GlanceModifier.width(ToolbarDimens.Spacing))
                ErrorButton(labels.openApp, CellTone.Container, colors.secondaryContainer, colors.onSecondaryContainer, openApp, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun ErrorButton(
    label: String,
    tone: CellTone,
    container: ColorProvider,
    content: ColorProvider,
    tap: WidgetTap,
    modifier: GlanceModifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        HoverButton(
            icon = NO_ICON,
            text = label,
            tone = tone,
            shape = WidgetShape.Full,
            leading = false,
            description = label,
            tap = tap,
            modifier = modifier.height(ToolbarDimens.MinTarget),
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(ToolbarDimens.MinTarget)
            .shapedBackground(container, WidgetShape.Full)
            .padding(horizontal = 12.dp)
            .clickable(tap.action()),
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}
