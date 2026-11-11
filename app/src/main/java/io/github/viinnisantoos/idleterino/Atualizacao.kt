package io.github.viinnisantoos.idleterino

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Atualização igual à do PC: procura versão nova nas releases oficiais do GitHub, BAIXA SOZINHA e avisa; "Atualizar agora"
 * instala por cima (o Android mostra a confirmação dele). Antes de instalar, o app confere que o arquivo baixado é o
 * Idleterino com a MESMA assinatura (chave) do que está instalado — um arquivo trocado no caminho é recusado.
 */
object Atualizacao {
    private const val API = "https://api.github.com/repos/ViinniSantoos/idleterino-android/releases/latest"
    /** Plano B: baixa o arquivo direto pelo navegador (sem passar pela página do GitHub). */
    const val DIRETO = "https://github.com/ViinniSantoos/idleterino-android/releases/latest/download/Idleterino-android.apk"
    private const val ARQUIVO = "Idleterino-android.apk"
    const val ACAO = "io.github.viinnisantoos.idleterino.INSTALOU"
    private const val CONFIRMAR = "android.content.pm.action.CONFIRM_INSTALL"

    @Volatile var nova: String? = null
    @Volatile private var url: String? = null
    @Volatile var progresso = -1      // -1 = nada; 0..99 = baixando; 100 = pronta para instalar
    @Volatile var erro: String? = null
    @Volatile private var sessao = -1
    private var avisouPronta = false

    fun estado(): JSONObject = JSONObject().put("versao", nova ?: JSONObject.NULL).put("progresso", progresso).put("erro", erro ?: JSONObject.NULL)

