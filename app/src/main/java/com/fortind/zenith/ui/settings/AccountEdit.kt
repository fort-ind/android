@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fortind.zenith.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.fortind.zenith.R
import com.fortind.zenith.account.Birthday
import com.fortind.zenith.account.ProfileLimits
import com.fortind.zenith.account.ProfileProblem
import com.fortind.zenith.account.codePointLength
import com.fortind.zenith.account.normalizeProfileText
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.account.FieldRow
import com.fortind.zenith.ui.account.ProfileFormState
import com.fortind.zenith.ui.account.ProfileSaveFailure
import com.fortind.zenith.ui.account.ProfileSaveState
import com.fortind.zenith.ui.StatusMessage
import com.fortind.zenith.ui.login.AuthPageEffects
import com.fortind.zenith.ui.login.ErrorMessage
import com.fortind.zenith.ui.login.LoginViewModel
import com.fortind.zenith.ui.rememberReduceMotion
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

private val AvatarSize = 88.dp
private val AvatarRing = 4.dp

@Composable
internal fun AccountSummary(user: CurrentUser) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ImageHeader(
            bannerModel = user.bannerUrl,
            avatarModel = user.avatarUrl,
            name = user.displayName,
            onPickBanner = null,
            onPickAvatar = null,
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = user.handle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImageHeader(
    bannerModel: String?,
    avatarModel: String?,
    name: String,
    onPickBanner: (() -> Unit)?,
    onPickAvatar: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = AvatarSize / 2)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))),
        ) {
            if (bannerModel != null) {
                AsyncImage(
                    model = bannerModel,
                    contentDescription = stringResource(R.string.account_banner_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (onPickBanner != null) {
                FilledTonalIconButton(
                    onClick = onPickBanner,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.account_change_banner))
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 24.dp, y = AvatarSize / 2)
                .size(AvatarSize + AvatarRing * 2)
                .clip(CircleShape)
                .background(colors.surface)
                .padding(AvatarRing),
        ) {
            Avatar(name = name, url = avatarModel, size = AvatarSize)
            if (onPickAvatar != null) {
                FilledTonalIconButton(
                    onClick = onPickAvatar,
                    modifier = Modifier.align(Alignment.BottomEnd).size(40.dp),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.account_change_avatar))
                }
            }
        }
    }
}

@Composable
internal fun EditProfileContent(user: CurrentUser, form: ProfileFormState, saveState: ProfileSaveState) {
    if (saveState.needsPermission) {
        ReauthorizePrompt()
        return
    }
    val enabled = !saveState.saving
    val problems = form.toForm().problems
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) form.avatarUri = uri.toString()
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) form.bannerUri = uri.toString()
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (saveState.saving) {
            val progress = saveState.progress
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        saveState.failure?.let { failure ->
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        ImageHeader(
            bannerModel = form.bannerUri ?: user.bannerUrl,
            avatarModel = form.avatarUri ?: user.avatarUrl,
            name = user.displayName,
            onPickBanner = if (enabled) ({ pickBanner.launch(imageOnly) }) else null,
            onPickAvatar = if (enabled) ({ pickAvatar.launch(imageOnly) }) else null,
        )
        CountedField(
            state = form.name,
            labelRes = R.string.account_field_name,
            limit = ProfileLimits.NAME,
            over = ProfileProblem.NameTooLong in problems,
            enabled = enabled,
            singleLine = true,
        )
        CountedField(
            state = form.bio,
            labelRes = R.string.account_field_bio,
            limit = ProfileLimits.BIO,
            over = ProfileProblem.BioTooLong in problems,
            enabled = enabled,
            singleLine = false,
        )
        CountedField(
            state = form.location,
            labelRes = R.string.account_field_location,
            limit = ProfileLimits.LOCATION,
            over = ProfileProblem.LocationTooLong in problems,
            enabled = enabled,
            singleLine = true,
        )
        BirthdayField(form = form, enabled = enabled)
        ProfileFields(form = form, incomplete = ProfileProblem.IncompleteField in problems, enabled = enabled)
        if (saveState.saving) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    }
}

