package com.fortind.zenith.ui.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fortind.zenith.account.ProfileForm
import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.ProfileField

class FieldRow(val id: Long, name: String, value: String) {
    val name = TextFieldState(name)
    val value = TextFieldState(value)
}

class ProfileFormState(user: CurrentUser) {
    val name = TextFieldState(user.name.orEmpty())
    val bio = TextFieldState(user.description.orEmpty())
    val location = TextFieldState(user.location.orEmpty())
    var birthday by mutableStateOf(user.birthday)
    val fields = mutableStateListOf<FieldRow>()
    var avatarUri by mutableStateOf<String?>(null)
    var bannerUri by mutableStateOf<String?>(null)

    private var nextId = 0L

    init {
        user.fields.forEach { addField(it.name, it.value) }
    }

    fun addField(name: String = "", value: String = "") {
        fields += FieldRow(nextId++, name, value)
    }

    fun removeField(id: Long) {
        fields.removeAll { it.id == id }
    }

    fun toForm(): ProfileForm = ProfileForm(
        name = name.text.toString(),
        description = bio.text.toString(),
        location = location.text.toString(),
        birthday = birthday,
        fields = fields.map { ProfileField(it.name.text.toString(), it.value.text.toString()) },
    )
}
