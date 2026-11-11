package io.github.viinnisantoos.idleterino

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cofre dos logins: AES-256-GCM com a chave guardada no Android Keystore (no hardware de segurança do celular, quando
 * ele tem). A chave nunca sai do Keystore: o arquivo das contas, copiado para fora do app, não abre.
 */
object Seguro {
    private const val ALIAS = "idleterino-contas"

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return g.generateKey()
    }

    fun cifrar(texto: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, chave())
        val dados = c.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + dados, Base64.NO_WRAP)
    }

    fun decifrar(guardado: String?): String? {
        if (guardado.isNullOrEmpty()) return null
        return try {
            val b = Base64.decode(guardado, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
            String(c.doFinal(b.copyOfRange(12, b.size)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
}
