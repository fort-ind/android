package com.fortind.zenith.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.discovery.Announcement
import com.fortind.zenith.discovery.AnnouncementKind
import com.fortind.zenith.ui.feed.FeedMaxWidth
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.mfm.MfmEnvironment

@Composable
fun AnnouncementBars(announcements: List<Announcement>, mfm: MfmEnvironment, onDismiss: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = FeedMaxWidth).fillMaxWidth(),
    ) {
        announcements.forEach { announcement ->
            AnnouncementBar(announcement, mfm, onDismiss)
        }
    }
}

@Composable
private fun AnnouncementBar(announcement: Announcement, mfm: MfmEnvironment, onDismiss: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container: Color, content: Color, icon: ImageVector) = when (announcement.kind) {
        AnnouncementKind.Info -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, Icons.Default.Info)
        AnnouncementKind.Warning -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, Icons.Default.Warning)
        AnnouncementKind.Error -> Triple(colors.errorContainer, colors.onErrorContainer, Icons.Default.Error)
        AnnouncementKind.Success -> Triple(colors.primaryContainer, colors.onPrimaryContainer, Icons.Default.CheckCircle)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("announcement")
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (announcement.title.isNotEmpty()) {
                    Text(announcement.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                if (announcement.textMfm.isNotEmpty()) {
                    MfmContent(announcement.textMfm, mfm, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TooltipIconButton(
                label = stringResource(R.string.announcement_dismiss),
                icon = Icons.Default.Close,
                onClick = { onDismiss(announcement.id) },
            )
        }
    }
}
