package com.fortind.zenith.ui.mfm

import android.content.res.Resources
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.mfm.Ago
import com.fortind.zenith.mfm.AgoUnit
import com.fortind.zenith.mfm.ago
import com.fortind.zenith.ui.absoluteTime

internal val UnixTimeIcon = 14.dp
internal val UnixTimeGap = 4.dp
internal val UnixTimeStart = 6.dp
internal val UnixTimeEnd = 10.dp
internal val UnixTimeBorder = 1.dp
internal val UnixTimeVertical = 3.dp
internal const val UNIX_TIME_FONT_SCALE = 0.9f

internal val UnixTimeFixedWidth: Dp = UnixTimeStart + UnixTimeIcon + UnixTimeGap + UnixTimeEnd + UnixTimeBorder * 2

internal fun unixTimeLabel(resources: Resources, seconds: Long, nowMillis: Long): String =
    resources.getString(R.string.unixtime_label, absoluteTime(seconds * 1_000L), agoText(resources, ago(seconds, nowMillis)))

internal fun agoText(resources: Resources, ago: Ago): String = when (ago) {
    Ago.JustNow -> resources.getString(R.string.unixtime_just_now)
    is Ago.Past -> resources.getString(
        when (ago.unit) {
            AgoUnit.Years -> R.string.unixtime_ago_years
            AgoUnit.Months -> R.string.unixtime_ago_months
            AgoUnit.Weeks -> R.string.unixtime_ago_weeks
            AgoUnit.Days -> R.string.unixtime_ago_days
            AgoUnit.Hours -> R.string.unixtime_ago_hours
            AgoUnit.Minutes -> R.string.unixtime_ago_minutes
            AgoUnit.Seconds -> R.string.unixtime_ago_seconds
        },
        ago.count,
    )
    is Ago.Ahead -> resources.getString(
        when (ago.unit) {
            AgoUnit.Years -> R.string.unixtime_in_years
            AgoUnit.Months -> R.string.unixtime_in_months
            AgoUnit.Weeks -> R.string.unixtime_in_weeks
            AgoUnit.Days -> R.string.unixtime_in_days
            AgoUnit.Hours -> R.string.unixtime_in_hours
            AgoUnit.Minutes -> R.string.unixtime_in_minutes
            AgoUnit.Seconds -> R.string.unixtime_in_seconds
        },
        ago.count,
    )
}

@Composable
internal fun UnixTimeChip(label: String, style: TextStyle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UnixTimeGap),
        modifier = Modifier
            .fillMaxSize()
            .border(UnixTimeBorder, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(start = UnixTimeStart, end = UnixTimeEnd),
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(UnixTimeIcon),
        )
        Text(text = label, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}
