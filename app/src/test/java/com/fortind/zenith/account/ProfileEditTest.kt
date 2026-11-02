package com.fortind.zenith.account

import com.fortind.zenith.auth.CurrentUser
import com.fortind.zenith.auth.ProfileField
import com.fortind.zenith.auth.parseCurrentUser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditTest {

    private val user = CurrentUser(
        id = "1",
        username = "me",
        name = "Me",
        avatarUrl = null,
        bannerUrl = null,
        description = "hello",
        location = "Earth",
        birthday = "2000-02-29",
        fields = listOf(ProfileField("site", "example.com")),
    )

    @Test
    fun anUntouchedFormProducesNoEdit() {
        assertTrue(ProfileForm.from(user).diff(user).isEmpty)
    }

    @Test
    fun onlyChangedKeysAreSent() {
        val edit = ProfileForm.from(user).copy(name = "New name").diff(user)

        val body = edit.toRequest("token")

        assertEquals(setOf("i", "name"), body.keys().asSequence().toSet())
        assertEquals("New name", body.getString("name"))
    }

    @Test
    fun emptiedTextIsSentAsNullNotAnEmptyString() {
        val edit = ProfileForm.from(user).copy(name = "  ", description = "", location = "\n").diff(user)

        val body = edit.toRequest("token")

        assertTrue(body.isNull("name"))
        assertTrue(body.isNull("description"))
        assertTrue(body.isNull("location"))
    }

    @Test
    fun whitespaceAndLineEndingsDoNotCountAsChanges() {
        val form = ProfileForm.from(user).copy(description = "  hello \r\n")

        assertTrue(form.diff(user).isEmpty)
    }

    @Test
    fun aClearedBirthdayIsSentAsNull() {
        val body = ProfileForm.from(user).copy(birthday = null).diff(user).toRequest("token")

        assertTrue(body.has("birthday"))
        assertTrue(body.isNull("birthday"))
    }

    @Test
    fun fieldsAreSentAsTheWholeCleanedListAndIncompleteRowsAreRejected() {
        val form = ProfileForm.from(user).copy(
            fields = listOf(ProfileField(" site ", " example.com "), ProfileField("", ""), ProfileField("mastodon", " @me ")),
        )

        assertTrue(form.isValid)
        val array = form.diff(user).toRequest("token").getJSONArray("fields")
        assertEquals(2, array.length())
        assertEquals("mastodon", array.getJSONObject(1).getString("name"))
        assertEquals("@me", array.getJSONObject(1).getString("value"))

        val half = form.copy(fields = listOf(ProfileField("only a label", "")))
        assertEquals(setOf(ProfileProblem.IncompleteField), half.problems)
        assertFalse(half.isValid)
    }

    @Test
    fun limitsCountCodePointsSoAnEmojiIsOne() {
        val emoji = "😀"
        val atLimit = ProfileForm.from(user).copy(name = emoji.repeat(ProfileLimits.NAME))
        val overLimit = ProfileForm.from(user).copy(name = emoji.repeat(ProfileLimits.NAME + 1))

        assertTrue(atLimit.isValid)
        assertEquals(setOf(ProfileProblem.NameTooLong), overLimit.problems)
    }

    @Test
    fun moreThanSixteenFieldsIsRejected() {
        val form = ProfileForm.from(user).copy(fields = (1..17).map { ProfileField("k$it", "v$it") })

        assertEquals(setOf(ProfileProblem.TooManyFields), form.problems)
    }

    @Test
    fun uploadedImageIdsRideAlongWithTheEdit() {
        val body = ProfileEdit(avatarId = "a1", bannerId = "b1").toRequest("token")

        assertEquals("a1", body.getString("avatarId"))
        assertEquals("b1", body.getString("bannerId"))
        assertFalse(ProfileEdit(avatarId = "a1").isEmpty)
        assertTrue(ProfileEdit().isEmpty)
    }

    @Test
    fun birthdaysRoundTripWithoutTimeZoneDrift() {
        val millis = Birthday.toMillis("2000-02-29")!!

        assertEquals("2000-02-29", Birthday.fromMillis(millis))
        assertNull(Birthday.toMillis("29/02/2000"))
        assertNull(Birthday.toMillis(null))
    }

    @Test
    fun theServersProfileIsParsedIntoTheForm() {
        val json = JSONObject(
            """{"id":"1","username":"me","name":null,"description":"bio","location":"X","birthday":"1999-12-31",
                "fields":[{"name":"a","value":"b"},{"name":"c","value":""}]}""",
        )

        val parsed = parseCurrentUser(json)

        assertNull(parsed.name)
        assertEquals("bio", parsed.description)
        assertEquals("1999-12-31", parsed.birthday)
        assertEquals(listOf(ProfileField("a", "b"), ProfileField("c", "")), parsed.fields)
    }
}
