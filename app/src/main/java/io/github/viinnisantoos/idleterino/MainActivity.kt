package io.github.viinnisantoos.idleterino

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Idleterino no celular: as contas do PokeIdle, cada uma num navegador interno com PERFIL PRÓPRIO (login e armazenamento
 * separados), rodando AO MESMO TEMPO. Aba "Principal" = a conta principal em tela cheia; aba "ALTs" = as outras (até 3),
 * empilhadas, no modo ULTRA do jogo (leve). Em cada conta, o mesmo núcleo do Idleterino do PC (nucleo.js) e o painel do
 * celular (painel.js) entram ANTES do jogo carregar.
 *
 * SEGURANÇA (o celular tem banco, WhatsApp...):
 *  · o app só pede internet, notificações e manter o jogo conectado — nada de contatos, SMS, arquivos, câmera ou microfone;
 *  · a ponte painel ↔ app só aceita a página https://pokeidle.io no quadro principal (addWebMessageListener);
 *  · o código de acesso das contas NUNCA volta para a página: o app coloca o login de cada conta no perfil dela por um
 *    script que só roda em https://pokeidle.io;
 *  · os logins ficam criptografados com chave do Android Keystore (Seguro.kt);
 *  · a WebView não abre arquivos, não usa câmera/microfone/localização, não baixa nada, usa o Safe Browsing e abre fora tudo
 *    o que não for o jogo; sem backup dos dados do app; a tela do PIN não deixa tirar print.
 */
