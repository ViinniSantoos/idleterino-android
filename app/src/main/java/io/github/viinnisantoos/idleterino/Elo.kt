package io.github.viinnisantoos.idleterino

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Elo com o Idleterino do PC, só pela rede de casa (Wi-Fi). Enquanto a Twitch ou a Kick está farmando aqui, o celular
 * avisa a cada minuto ("IDL1." + dados + assinatura HMAC com a chave do par, que veio no QR do PC). O PC só aceita aviso
 * com essa chave e para as lives daquela plataforma; sem aviso por 3 min (ou com "fim", ao fechar o app), volta a farmar.
 * O aviso não leva login nem nada da conta: só "twitch sim/não, kick sim/não" e a hora.
 */
object Elo {
    private const val PORTA = 47813
    private fun prefs(c: Context) = c.getSharedPreferences("elo", Context.MODE_PRIVATE)

    fun guardarChave(c: Context, k: String) {
        if (!Regex("^[A-Za-z0-9_-]{40,60}$").matches(k)) return
        prefs(c).edit().putString("k", Seguro.cifrar(k)).apply()
    }
    fun pareado(c: Context) = prefs(c).getString("k", null) != null
    private fun chave(c: Context): ByteArray? = Seguro.decifrar(prefs(c).getString("k", null))?.let {
        try { Base64.decode(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) } catch (_: Exception) { null }
    }

    /** Só no Wi-Fi (ou cabo): nos dados móveis não há PC na rede para avisar. */
    private fun naRedeDeCasa(c: Context): Boolean = try {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    } catch (_: Exception) { false }

    private fun destinos(c: Context): List<InetAddress> {
        val out = mutableListOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
        try {
            val cm = c.getSystemService(ConnectivityManager::class.java)
            cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.forEach { la ->
                val a = la.address
                if (a is Inet4Address && la.prefixLength in 8..30) {
                    val ip = a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
                    val bc = ip or ((1 shl (32 - la.prefixLength)) - 1)
                    out.add(InetAddress.getByAddress(byteArrayOf((bc ushr 24).toByte(), (bc ushr 16).toByte(), (bc ushr 8).toByte(), bc.toByte())))
                }
            }
        } catch (_: Exception) {}
        return out.distinct()
    }

    private var ultimoT = 0L
    /** Manda o aviso (numa thread). `fim` = o app está fechando: o PC volta a farmar na hora. */
    fun avisar(c: Context, twitch: Boolean, kick: Boolean, fim: Boolean = false): Thread? {
        val k = chave(c) ?: return null
        if (!naRedeDeCasa(c)) return null
        val ctx = c.applicationContext
        return thread {
            try {
                val t = maxOf(System.currentTimeMillis(), ultimoT + 1).also { ultimoT = it }
                val dados = JSONObject().put("t", t).put("tw", twitch).put("kk", kick).put("fim", fim).toString()
                val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                val corpo = Base64.encodeToString(dados.toByteArray(Charsets.UTF_8), flags)
                val mac = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(k, "HmacSHA256")); doFinal(corpo.toByteArray(Charsets.US_ASCII)) }
                val msg = "IDL1.$corpo.${Base64.encodeToString(mac, flags)}".toByteArray(Charsets.US_ASCII)
                DatagramSocket().use { s ->
                    s.broadcast = true
                    for (d in destinos(ctx)) try { s.send(DatagramPacket(msg, msg.size, d, PORTA)) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
}
