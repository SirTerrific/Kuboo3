package com.sethchhim.kuboo_client.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the saved server list, which contains the password used for HTTP Basic auth.
 *
 * The key lives in the AndroidKeyStore and never leaves the device, so a copy of the
 * preferences file — from a cloud backup, a device transfer, or an unlocked bootloader —
 * is useless on its own.
 *
 * Values written before this existed are plain JSON. [decrypt] passes anything without the
 * [PREFIX] straight through, so an existing install keeps working and is re-encrypted the
 * next time the server list is saved.
 */
object CredentialCipher {

    private const val PREFIX = "enc1:"
    private const val KEY_ALIAS = "kuboo_credentials"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH = 12

    fun encrypt(plainText: String): String = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    } catch (e: Exception) {
        // Storing the credentials in the clear is worse than not storing them, but losing
        // the user's server list on an unexpected keystore failure is worse still.
        Timber.e("Failed to encrypt credentials, storing as plain text: ${e.message}")
        plainText
    }

    /**
     * Returns the plain text, "" when the stored value can never be read again, or null when
     * the keystore failed for now and a later try may work. The caller must not overwrite the
     * stored value after a null.
     *
     * "Never again" is the key being gone (restored on another device, cleared by a lock-screen
     * reset): the tag check fails, or the key is invalidated. Starting empty is then better than
     * handing back ciphertext that would be parsed as a server address.
     */
    fun decrypt(storedValue: String): String? {
        if (!storedValue.startsWith(PREFIX)) return storedValue
        return try {
            val bytes = Base64.decode(storedValue.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, IV_LENGTH))
            }
            String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
        } catch (e: Exception) {
            if (e.isKeyLost()) {
                Timber.e("Stored servers cannot be decrypted, discarding them: ${e.message}")
                ""
            } else {
                Timber.e("Keystore failed, keeping stored servers for a later try: ${e.message}")
                null
            }
        }
    }

    private fun Exception.isKeyLost() = this is AEADBadTagException
            || this is KeyPermanentlyInvalidatedException
            || this is UnrecoverableKeyException
            || this is IllegalArgumentException // not valid base64 or too short: corrupt value

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
        }.generateKey()
    }
}