@Composable
private fun CountedField(
    state: TextFieldState,
    @StringRes labelRes: Int,
    limit: Int,
    over: Boolean,
    enabled: Boolean,
    singleLine: Boolean,
) {
    val count = normalizeProfileText(state.text.toString()).codePointLength()
    OutlinedTextField(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(labelRes)) },
        isError = over,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        supportingText = {
            Text(
                text = stringResource(R.string.account_counter, count, limit),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        },
    )
}

@Composable
private fun BirthdayField(form: ProfileFormState, enabled: Boolean) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val millis = Birthday.toMillis(form.birthday)
    val formatter = remember {
        DateFormat.getDateInstance(DateFormat.LONG).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    val shown = millis?.let { formatter.format(Date(it)) } ?: stringResource(R.string.account_birthday_not_set)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.account_field_birthday),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = shown, style = MaterialTheme.typography.bodyLarge)
        }
        if (millis != null) {
            IconButton(onClick = { form.birthday = null }, enabled = enabled) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.account_birthday_clear))
            }
        }
        IconButton(onClick = { picking = true }, enabled = enabled) {
            Icon(Icons.Default.Cake, contentDescription = stringResource(R.string.account_birthday_pick))
        }
    }

    if (picking) {
        val pastOnly = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
            }
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis, selectableDates = pastOnly)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { form.birthday = Birthday.fromMillis(it) }
                        picking = false
                    },
                ) {
                    Text(stringResource(R.string.account_birthday_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.note_dialog_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ProfileFields(form: ProfileFormState, incomplete: Boolean, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.account_fields_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                text = stringResource(R.string.account_fields_count, form.fields.size, ProfileLimits.FIELDS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        form.fields.forEach { row ->
            key(row.id) {
                FieldEditor(row = row, enabled = enabled, onRemove = { form.removeField(row.id) })
            }
        }
        if (incomplete) {
            Text(
                text = stringResource(R.string.account_field_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(
            onClick = { form.addField() },
            enabled = enabled && form.fields.size < ProfileLimits.FIELDS,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.account_field_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FieldEditor(row: FieldRow, enabled: Boolean, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = row.name,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.account_field_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                state = row.value,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(stringResource(R.string.account_field_content)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.account_field_remove))
        }
    }
}

@Composable
internal fun ReauthorizePrompt() {
    val login: LoginViewModel = viewModel(key = "reauthorize", factory = LoginViewModel.Factory)
    val loginState by login.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    AuthPageEffects(login)

    StatusMessage(
        shape = MaterialShapes.Slanted,
        title = stringResource(R.string.account_permission_title),
        body = stringResource(R.string.account_permission_body, FortInstance.HOST),
    ) {
        loginState.error?.let { ErrorMessage(it) }
        if (loginState.signingIn) {
            if (!reduceMotion) {
                LoadingIndicator()
            } else {
                Text(stringResource(R.string.login_signing_in), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = login::onCancel) { Text(stringResource(R.string.login_cancel)) }
        } else {
            Button(onClick = login::onSignInClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_permission_grant))
            }
        }
    }
}

@StringRes
private fun ProfileSaveFailure.messageRes(): Int = when (this) {
    ProfileSaveFailure.Offline -> R.string.account_save_failed_offline
    ProfileSaveFailure.RateLimited -> R.string.account_save_failed_rate
    ProfileSaveFailure.ProhibitedName -> R.string.account_save_failed_name
    ProfileSaveFailure.NotAnImage -> R.string.account_save_failed_image
    ProfileSaveFailure.FileTooBig -> R.string.account_save_failed_too_big
    ProfileSaveFailure.NoFreeSpace -> R.string.account_save_failed_space
    ProfileSaveFailure.Unreadable -> R.string.account_save_failed_unreadable
}