class MainActivity : Activity() {
    private val jogo = "https://pokeidle.io/app"
    private val ORIGEM = setOf("https://pokeidle.io")
    private val multiPerfil get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private class Conta(val nick: String, val web: WebView, val alt: Boolean) { var celula: View? = null; var script: ScriptHandler? = null }
    private val contas = mutableListOf<Conta>()
    private lateinit var area: FrameLayout
    private lateinit var tabPrincipal: Button
    private lateinit var tabAlts: Button
    private lateinit var tabTwitch: Button
    private lateinit var tabKick: Button
    // Lives da Twitch/Kick tocam aqui, ATRÁS das abas (escondidas, mas "na tela" para o vídeo andar).
    private lateinit var fundo: FrameLayout
    private val plataformas = mutableListOf<Plataforma>()
    private var reconstruindo = false
    private val gradeAlts by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#0B0A16")) } }
    private var aba = "principal"
    private var ampliada: Conta? = null
    // Rede: conta que não carregou tenta de novo sozinha (5 s, 10 s, 20 s… até 60 s) e recarrega quando a internet volta.
    private val laco = Handler(Looper.getMainLooper())
    private val falhas = mutableMapOf<WebView, Int>()
    private var redeCb: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Avisos.criarCanais(this)
        WebView.setWebContentsDebuggingEnabled(false)
        montarTela()
        montarContas()
        vigiarRede()
        pedirPermissaoDeAviso()
        Atualizacao.verificar(this)
        importarSeVeio(intent)
    }

    // ---------------- tela: área das contas + abas embaixo ----------------
    private fun montarTela() {
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#0B0A16")) }
        val palco = FrameLayout(this)
        fundo = FrameLayout(this)
        area = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0A16")) }
        val cheio = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        palco.addView(fundo, FrameLayout.LayoutParams(cheio))
        palco.addView(area, FrameLayout.LayoutParams(cheio))
        for (ns in listOf("twitch", "kick")) {
            val pl = Plataforma(this, ns, fundo)
            plataformas.add(pl)
            pl.tela.visibility = View.GONE
            palco.addView(pl.tela, FrameLayout.LayoutParams(cheio))
        }
        raiz.addView(palco, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val barra = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#141024")) }
        fun botao(t: String, f: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setTextColor(Color.WHITE); textSize = 13f; setBackgroundColor(Color.TRANSPARENT); setPadding(0, 0, 0, 0); setOnClickListener { f() }
        }
        tabPrincipal = botao("⭐ Principal") { mostrar("principal") }
        tabAlts = botao("👥 ALTs") { mostrar("alts") }
        barra.addView(tabPrincipal, LinearLayout.LayoutParams(0, dp(48), 1f))
        barra.addView(tabAlts, LinearLayout.LayoutParams(0, dp(48), 1f))
        tabTwitch = botao("🟣 Twitch") { mostrar("twitch") }
        tabKick = botao("🟢 Kick") { mostrar("kick") }
        barra.addView(tabTwitch, LinearLayout.LayoutParams(0, dp(48), 1f))
        barra.addView(tabKick, LinearLayout.LayoutParams(0, dp(48), 1f))
        raiz.addView(barra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(raiz)
        plataformas.forEach { it.iniciar() }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun montarContas() {
        laco.removeCallbacksAndMessages(null); falhas.clear()
        for (c in contas) { c.script?.remove(); (c.web.parent as? ViewGroup)?.removeView(c.web); c.web.destroy() }
        contas.clear(); area.removeAllViews(); gradeAlts.removeAllViews(); ampliada = null
        val nicks = Contas.ordem(this)
        if (!multiPerfil || nicks.isEmpty()) {
            // Uma conta por vez (celular sem perfis no navegador interno, ou ainda sem contas guardadas).
            val w = criarWeb(null, nicks.firstOrNull(), false)
            contas.add(Conta(nicks.firstOrNull() ?: "", w, false))
            area.addView(w)
            if (!multiPerfil && nicks.size > 1) Toast.makeText(this, "Atualize o \"Android System WebView\" na Play Store para usar as contas juntas.", Toast.LENGTH_LONG).show()
        } else {
            nicks.forEachIndexed { i, nick ->
                val alt = i > 0
                val w = criarWeb("conta-${nick.lowercase()}", nick, alt)
                val c = Conta(nick, w, alt)
                contas.add(c)
                if (!alt) area.addView(w) else gradeAlts.addView(celulaAlt(c), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            if (nicks.size > 1) area.addView(gradeAlts)
        }
        tabAlts.visibility = if (contas.size > 1) View.VISIBLE else View.GONE
        mostrar(if (contas.size > 1 || aba != "alts") aba else "principal")
        // A principal abre na hora; as ALTs logo depois, uma a cada 1,5 s (não pede tudo de uma vez).
        contas.forEachIndexed { i, c -> laco.postDelayed({ if (contas.contains(c)) c.web.loadUrl(jogo) }, i * 1500L) }
    }

    /** Cada ALT: uma faixa com o nick e "Ampliar", e o jogo embaixo. */
    private fun celulaAlt(c: Conta): View {
        val cel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val topo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.parseColor("#1C1733")); setPadding(dp(10), 0, 0, 0) }
        topo.addView(TextView(this).apply { text = c.nick; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; textSize = 13f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val amp = Button(this).apply { text = "Ampliar"; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT) }
        amp.setOnClickListener { alternarAmpliar(c, amp) }
        topo.addView(amp, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        cel.addView(topo)
        cel.addView(c.web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        c.celula = cel
        return cel
    }
    private fun alternarAmpliar(c: Conta, b: Button) {
        ampliada = if (ampliada == c) null else c
        for (o in contas) if (o.alt) o.celula?.visibility = if (ampliada == null || ampliada == o) View.VISIBLE else View.GONE
        b.text = if (ampliada == c) "Ver as 3" else "Ampliar"
    }

    private fun mostrar(qual: String) {
        aba = qual
        val jogoNaTela = qual == "principal" || qual == "alts"
        area.visibility = if (jogoNaTela) View.VISIBLE else View.INVISIBLE
        contas.firstOrNull()?.web?.visibility = if (qual == "principal") View.VISIBLE else View.GONE
        gradeAlts.visibility = if (qual == "alts") View.VISIBLE else View.GONE
        for (p in plataformas) { val on = qual == p.ns; p.tela.visibility = if (on) View.VISIBLE else View.GONE; if (on) p.aoVoltarParaATela() }
        tabPrincipal.alpha = if (qual == "principal") 1f else .55f
        tabAlts.alpha = if (qual == "alts") 1f else .55f
        tabTwitch.alpha = if (qual == "twitch") 1f else .55f
        tabKick.alpha = if (qual == "kick") 1f else .55f
    }

    // ---------------- cada conta: um navegador interno fechado, com o perfil dela ----------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun criarWeb(perfil: String?, nick: String?, alt: Boolean): WebView {
        val web = WebView(this)
        if (perfil != null) { ProfileStore.getInstance().getOrCreateProfile(perfil); WebViewCompat.setProfile(web, perfil) }
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(false)
        s.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) WebSettingsCompat.setSafeBrowsingEnabled(s, true)
        s.userAgentString = s.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.setDownloadListener { _, _, _, _, _ -> }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean { cb.onReceiveValue(null); return true }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                if (u.scheme != "https") return true
                if (dentroDoApp(u.host ?: "")) return false
                abrirFora(u.toString()); return true
            }
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) tentarDeNovo(view)
            }
            // O navegador interno caiu (falta de memória): em vez de o app fechar, abre as contas de novo.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (!reconstruindo) { reconstruindo = true; laco.postDelayed({ reconstruindo = false; montarContas() }, 1500) }
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                val noJogo = Uri.parse(url).host?.let { it == "pokeidle.io" || it.endsWith(".pokeidle.io") } == true
                // Negativo = tentando de novo; se chegou aqui sem erro (o erro vem antes), carregou: zera as tentativas.
                if (noJogo && (falhas[view] ?: 0) < 0) falhas.remove(view)
                if (noJogo && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    Contas.pendente(this@MainActivity)?.let { ses -> Contas.aplicada(this@MainActivity); view.evaluateJavascript("localStorage.setItem('sessao', ${JSONObject.quote(ses)});", null); view.reload(); return }
                    view.evaluateJavascript(lerAsset("nucleo.js"), null); view.evaluateJavascript(lerAsset("painel.js"), null)
                }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // 1) o login desta conta (só se o perfil está sem login ou com outra conta); 2) ALT no modo ULTRA (leve).
            val ses = if (perfil != null && nick != null) Contas.sessaoDe(this, nick) else Contas.pendente(this)?.also { Contas.aplicada(this) }
            val partes = StringBuilder("try{")
            if (ses != null) partes.append("var s=${JSONObject.quote(ses)};var a=localStorage.getItem('sessao');var n=JSON.parse(s).nick;var an=null;try{an=a?JSON.parse(a).nick:null}catch(e){}if(!a||an!==n)localStorage.setItem('sessao',s);")
            if (alt) partes.append("localStorage.setItem('cfg-ultra','1');localStorage.setItem('cfg-economia','0');localStorage.setItem('cfg-otimizado','0');")
            partes.append("}catch(e){}")
            WebViewCompat.addDocumentStartJavaScript(web, partes.toString(), ORIGEM)
            WebViewCompat.addDocumentStartJavaScript(web, lerAsset("nucleo.js"), ORIGEM)
            WebViewCompat.addDocumentStartJavaScript(web, lerAsset("painel.js"), ORIGEM)
        }
        ligarPonte(web, nick ?: "")
        return web
    }

    private fun tentarDeNovo(web: WebView) {
        val n = kotlin.math.abs(falhas[web] ?: 0) + 1
        falhas[web] = n
        val espera = minOf(60_000L, 5_000L * (1L shl minOf(n - 1, 4)))
        laco.postDelayed({ if (contas.any { it.web == web }) { falhas[web] = -n; web.loadUrl(jogo) } }, espera)
    }
    private fun vigiarRede() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                laco.post { for ((w, n) in falhas.toMap()) if (n > 0 && contas.any { it.web == w }) { falhas[w] = -n; w.loadUrl(jogo) } }
            }
        }
        try { cm.registerDefaultNetworkCallback(cb); redeCb = cb } catch (_: Exception) {}
    }

    private fun dentroDoApp(h: String) = h == "pokeidle.io" || h.endsWith(".pokeidle.io") ||
        h == "accounts.google.com" || h.endsWith(".googleusercontent.com") || h == "www.gstatic.com" || h == "discord.com"

    private fun lerAsset(nome: String) = assets.open(nome).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun abrirFora(url: String) {
        if (!url.startsWith("https://")) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) } catch (_: Exception) {}
    }

    // ---------------- ponte com o painel de cada conta: só https://pokeidle.io, só no quadro principal ----------------
    private fun ligarPonte(web: WebView, nickDaView: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(web, "IdleterinoApp", ORIGEM) { _: WebView, msg: WebMessageCompat, origem: Uri, principal: Boolean, resposta: JavaScriptReplyProxy ->
            if (!principal || origem.host != "pokeidle.io") return@addWebMessageListener
            val j = try { JSONObject(msg.data ?: return@addWebMessageListener) } catch (_: Exception) { return@addWebMessageListener }
            val d = j.optJSONObject("dados") ?: JSONObject()
            val valor: Any? = when (j.optString("acao")) {
                "versao" -> packageManager.getPackageInfo(packageName, 0).versionName
                "aviso" -> { val quem = nickDaView.takeIf { it.isNotBlank() && contas.size > 1 }?.let { "$it · " } ?: ""; Avisos.mostrar(this, (quem + d.optString("titulo")).take(80), d.optString("texto").take(300)); true }
                "farmLigado" -> telaDesligada
                "farm" -> { telaDesligada = d.optBoolean("ligar"); FarmService.ligar(this, telaDesligada); if (telaDesligada) pedirSemEconomiaDeBateria(); true }
                "atualizacao" -> Atualizacao.estado()
                "atualizarAgora" -> { Atualizacao.instalar(this); true }
                "copiar" -> { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Idleterino", d.optString("texto").take(500))); true }
                "abrir" -> { abrirFora(d.optString("url")); true }
                "verificarAtualizacao" -> { Atualizacao.verificar(this, true); true }
                "lembrarSessao" -> { Contas.guardar(this, d.optString("sessao")); true }
                "contas" -> Contas.listaParaPainel(this)
                "trocarConta" -> { Contas.marcarAtiva(this, d.optString("nick")); if (!multiPerfil) Contas.usar(this, d.optString("nick")); montarContas(); true }
                "removerConta" -> { Contas.remover(this, d.optString("nick")); true }
                "novaConta" -> { web.evaluateJavascript("localStorage.removeItem('sessao');", null); web.loadUrl(jogo); true }
                "multi" -> multiPerfil
                "souPrincipal" -> contas.firstOrNull()?.web == web
                else -> null
            }
            resposta.postMessage(JSONObject().put("id", j.optInt("id")).put("valor", valor ?: JSONObject.NULL).toString())
        }
    }

    // ---------------- "Levar para o celular": o QR do PC chega aqui ----------------
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Atualizacao.ACAO) { Atualizacao.resultado(this, intent); return }
        importarSeVeio(intent)
    }

    private fun importarSeVeio(i: Intent?) {
        val d = i?.getStringExtra("d") ?: return
        i.removeExtra("d")
        if (!Contas.pacoteValido(d)) { aviso("Este link não é um QR válido do Idleterino. Gere outro no PC."); return }
        val campo = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "PIN de 8 números" }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Trazer as contas do PC")
            .setMessage("Digite o PIN de 8 números que aparece na tela do PC. Só faça isso com o SEU PC, na sua frente.")
            .setView(campo)
            .setPositiveButton("Entrar") { _, _ ->
                val pin = campo.text.toString().filter { it.isDigit() }
                thread {
                    try {
                        val nicks = Contas.abrirPacote(d, pin).mapNotNull { Contas.guardar(this, it) }
                        runOnUiThread {
                            if (nicks.isEmpty()) { aviso("Nenhuma conta veio no QR."); return@runOnUiThread }
                            if (Contas.ativa(this) == null || Contas.ordem(this).none { it.equals(Contas.ativa(this), true) }) Contas.marcarAtiva(this, nicks.first())
                            if (!multiPerfil) Contas.usar(this, Contas.ativa(this) ?: nicks.first())
                            Toast.makeText(this, "${nicks.size} conta(s) trazidas: ${nicks.joinToString(", ")}", Toast.LENGTH_LONG).show()
                            montarContas()
                        }
                    } catch (e: Exception) { runOnUiThread { aviso(e.message ?: "Não deu para abrir o QR.") } }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dlg.show()
    }

    private fun aviso(t: String) { AlertDialog.Builder(this).setMessage(t).setPositiveButton("Ok", null).show() }

    private fun pedirPermissaoDeAviso() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** "Tela desligada" = o modo farm de antes (segura o processador com a tela apagada). */
    private var telaDesligada: Boolean
        get() = getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("telaDesligada", false)
        set(v) { getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("telaDesligada", v).apply() }

    override fun onStart() {
        super.onStart()
        // Enquanto o app estiver aberto (mesmo indo para outro app), tudo segue farmando: o serviço com a notificação fixa
        // impede o Android de congelar o app. Fechar o app (tirar dos recentes) para tudo.
        FarmService.ligar(this, telaDesligada)
    }

    override fun onPause() {
        super.onPause()
        // Não pausa nada: o jogo, as ALTs e as lives seguem rodando quando a pessoa troca de app.
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        contas.forEach { it.web.onResume() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        plataformas.firstOrNull { it.ns == aba }?.let { if (!it.voltar()) mostrar("principal"); return }
        val atual = if (aba == "alts") (ampliada ?: contas.getOrNull(1)) else contas.firstOrNull()
        when {
            aba == "alts" && ampliada != null -> { val c = ampliada!!; alternarAmpliar(c, (c.celula as? ViewGroup)?.let { ((it.getChildAt(0) as ViewGroup).getChildAt(1) as Button) } ?: return) }
            atual != null && atual.web.canGoBack() -> atual.web.goBack()
            aba == "alts" -> mostrar("principal")
            else -> moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        if (isFinishing) FarmService.desligar(this)
        laco.removeCallbacksAndMessages(null)
        redeCb?.let { try { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        plataformas.forEach { it.parar(!FarmService.ligado) }
        if (!FarmService.ligado) contas.forEach { it.web.destroy() }
        super.onDestroy()
    }

    /** Sem isso, vários celulares "dormem" o app com a tela desligada mesmo com o serviço. */
    private fun pedirSemEconomiaDeBateria() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        AlertDialog.Builder(this)
            .setTitle("Deixar o Idleterino rodar com a tela desligada")
            .setMessage("Para o jogo não desconectar no bolso, permita que o Idleterino ignore a economia de bateria.")
            .setPositiveButton("Permitir") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
            }
            .setNegativeButton("Agora não", null)
            .show()
    }
}
