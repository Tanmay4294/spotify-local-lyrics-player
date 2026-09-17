package com.example.spotlyrics.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "spotlyrics_secure_token_prefs"
        private const val KEY_ALIAS = "SpotifyTokenSecretKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ENCRYPTED_TOKEN = "encrypted_token"
        private const val KEY_TOKEN_IV = "token_iv"
        private const val KEY_CODE_VERIFIER = "code_verifier"

        @Volatile
        private var instance: SecureTokenStorage? = null

        fun getInstance(context: Context): SecureTokenStorage {
            return instance ?: synchronized(this) {
                instance ?: SecureTokenStorage(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    fun save(token: SpotifyToken) {
        val json = gson.toJson(token)
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)

        prefs.edit()
            .putString(KEY_ENCRYPTED_TOKEN, encryptedBase64)
            .putString(KEY_TOKEN_IV, ivBase64)
            .apply()
    }

    fun get(): SpotifyToken? {
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
        val ivBase64 = prefs.getString(KEY_TOKEN_IV, null) ?: return null

        return try {
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val json = String(decryptedBytes, Charsets.UTF_8)
            gson.fromJson(json, SpotifyToken::class.java)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_TOKEN)
            .remove(KEY_TOKEN_IV)
            .apply()
    }

    fun saveCodeVerifier(verifier: String) {
        prefs.edit().putString(KEY_CODE_VERIFIER, verifier).apply()
    }

    fun getCodeVerifier(): String? {
        return prefs.getString(KEY_CODE_VERIFIER, null)
    }

    fun clearCodeVerifier() {
        prefs.edit().remove(KEY_CODE_VERIFIER).apply()
    }
}
