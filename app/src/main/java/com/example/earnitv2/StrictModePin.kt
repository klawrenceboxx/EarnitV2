package com.example.earnitv2

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal interface StrictModePinStore {
    fun hasPin(): Boolean
    fun save(pin: CharArray): Boolean
    fun verify(pin: CharArray): Boolean
}

internal class SharedPreferencesStrictModePinStore(context: Context) : StrictModePinStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun hasPin(): Boolean =
        preferences.getString(KEY_SALT, null) != null && preferences.getString(KEY_HASH, null) != null

    override fun save(pin: CharArray): Boolean {
        if (!pin.isValidStrictModePin()) return false
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)?.let { protect(it, key(createIfMissing = true)) } ?: return false
        return preferences.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_ITERATIONS, ITERATIONS)
            .commit()
    }

    override fun verify(pin: CharArray): Boolean {
        if (!pin.isValidStrictModePin()) return false
        val salt = preferences.getString(KEY_SALT, null)?.decodeBase64() ?: return false
        val expected = preferences.getString(KEY_HASH, null)?.decodeBase64() ?: return false
        val iterations = preferences.getInt(KEY_ITERATIONS, ITERATIONS).takeIf { it >= MIN_ACCEPTED_ITERATIONS }
            ?: return false
        val actual = derive(pin, salt, iterations)?.let { protect(it, key(createIfMissing = false)) } ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray? {
        val specification = PBEKeySpec(pin, salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded
        } catch (_: Exception) {
            null
        } finally {
            specification.clearPassword()
        }
    }

    private fun String.decodeBase64(): ByteArray? = runCatching {
        Base64.decode(this, Base64.NO_WRAP)
    }.getOrNull()

    private fun key(createIfMissing: Boolean): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: if (createIfMissing) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE_PROVIDER).run {
                init(KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).setDigests(KeyProperties.DIGEST_SHA256).build())
                generateKey()
            }
        } else null
    }.getOrNull()

    private fun protect(derived: ByteArray, key: SecretKey?): ByteArray? {
        if (key == null) return null
        return runCatching {
            Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).run {
                init(key)
                doFinal(derived)
            }
        }.getOrNull().also { derived.fill(0) }
    }

    private companion object {
        const val PREFERENCES_NAME = "earnit_strict_mode_pin_v1"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_ITERATIONS = "iterations"
        const val SALT_BYTES = 16
        const val HASH_BITS = 256
        const val ITERATIONS = 210_000
        const val MIN_ACCEPTED_ITERATIONS = 100_000
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "earnit_strict_mode_pin_hmac_v1"
    }
}

internal fun CharArray.isValidStrictModePin(): Boolean = size in 4..8 && all(Char::isDigit)

internal sealed class PinVerificationResult {
    data class Verified(val action: PendingStrictModeAction, val countdownStarted: Boolean) : PinVerificationResult()
    data object Incorrect : PinVerificationResult()
    data class Rejected(val message: String) : PinVerificationResult()
}
