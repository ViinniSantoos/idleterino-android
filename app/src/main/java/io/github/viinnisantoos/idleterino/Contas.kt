package io.github.viinnisantoos.idleterino

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * As contas do PokeIdle no celular. O login do jogo é só o `sessao` da página (nick + código de acesso). O app guarda o
 * de cada conta CRIPTOGRAFADO (Seguro: chave no Android Keystore) e nunca devolve o código para página nenhuma: para
 * trocar de conta, o próprio app coloca o `sessao` no jogo (só no endereço https://pokeidle.io) e recarrega.
 */
object Contas {
    private fun prefs(c: Context) = c.getSharedPreferences("contas", Context.MODE_PRIVATE)
    const val ITERACOES = 310000

    /** nick (minúsculo) → { nick, sessao } — guardado criptografado. */
    private fun todas(c: Context): JSONObject = try { JSONObject(Seguro.decifrar(prefs(c).getString("lista", null)) ?: "{}") } catch (_: Exception) { JSONObject() }
    private fun gravar(c: Context, l: JSONObject) { prefs(c).edit().putString("lista", Seguro.cifrar(l.toString())).apply() }
    fun ativa(c: Context): String? = prefs(c).getString("ativa", null)

    fun guardar(c: Context, sessao: String): String? {
        if (sessao.length > 4000) return null
        val s = try { JSONObject(sessao) } catch (_: Exception) { return null }
        val nick = s.optString("nick").takeIf { it.isNotBlank() && it.length <= 40 } ?: return null
        if (s.optString("token").isBlank()) return null
        val l = todas(c)
        val atual = l.optJSONObject(nick.lowercase())
        if (atual != null && atual.optString("sessao") == sessao) return nick
        l.put(nick.lowercase(), JSONObject().put("nick", nick).put("sessao", sessao))
        gravar(c, l)
        return nick
    }
    fun remover(c: Context, nick: String) { val l = todas(c); l.remove(nick.lowercase()); gravar(c, l) }
    /** Marca a conta para entrar na próxima carga da página. */
    fun usar(c: Context, nick: String): Boolean {
        val conta = todas(c).optJSONObject(nick.lowercase()) ?: return false
        prefs(c).edit().putString("ativa", conta.optString("nick")).putString("pendente", Seguro.cifrar(conta.optString("sessao"))).apply()
        return true
    }
    fun pendente(c: Context): String? = Seguro.decifrar(prefs(c).getString("pendente", null))
    fun aplicada(c: Context) { prefs(c).edit().remove("pendente").apply() }
    fun marcarAtiva(c: Context, nick: String) { prefs(c).edit().putString("ativa", nick).apply() }

    /** As contas na ordem da tela: a principal ("ativa") primeiro, depois as ALTs (no máximo 4 no total). */
    fun ordem(c: Context): List<String> {
        val l = todas(c); val a = ativa(c)?.lowercase()
        val nicks = l.keys().asSequence().map { l.getJSONObject(it).optString("nick") }.filter { it.isNotBlank() }.toMutableList()
        nicks.sortBy { if (it.lowercase() == a) 0 else 1 }
        return nicks.take(4)
    }
    /** O login de UMA conta, só para o app colocar no perfil dela (nunca vai para o painel). */
    fun sessaoDe(c: Context, nick: String): String? = todas(c).optJSONObject(nick.lowercase())?.optString("sessao")?.takeIf { it.isNotBlank() }

    /** Para o painel: só os nicks — o código de acesso nunca sai do app. */
    fun listaParaPainel(c: Context): JSONArray {
        val l = todas(c); val a = ativa(c)?.lowercase()
        val out = JSONArray()
        for (k in l.keys()) out.put(JSONObject().put("nick", l.getJSONObject(k).optString("nick")).put("ativa", k == a))
        return out
    }

    private val FORMATO = Regex("^c2\\.[A-Za-z0-9_-]{60,8000}$")
    fun pacoteValido(d: String) = FORMATO.matches(d)

    /**
     * Abre o pacote do QR do PC ("c2." + base64url(sal 16 | iv 12 | dados + etiqueta 16)) com o PIN de 8 números:
     * AES-256-GCM, chave PBKDF2-SHA256 de 310 mil voltas. Devolve as sessões, ou lança erro com a mensagem para a pessoa.
     */
    fun abrirPacote(c: Context, d: String, pin: String): List<String> {
        if (!pacoteValido(d)) throw IllegalArgumentException("Este QR não é do Idleterino (ou é de uma versão antiga). Gere outro no PC.")
        if (!Regex("^\\d{8}$").matches(pin)) throw IllegalArgumentException("O PIN tem 8 números. Confira na tela do PC.")
        val bytes = Base64.decode(d.substring(3), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        if (bytes.size < 16 + 12 + 17) throw IllegalArgumentException("QR incompleto. Gere outro no PC.")
        val sal = bytes.copyOfRange(0, 16); val iv = bytes.copyOfRange(16, 28); val corpo = bytes.copyOfRange(28, bytes.size)
        val chave = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), sal, ITERACOES, 256)).encoded
        val txt = try {
            Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, SecretKeySpec(chave, "AES"), GCMParameterSpec(128, iv)); String(doFinal(corpo), Charsets.UTF_8) }
        } catch (_: Exception) { throw IllegalArgumentException("PIN errado. Confira os 8 números na tela do PC.") }
        val j = JSONObject(txt)
        val exp = if (j.has("e")) j.optLong("e") else j.optLong("exp")
        // Os 5 minutos são controlados pelo PC (o guichê fecha sozinho). Aqui só recusa pacote velho de verdade (30 min),
        // para uma diferença de relógio entre o PC e o celular não atrapalhar.
        if (exp <= 0L || exp + 25 * 60 * 1000L < System.currentTimeMillis()) throw IllegalArgumentException("Este QR é antigo. Gere outro no PC.")
        // Chave do par PC+celular (Twitch/Kick: o celular avisa o PC pelo Wi-Fi quando está farmando aqui).
        j.optString("k").takeIf { it.isNotBlank() }?.let { Elo.guardarChave(c, it) }
        // v2 (curto): c = [[nick, código, provedor], ...] → monta o `sessao` do jogo. v1: contas = [{ sessao }].
        j.optJSONArray("c")?.let { c ->
            return (0 until minOf(c.length(), 4)).mapNotNull { i ->
                val a = c.optJSONArray(i) ?: return@mapNotNull null
                val nick = a.optString(0); val token = a.optString(1)
                if (nick.isBlank() || token.isBlank()) null
                else JSONObject().put("nick", nick).put("token", token).put("provedor", a.optString(2)).put("precisaNick", false).toString()
            }
        }
        val contas = j.optJSONArray("contas") ?: JSONArray()
        return (0 until minOf(contas.length(), 4)).mapNotNull { contas.optJSONObject(it)?.optString("sessao")?.takeIf { s -> s.isNotBlank() } }
    }
}
