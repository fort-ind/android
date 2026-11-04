package com.fortind.zenith.auth

import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenStoreFailureTest {

    @Test
    fun aWrongOrMissingKeyIsPermanent() {
        assertTrue(isPermanentKeyFailure(AEADBadTagException()))
        assertTrue(isPermanentKeyFailure(UnrecoverableKeyException()))
        assertTrue(isPermanentKeyFailure(InvalidKeyException()))
    }

    @Test
    fun aKeystoreThatIsBusyOrBrokenForNowIsNot() {
        assertFalse(isPermanentKeyFailure(KeyStoreException("Keystore operation failed")))
        assertFalse(isPermanentKeyFailure(NoSuchAlgorithmException()))
        assertFalse(isPermanentKeyFailure(GeneralSecurityException()))
    }
}
