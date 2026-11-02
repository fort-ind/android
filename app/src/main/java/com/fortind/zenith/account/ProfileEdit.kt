package com.fortind.zenith.account

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.ProfileField
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

object ProfileLimits {
    const val NAME = 50
    const val BIO = 1500
    const val LOCATION = 50
    const val FIELDS = 16
}

fun String.codePointLength(): Int = codePointCount(0, length)

fun normalizeProfileText(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n').trim()

class Update<T>(val value: T)

data class ProfileEdit(
    val name: Update<String?>? = null,
    val description: Update<String?>? = null,
    val location: Update<String?>? = null,
    val birthday: Update<String?>? = null,
    val fields: List<ProfileField>? = null,
    val avatarId: String? = null,
    val bannerId: String? = null,
) {
    val isEmpty: Boolean
        get() = name == null && description == null && location == null && birthday == null &&
            fields == null && avatarId == null && bannerId == null

    fun toRequest(token: String): JSONObject {
        val body = JSONObject().put("i", token)
        name?.let { body.put("name", it.value ?: JSONObject.NULL) }
        description?.let { body.put("description", it.value ?: JSONObject.NULL) }
        location?.let { body.put("location", it.value ?: JSONObject.NULL) }
        birthday?.let { body.put("birthday", it.value ?: JSONObject.NULL) }
        fields?.let { list ->
            body.put(
                "fields",
                JSONArray().apply { list.forEach { put(JSONObject().put("name", it.name).put("value", it.value)) } },
            )
        }
        avatarId?.let { body.put("avatarId", it) }
        bannerId?.let { body.put("bannerId", it) }
        return body
    }
}

enum class ProfileProblem { NameTooLong, BioTooLong, LocationTooLong, IncompleteField, TooManyFields }

data class ProfileForm(
    val name: String,
    val description: String,
    val location: String,
    val birthday: String?,
    val fields: List<ProfileField>,
) {
    val problems: Set<ProfileProblem>
        get() = buildSet {
            if (normalizeProfileText(name).codePointLength() > ProfileLimits.NAME) add(ProfileProblem.NameTooLong)
            if (normalizeProfileText(description).codePointLength() > ProfileLimits.BIO) add(ProfileProblem.BioTooLong)
            if (normalizeProfileText(location).codePointLength() > ProfileLimits.LOCATION) add(ProfileProblem.LocationTooLong)
            val rows = fields.map { ProfileField(it.name.trim(), it.value.trim()) }.filterNot { it.name.isEmpty() && it.value.isEmpty() }
            if (rows.any { it.name.isEmpty() || it.value.isEmpty() }) add(ProfileProblem.IncompleteField)
            if (rows.size > ProfileLimits.FIELDS) add(ProfileProblem.TooManyFields)
        }

    val isValid: Boolean get() = problems.isEmpty()

    fun cleanFields(): List<ProfileField> = fields
        .map { ProfileField(it.name.trim(), it.value.trim()) }
        .filter { it.name.isNotEmpty() && it.value.isNotEmpty() }

    fun diff(user: CurrentUser): ProfileEdit {
        fun textChange(current: String, saved: String?): Update<String?>? {
            val next = normalizeProfileText(current).ifEmpty { null }
            val before = saved?.let(::normalizeProfileText)?.ifEmpty { null }
            return if (next == before) null else Update(next)
        }
        val cleaned = cleanFields()
        return ProfileEdit(
            name = textChange(name, user.name),
            description = textChange(description, user.description),
            location = textChange(location, user.location),
            birthday = if (birthday == user.birthday) null else Update(birthday),
            fields = cleaned.takeIf { it != user.fields },
        )
    }

    companion object {
        fun from(user: CurrentUser) = ProfileForm(
            name = user.name.orEmpty(),
            description = user.description.orEmpty(),
            location = user.location.orEmpty(),
            birthday = user.birthday,
            fields = user.fields,
        )
    }
}

object Birthday {
    private val pattern = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun toMillis(value: String?): Long? {
        val match = pattern.matchEntire(value ?: return null) ?: return null
        val (year, month, day) = match.destructured
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
            clear()
            set(year.toInt(), month.toInt() - 1, day.toInt())
            timeInMillis
        }
    }

    fun fromMillis(millis: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        return String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }
}
