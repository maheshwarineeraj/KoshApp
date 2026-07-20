package com.neeraj.fin.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption for card numbers and CVVs, keyed by a hardware-backed
 * Android Keystore key. The key never leaves the device; ciphertext in the DB
 * is useless without it. Display of decrypted values is additionally gated by
 * BiometricPrompt in the UI.
 */
object CardCrypto {

    private const val ALIAS = "kosh-card-vault"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(blob: String): String {
        if (blob.isEmpty()) return ""
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /** Luhn checksum for card-number validation. */
    fun luhnValid(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 12..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    fun network(number: String): String {
        val d = number.filter { it.isDigit() }
        return when {
            d.startsWith("4") -> "VISA"
            d.take(2).toIntOrNull() in 51..55 || d.take(4).toIntOrNull() in 2221..2720 -> "Mastercard"
            d.startsWith("34") || d.startsWith("37") -> "AMEX"
            d.startsWith("60") || d.startsWith("65") || d.startsWith("81") || d.startsWith("508") -> "RuPay"
            d.startsWith("36") || d.startsWith("30") -> "Diners"
            else -> "Card"
        }
    }
}
