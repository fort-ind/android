package com.fortind.zenith.ui.people

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.fortind.zenith.R
import com.fortind.zenith.people.UserDetail
import com.fortind.zenith.people.UserRole
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.social.LocalNoteEnvironment
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RoleColor = Regex("#[0-9a-fA-F]{6}")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonAboutContent(detail: UserDetail, environment: NoteEnvironment, modifier: Modifier = Modifier) {
    val textEnvironment = remember(detail.user, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(detail.user)) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail.bioMfm.isNotEmpty()) {
            MfmContent(detail.bioMfm, textEnvironment, style = MaterialTheme.typography.bodyLarge)
        }
        if (detail.roles.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                detail.roles.forEach { RoleTag(it) }
            }
        }
        detail.location?.let { AboutLine(stringResource(R.string.person_about_location), it) }
        detail.birthday?.let(::monthAndDay)?.let { AboutLine(stringResource(R.string.person_about_birthday), it) }
        if (detail.createdAtMillis > 0L) {
            val joined = remember(detail.createdAtMillis) { DateFormat.getDateInstance(DateFormat.LONG).format(Date(detail.createdAtMillis)) }
            AboutLine(stringResource(R.string.person_about_joined), joined)
        }
        detail.fields.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MfmContent(
                    field.nameMfm,
                    textEnvironment,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MfmContent(field.valueMfm, textEnvironment, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleTag(role: UserRole) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val edge = remember(role.color, outline) { role.color?.takeIf(RoleColor::matches)?.let(::parseRoleColor) ?: outline }
    val tag = @Composable {
        Surface(shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, edge), color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                role.iconUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(role.name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val description = role.description?.takeIf { it.isNotBlank() }
    if (description == null) {
        tag()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(description) } },
            state = rememberTooltipState(),
        ) { tag() }
    }
}

private fun parseRoleColor(hex: String): Color = Color(hex.removePrefix("#").toInt(16).toLong() or 0xFF000000L)

private fun monthAndDay(birthday: String): String? = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(birthday) ?: return null
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd")
    SimpleDateFormat(pattern, Locale.getDefault()).format(parsed)
} catch (_: ParseException) {
    null
}

@Composable
fun PersonAboutPane(userId: String, people: PeopleViewModel, modifier: Modifier = Modifier) {
    val state by people.uiState.collectAsStateWithLifecycle()
    val detail = state.person(userId).detail ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.person_about_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            PersonAboutContent(detail, LocalNoteEnvironment.current)
        }
    }
}
