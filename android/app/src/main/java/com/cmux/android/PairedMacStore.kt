package com.cmux.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PairedMacStore(context: Context) {
    private val preferences = context.getSharedPreferences("cmux_mobile", Context.MODE_PRIVATE)

    fun list(): List<PairedMac> {
        val encrypted = preferences.getString(KEY_PAIRED_MACS_ENCRYPTED, null)
        if (encrypted != null) {
            return parseMacs(decrypt(encrypted).getOrElse { "[]" })
        }

        val raw = preferences.getString(KEY_PAIRED_MACS, "[]") ?: "[]"
        val macs = parseMacs(raw)
        if (macs.isNotEmpty()) {
            write(macs)
        }
        return macs
    }

    private fun parseMacs(raw: String): List<PairedMac> {
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                PairedMac.fromJson(array.getJSONObject(index))
            }.getOrNull()
        }
    }

    fun save(mac: PairedMac): List<PairedMac> {
        val updated = list()
            .filterNot { it.id == mac.id }
            .toMutableList()
            .apply { add(0, mac) }
        write(updated)
        return updated
    }

    fun forget(id: String): List<PairedMac> {
        val updated = list().filterNot { it.id == id }
        write(updated)
        return updated
    }

    private fun write(macs: List<PairedMac>) {
        val array = JSONArray()
        macs.forEach { array.put(it.toJson()) }
        preferences.edit()
            .putString(KEY_PAIRED_MACS_ENCRYPTED, encrypt(array.toString()))
            .remove(KEY_PAIRED_MACS)
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
        const val KEY_ALIAS = "cmux_mobile_paired_macs"
        const val KEY_PAIRED_MACS = "paired_macs"
        const val KEY_PAIRED_MACS_ENCRYPTED = "paired_macs_encrypted"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
