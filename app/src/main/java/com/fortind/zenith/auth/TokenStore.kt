package com.fortind.zenith.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.security.InvalidKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun isPermanentKeyFailure(error: GeneralSecurityException): Boolean = when (error) {
    is BadPaddingException, is UnrecoverableKeyException, is InvalidKeyException -> true
    else -> false
}

interface TokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

class KeystoreTokenStore(context: Context) : TokenStore {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun read(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            if (isPermanentKeyFailure(e)) clear()
            null
        } catch (_: ProviderException) {
            null
        } catch (_: IllegalArgumentException) {
            clear()
            null
        } catch (_: IndexOutOfBoundsException) {
            clear()
            null
        }
    }

    override fun write(token: String) {
        val encrypted = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            throw IOException("Could not store the session", e)
        } catch (e: ProviderException) {
            throw IOException("Could not store the session", e)
        }
        prefs.edit { putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP)) }
    }

    override fun clear() {
        prefs.edit { remove(KEY_TOKEN) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fort_session_token_key"
        const val PREFS_NAME = "fort_session"
        const val KEY_TOKEN = "token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