    fun baixarPeloNavegador(a: Activity) { try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DIRETO))) } catch (_: Exception) {} }

    private fun arquivo(a: Activity) = File(File(a.cacheDir, "atualizacao").apply { mkdirs() }, "nova.apk")

    fun verificar(a: Activity, avisarSeNada: Boolean = false) {
        thread {
            try {
                val c = URL(API).openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 8000; c.readTimeout = 8000
                if (c.responseCode != 200) { if (avisarSeNada) a.runOnUiThread { aviso(a, "Não consegui ver se há versão nova agora.") }; return@thread }
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = j.optString("tag_name").removePrefix("v")
                val atual = a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "0"
                val assets = j.optJSONArray("assets")
                val link = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }.firstOrNull { it.optString("name") == ARQUIVO }?.optString("browser_download_url")
                if (maisNova(tag, atual) && link != null && link.startsWith("https://github.com/ViinniSantoos/idleterino-android/")) {
                    nova = tag; url = link
                    baixar(a)
                } else {
                    try { arquivo(a).delete() } catch (_: Exception) {}
                    if (avisarSeNada) a.runOnUiThread { aviso(a, "Você já está na versão mais nova ($atual).") }
                }
            } catch (_: Exception) { if (avisarSeNada) a.runOnUiThread { aviso(a, "Sem internet para ver atualizações.") } }
        }
    }

    /** Baixa em segundo plano (são uns 2 MB). Quando termina, avisa como no PC. */
    private fun baixar(a: Activity) {
        val u = url ?: return
        if (progresso in 0..100) { if (progresso == 100) avisarPronta(a); return }
        progresso = 0; erro = null
        try {
            val f = arquivo(a)
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "Idleterino-Android")
            if (c.responseCode != 200) throw Exception("o GitHub respondeu ${c.responseCode}")
            val total = c.contentLengthLong
            var feito = 0L
            c.inputStream.use { inp -> f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); feito += n; if (total > 0) progresso = minOf(99, (feito * 100 / total).toInt()) }
            } }
            if (f.length() < 100_000) { f.delete(); throw Exception("o arquivo veio incompleto") }
            if (!eDoIdleterino(a, f)) { f.delete(); throw Exception("o arquivo baixado não é o Idleterino oficial") }
            progresso = 100
            avisarPronta(a)
        } catch (e: Exception) { progresso = -1; erro = (e.message ?: e.javaClass.simpleName).take(160) }
    }

    private fun avisarPronta(a: Activity) {
        if (avisouPronta) return
        avisouPronta = true
        Avisos.mostrar(a, "Atualização pronta", "O Idleterino ${nova ?: ""} está pronto. Toque em \"Atualizar agora\" no painel (botão I) ou aqui.")
        a.runOnUiThread {
            if (a.isFinishing) return@runOnUiThread
            AlertDialog.Builder(a).setTitle("Idleterino ${nova ?: ""} pronto")
                .setMessage("A versão nova já foi baixada. Atualizar agora? O app fecha e abre de novo na versão nova; suas contas continuam salvas.")
                .setPositiveButton("Atualizar agora") { _, _ -> instalar(a) }
                .setNegativeButton("Depois", null).show()
        }
    }

    private fun assinaturas(pm: PackageManager, info: android.content.pm.PackageInfo?): Set<Signature> {
        if (info == null) return emptySet()
        return if (Build.VERSION.SDK_INT >= 28) (info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }?.toSet() ?: emptySet())
        else @Suppress("DEPRECATION") (info.signatures?.toSet() ?: emptySet())
    }
    private fun eDoIdleterino(a: Activity, f: File): Boolean {
        val pm = a.packageManager
        val flag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val novo = pm.getPackageArchiveInfo(f.path, flag) ?: return false
        if (novo.packageName != a.packageName) return false
        val sNovo = try { assinaturas(pm, novo) } catch (_: Exception) { emptySet() }
        val sAtual = try { assinaturas(pm, pm.getPackageInfo(a.packageName, flag)) } catch (_: Exception) { emptySet() }
        // Sem ler a assinatura do arquivo (alguns Android não leem a de um APK solto), quem garante é o próprio instalador
        // do Android: ele RECUSA atualizar com um arquivo de outra chave. Lendo, tem que bater aqui também.
        if (sNovo.isEmpty() || sAtual.isEmpty()) return true
        return sNovo.any { it in sAtual }
    }

    /** "Atualizar agora": instala o arquivo baixado por cima (o Android pede a confirmação). */
    fun instalar(a: Activity) {
        val f = arquivo(a)
        if (progresso != 100 || !f.exists()) {
            if (nova != null && progresso < 0) { progresso = 0; erro = null; thread { progresso = -1; baixar(a) } } else if (nova == null) baixarPeloNavegador(a)
            return
        }
        thread {
            try {
                if (!eDoIdleterino(a, f)) throw Exception("O arquivo baixado não é o Idleterino oficial.")
                val pi = a.packageManager.packageInstaller
                val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                p.setAppPackageName(a.packageName)
                val id = pi.createSession(p)
                sessao = id
                pi.openSession(id).use { s ->
                    s.openWrite("idleterino", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                    val volta = Intent(a, MainActivity::class.java).setAction(ACAO)
                    val pend = PendingIntent.getActivity(a, 3, volta, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    s.commit(pend.intentSender)
                }
            } catch (e: Exception) { erro = (e.message ?: e.javaClass.simpleName).take(160); a.runOnUiThread { Toast.makeText(a, "Não deu para atualizar: ${e.message}", Toast.LENGTH_LONG).show() } }
        }
    }

    /** Resposta do instalador do Android (só a da NOSSA sessão; e só abre a tela de confirmação dele). */
    fun resultado(a: Activity, i: Intent) {
        if (sessao < 0 || i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -2) != sessao) return
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val conf = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (conf != null && conf.action == CONFIRMAR) try { a.startActivity(conf) } catch (_: Exception) {}
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> {
                sessao = -1
                val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(a, "A atualização não foi instalada. $msg".trim(), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun aviso(a: Activity, t: String) { AlertDialog.Builder(a).setMessage(t).setPositiveButton("Ok", null).show() }
    private fun maisNova(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }; val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }
}
