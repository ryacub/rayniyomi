package eu.kanade.tachiyomi.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed secure storage using AES-256-GCM encryption.
 * Encrypted values are stored in a private SharedPreferences file.
 * Format: Base64(IV) + ":" + Base64(ciphertext)
 */
internal class KeystoreSecureStorage(
    context: Context,
    prefsName: String = "rayniyomi_secure",
) : SecureStorage {

    companion object {
        private const val KEY_ALIAS = "rayniyomi_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(spec)
                generateKey()
            }
        }

        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    override fun getString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val colonIndex = stored.indexOf(':')
            if (colonIndex == -1) return null
            val iv = Base64.decode(stored.substring(0, colonIndex), Base64.NO_WRAP)
            val ciphertext = Base64.decode(stored.substring(colonIndex + 1), Base64.NO_WRAP)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv.copyOf()))
            String(cipher.doFinal(ciphertext))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to decrypt secure pref key: $key" }
            null
        }
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)

        prefs.edit().putString(key, "$iv:$ciphertext").apply()
    }
}
