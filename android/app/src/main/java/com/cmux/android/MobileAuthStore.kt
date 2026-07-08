package com.cmux.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MobileAuthStore(context: Context) {
    private val preferences = context.getSharedPreferences("cmux_mobile", Context.MODE_PRIVATE)

    fun stackAccessToken(): String? {
        val encrypted = preferences.getString(KEY_STACK_ACCESS_TOKEN_ENCRYPTED, null) ?: return null
        return decrypt(encrypted)
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun saveStackAccessToken(token: String): Boolean {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return false
        preferences.edit()
            .putString(KEY_STACK_ACCESS_TOKEN_ENCRYPTED, encrypt(trimmed))
            .apply()
        return true
    }

    fun clearStackAccessToken() {
        preferences.edit()
            .remove(KEY_STACK_ACCESS_TOKEN_ENCRYPTED)
            .apply()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("v", 1)
            .put("alg", TRANSFORMATION)
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .toString()
    }

    private fun decrypt(payload: String): Result<String> = runCatching {
        val json = JSONObject(payload)
        val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_TAG_BITS = 128
        const val KEY_ALIAS = "cmux_mobile_auth"
        const val KEY_STACK_ACCESS_TOKEN_ENCRYPTED = "stack_access_token_encrypted"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
