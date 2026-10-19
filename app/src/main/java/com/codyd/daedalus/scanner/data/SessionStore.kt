package com.codyd.daedalus.scanner.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** This phone's pairing: which API, which computer (device), and the linked token for it. */
data class Session(val apiUrl: String, val deviceId: String, val linkedId: String, val token: String)

/**
 * Keeps the session in SharedPreferences, with the token encrypted by an
 * Android Keystore key that never leaves the phone (and is excluded from
 * backups, see data_extraction_rules.xml).
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(): Session? {
        val enc = prefs.getString(KEY_TOKEN, null) ?: return null
        // The Keystore key can be lost (e.g. the lock screen was removed); pair again then.
        val token = runCatching { decrypt(enc) }.getOrElse { clear(); return null }
        return Session(
            apiUrl = prefs.getString(KEY_API, null) ?: return null,
            deviceId = prefs.getString(KEY_DEVICE, null) ?: return null,
            linkedId = prefs.getString(KEY_LINKED, null) ?: return null,
            token = token,
        )
    }

    fun save(s: Session) = prefs.edit {
        putString(KEY_API, s.apiUrl)
        putString(KEY_DEVICE, s.deviceId)
        putString(KEY_LINKED, s.linkedId)
        putString(KEY_TOKEN, encrypt(s.token))
        remove(KEY_PROFILE)
    }

    fun clear() = prefs.edit {
        remove(KEY_API); remove(KEY_DEVICE); remove(KEY_LINKED); remove(KEY_TOKEN); remove(KEY_PROFILE)
    }

    /** Profile scans go into; unset = the computer's default profile. */
    var profileId: String?
        get() = prefs.getString(KEY_PROFILE, null)
        set(v) = prefs.edit { putString(KEY_PROFILE, v) }

    /** Where the last part was put away, offered again for the next one. */
    var lastLocation: String?
        get() = prefs.getString(KEY_LOCATION, null)
        set(v) = prefs.edit { putString(KEY_LOCATION, v) }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    /** IV (12 bytes) + ciphertext, base64. */
    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val bytes = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        }
        return String(c.doFinal(bytes, 12, bytes.size - 12))
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "daedalus-session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_API = "api"
        const val KEY_DEVICE = "device"
        const val KEY_LINKED = "linked"
        const val KEY_TOKEN = "token"
        const val KEY_PROFILE = "profile"
        const val KEY_LOCATION = "location"
    }
}
