package io.github.viinnisantoos.idleterino

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Twitch e Kick no celular — o mesmo jeito do PC (Twitchterino/Kickterino dentro do Idleterino):
 *  · login da pessoa num navegador interno com PERFIL PRÓPRIO (o da Twitch não enxerga o do jogo nem o da Kick);
 *  · uma página "vazia" (robots.txt) do próprio site lê canais e pontos, como o site faz;
 *  · até 2 lives oficiais tocando escondidas, SEM SOM e na menor qualidade (160p) — é isso que conta como "assistindo";
 *  · só UMA conta por plataforma (regra da Kick) e NENHUMA compra automática: a loja abre e quem resgata é a pessoa;
 *  · o baú de bônus da Twitch o app pega sozinho (igual ao PC; não gasta pontos), dá para desligar;
 *  · por padrão só no Wi-Fi, para não gastar o plano de dados.
 */
class Plataforma(private val a: MainActivity, val ns: String, private val fundo: FrameLayout) {
    val twitch = ns == "twitch"
    val nome = if (twitch) "Twitch" else "Kick"
    private val base = if (twitch) "https://www.twitch.tv" else "https://kick.com"
    private val dominio = if (twitch) "twitch.tv" else "kick.com"
    private val leve = "$base/robots.txt"
    private val cor = Color.parseColor(if (twitch) "#A970FF" else "#53FC18")
    private val perfil: String? = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) "plat-$ns" else null
    private val prefs = a.getSharedPreferences("plat-$ns", Context.MODE_PRIVATE)
    private val laco = Handler(Looper.getMainLooper())
    private val MAX = 2

    private val padrao = if (twitch) listOf(
        "clovaobengala", "dersin2k", "mecmeno", "guaxilol1", "denissnidertv", "faturetosl", "patopapao", "axtlol",
        "matta", "rodil", "biscoitao", "cogu", "tftoddy", "luccatv", "techzz", "revolta", "yetz",
    ) else listOf(
        "yetz", "biscoitao", "surskity", "tftoddy", "techzz", "clovaobengalalol", "axt", "yulla", "faturetosl",
        "cogu", "rodil", "patopapao2", "matta", "luccatv", "guaxilol1", "dersin2k", "mecmeno",
    )
    private val parceiros = "https://viinnisantoos.github.io/${if (twitch) "twitchterino" else "kickterino"}/parceiros.json"
    private val slugOk = if (twitch) Regex("^[a-z0-9_]{2,25}$") else Regex("^[a-z0-9_-]{2,30}$")

    private class Estado {
        var nome = ""; var live = false; var viewers = 0; var pontos: Long? = null; var ganho = 0L
        var bau = false; var erro: String? = null; var desde = 0L; var vistoEm = 0L; var ultimoGanho = 0L
    }
    private val estado = HashMap<String, Estado>()
    private fun est(s: String) = estado.getOrPut(s) { Estado() }
    private val vigias = LinkedHashMap<String, WebView>()
    private var usuario: String? = null
    private var logado = false
    private var api: WebView? = null
    private var apiPronto = false
    private val pendentes = HashMap<Int, (Int, String) -> Unit>()
    private var seq = 0
    private var lendo = false
    private var bauAvisadoEm = 0L

    val tela = FrameLayout(a)
    private val lista = LinearLayout(a)
    private val rolagem = ScrollView(a)
    private val siteBox = LinearLayout(a)
    private val siteTitulo = TextView(a)
    private var site: WebView? = null

    // ---------------- ajustes guardados ----------------
    private fun canais(): MutableList<String> {
        val s = prefs.getString("canais", null) ?: return padrao.toMutableList()
        return try { val j = JSONArray(s); MutableList(j.length()) { j.getString(it) } } catch (_: Exception) { padrao.toMutableList() }
    }
    private fun gravarCanais(l: List<String>) { prefs.edit().putString("canais", JSONArray(l.distinct()).toString()).apply() }
    private var ligado: Boolean
        get() = prefs.getBoolean("ligado", true)
        set(v) { prefs.edit().putBoolean("ligado", v).apply() }
    private var autoBau: Boolean
        get() = prefs.getBoolean("autoBau", true)
        set(v) { prefs.edit().putBoolean("autoBau", v).apply() }
    private fun bausHoje() = if (prefs.getString("diaBau", "") == hoje()) prefs.getInt("baus", 0) else 0
    private var soWifi: Boolean
        get() = prefs.getBoolean("soWifi", true)
        set(v) { prefs.edit().putBoolean("soWifi", v).apply() }
    private fun hoje() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun ganhoHoje() = if (prefs.getString("dia", "") == hoje()) prefs.getLong("hoje", 0) else 0L
    private fun somarHoje(n: Long) { val d = hoje(); prefs.edit().putString("dia", d).putLong("hoje", ganhoHoje() + n).apply() }
    private fun medido() = try { a.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false } catch (_: Exception) { false }

    // ---------------- ciclo ----------------
    fun iniciar() {
        montarTela()
        criarApi()
        buscarParceiros()
        laco.postDelayed(ciclo, 90_000L)
    }
    private val ciclo = object : Runnable { override fun run() { ler(); laco.postDelayed(this, 90_000L) } }

    /** tudo = false (modo farm ligado e o app fechando): as lives seguem tocando. */
    fun parar(tudo: Boolean) {
        laco.removeCallbacksAndMessages(null)
        if (!tudo) return
        vigias.values.forEach { destruir(it) }; vigias.clear()
        bauWeb?.let { destruir(it) }; bauWeb = null; filaBau.clear(); bauOcupado = null
        api?.let { destruir(it) }; api = null
        site?.let { destruir(it) }; site = null
    }

    /** Farmando aqui agora (o PC, avisado pelo Wi-Fi, para a dele). */
    fun farmandoAqui() = ligado && logado && !(soWifi && medido())

    fun aoVoltarParaATela() { if (siteBox.visibility != View.VISIBLE) desenhar() }

    // ---------------- navegador interno da plataforma ----------------
    private fun cookies(): CookieManager = if (perfil != null) ProfileStore.getInstance().getOrCreateProfile(perfil).cookieManager else CookieManager.getInstance()
    private fun cookie(n: String): String? = try {
        cookies().getCookie(base)?.split(";")?.map { it.trim() }?.firstOrNull { it.startsWith("$n=") }?.substringAfter("=")?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }
    private fun cookieDeLogin() = cookie(if (twitch) "auth-token" else "session_token")

    private val confiaveis = if (twitch) listOf("twitch.tv", "apple.com", "facebook.com", "amazon.com", "google.com", "accounts.youtube.com", "gstatic.com")
        else listOf("kick.com", "google.com", "accounts.youtube.com", "apple.com", "discord.com", "gstatic.com")
    private fun confiavel(h: String) = confiaveis.any { h == it || h.endsWith(".$it") } || Regex("^accounts\\.google\\.(com?\\.)?[a-z]{2}$").matches(h)

    @SuppressLint("SetJavaScriptEnabled")
    private fun novaWeb(): WebView {
        val w = WebView(a)
        if (perfil != null) { ProfileStore.getInstance().getOrCreateProfile(perfil); WebViewCompat.setProfile(w, perfil) }
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(false)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) WebSettingsCompat.setSafeBrowsingEnabled(s, true)
        s.userAgentString = s.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        cookies().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false)
        w.setDownloadListener { _, _, _, _, _ -> }
        w.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean { cb.onReceiveValue(null); return true }
        }
        return w
    }
    private fun destruir(w: WebView) {
        (w.parent as? ViewGroup)?.removeView(w)
        try { w.stopLoading(); w.loadUrl("about:blank"); w.destroy() } catch (_: Exception) {}
    }

    // ---------------- leitura (de dentro do próprio site, como o site faz) ----------------
    private fun criarApi() {
        val w = novaWeb()
        w.settings.blockNetworkImage = true
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = !r.url.toString().startsWith(base)
            override fun onPageFinished(v: WebView, url: String) {
                val pronto = url.startsWith(base)
                if (pronto && !apiPronto) { apiPronto = true; laco.postDelayed({ ler() }, 1500) }
                apiPronto = pronto
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) { apiPronto = false; laco.postDelayed({ if (api == v) v.loadUrl(leve) }, 30_000) }
            }
            override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                laco.post { if (api == v) { destruir(v); api = null; apiPronto = false; criarApi() } }
                return true
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(w, "IdleterinoPlat", setOf(base)) { _, msg, origem, principal, _ ->
                if (!principal || "${origem.scheme}://${origem.host}" != base) return@addWebMessageListener
                val j = try { JSONObject(msg.data ?: return@addWebMessageListener) } catch (_: Exception) { return@addWebMessageListener }
                pendentes.remove(j.optInt("id"))?.invoke(j.optInt("status"), j.optString("texto"))
            }
        }
        fundo.addView(w, FrameLayout.LayoutParams(dp(8), dp(8)))
        api = w
        w.loadUrl(leve)
    }

    /** Roda `js` (que preenche s = status e t = texto) na página do site e devolve o resultado. */
    private fun buscar(js: String, cb: (Int, String) -> Unit) {
        val w = api
        if (w == null || !apiPronto) { cb(0, ""); return }
        val id = ++seq
        pendentes[id] = cb
        laco.postDelayed({ pendentes.remove(id)?.invoke(0, "") }, 20_000)
        w.evaluateJavascript("(async()=>{let s=0,t='';try{$js}catch(e){}try{IdleterinoPlat.postMessage(JSON.stringify({id:$id,status:s,texto:t}))}catch(e){}})()", null)
    }

    private fun ler() {
        if (lendo || !apiPronto) return
        lendo = true
        logado = cookieDeLogin() != null
        val fim = {
            lendo = false; organizar()
            if (twitch && logado && autoBau) canais().filter { est(it).bau }.forEach { enfileirarBau(it) } else avisarBau()
            desenhar()
        }
        if (twitch) lerTwitch(fim) else lerKick(fim)
    }

    private val CAMPOS = "login displayName stream{ viewersCount } channel{ self{ communityPoints{ balance availableClaim{ id } } } }"
    private fun lerTwitch(fim: () -> Unit) {
        val token = cookie("auth-token"); val dev = cookie("unique_id")
        val partes = canais().filter { slugOk.matches(it) }.chunked(10)
        if (partes.isEmpty()) { fim(); return }
        var faltam = partes.size
        partes.forEachIndexed { i, parte ->
            val q = "query{ ${if (i == 0) "currentUser{ login displayName }" else ""} " + parte.mapIndexed { j, s -> "c$j: user(login: \"$s\"){ $CAMPOS }" }.joinToString(" ") + " }"
            val corpo = JSONObject().put("query", q).toString()
            val js = "const h={'Client-Id':'kimne78kx3ncx6brgo4mv6wki5h1ko','Content-Type':'text/plain;charset=UTF-8'};" +
                (if (token != null) "h['Authorization']='OAuth '+${JSONObject.quote(token)};" else "") +
                (if (dev != null) "h['X-Device-Id']=${JSONObject.quote(dev)};" else "") +
                "const r=await fetch('https://gql.twitch.tv/gql',{method:'POST',headers:h,body:${JSONObject.quote(corpo)}});s=r.status;t=await r.text();"
            buscar(js) { st, txt ->
                val data = try { JSONObject(txt).optJSONObject("data") } catch (_: Exception) { null }
                if (st == 200 && data != null) {
                    if (i == 0) usuario = data.optJSONObject("currentUser")?.let { u -> u.optString("displayName").ifBlank { u.optString("login") }.ifBlank { null } }
                    parte.forEachIndexed { j, slug -> aplicarTwitch(slug, data.optJSONObject("c$j")) }
                } else parte.forEach { est(it).erro = if (st == 0) "sem resposta" else "Twitch respondeu $st" }
                if (--faltam == 0) fim()
            }
        }
    }
    private fun aplicarTwitch(slug: String, u: JSONObject?) {
        val s = est(slug)
        if (u == null) { s.erro = "canal não encontrado"; s.live = false; return }
        s.erro = null
        s.nome = u.optString("displayName").ifBlank { slug }
        val st = u.optJSONObject("stream")
        s.live = st != null
        s.viewers = st?.optInt("viewersCount") ?: 0
        val cp = u.optJSONObject("channel")?.optJSONObject("self")?.optJSONObject("communityPoints")
        s.bau = cp?.optJSONObject("availableClaim") != null
        if (cp != null && cp.has("balance") && !cp.isNull("balance")) novoSaldo(slug, s, cp.optLong("balance"))
    }

    private fun lerKick(fim: () -> Unit) {
        val token = cookie("session_token")
        val cab = "const h={'Accept':'application/json'};const x=decodeURIComponent((document.cookie.match(/XSRF-TOKEN=([^;]+)/)||[])[1]||'');if(x)h['X-XSRF-TOKEN']=x;" +
            (if (token != null) "h['Authorization']='Bearer '+${JSONObject.quote(token)};" else "")
        val slugs = canais().filter { slugOk.matches(it) }
        fun proximo(i: Int) {
            if (i >= slugs.size) { fim(); return }
            val slug = slugs[i]
            val js = cab + "const r=await fetch('/api/v2/channels/$slug',{headers:h,credentials:'include'});s=r.status;const c=await r.json().catch(()=>null);let p=null;" +
                (if (token != null) "if(c){try{const q=await fetch('/api/v2/channels/$slug/points',{headers:h,credentials:'include'});if(q.status===200)p=await q.json()}catch(e){}}" else "") +
                "t=JSON.stringify({c,p});"
            buscar(js) { st, txt -> aplicarKick(slug, st, txt); proximo(i + 1) }
        }
        if (token != null && usuario == null) {
            buscar(cab + "const r=await fetch('/api/v1/user',{headers:h,credentials:'include'});s=r.status;t=await r.text();") { st, txt ->
                if (st == 200) usuario = try { JSONObject(txt).optString("username").ifBlank { null } } catch (_: Exception) { null }
                proximo(0)
            }
        } else proximo(0)
    }
    private fun aplicarKick(slug: String, st: Int, txt: String) {
        val s = est(slug)
        val j = try { JSONObject(txt) } catch (_: Exception) { null }
        val c = j?.optJSONObject("c")
        if (st != 200 || c == null) { s.erro = if (st == 0) "sem resposta" else "Kick respondeu $st"; return }
        s.erro = null
        val ls = c.optJSONObject("livestream")
        s.live = ls != null
        s.viewers = ls?.optInt("viewer_count") ?: 0
        s.nome = c.optJSONObject("user")?.optString("username")?.ifBlank { null } ?: slug
        val p = j.optJSONObject("p")
        val d = p?.optJSONObject("data") ?: p
        if (d != null && d.has("points") && !d.isNull("points")) novoSaldo(slug, s, d.optLong("points"))
    }

    private fun novoSaldo(slug: String, s: Estado, p: Long) {
        val ant = s.pontos
        if (ant != null && p > ant) { s.ganho += p - ant; somarHoje(p - ant); if (vigias.containsKey(slug)) s.ultimoGanho = System.currentTimeMillis() }
        if (ant != null && p < ant) s.ganho = 0 // gastou na loja: o "+X" recomeça
        s.pontos = p
    }

    private fun avisarBau() {
        if (!twitch || !logado) return
        val com = canais().filter { est(it).bau }
        if (com.isEmpty() || System.currentTimeMillis() - bauAvisadoEm < 15 * 60_000L) return
        bauAvisadoEm = System.currentTimeMillis()
        val nomes = com.take(3).joinToString(", ") { est(it).nome.ifBlank { it } } + if (com.size > 3) "…" else ""
        Avisos.mostrar(a, "🎁 Baú de bônus na Twitch", "Em $nomes. Abra o Idleterino > Twitch e toque no canal para pegar os pontos extras.")
    }

    // ---------------- baú de bônus da Twitch: abre o chat escondido, clica no baú uma vez e fecha (um por vez) ----------------
    private val filaBau = ArrayDeque<String>()
    private var bauOcupado: String? = null
    private var bauWeb: WebView? = null
    private val tentativaBau = HashMap<String, Long>()
    private val ACHAR_BAU = "(function(){var n=0;var achar=function(){var i=document.querySelector('.claimable-bonus__icon');if(i&&i.closest('button'))return i.closest('button');" +
        "var box=document.querySelector('[data-test-selector=\"community-points-summary\"]');var bs=[].slice.call((box||document).querySelectorAll('button'));" +
        "return bs.find(function(b){return /claim bonus|resgatar b[oô]nus|coletar|claim/i.test(b.getAttribute('aria-label')||'')})};" +
        "var t=setInterval(function(){var b=achar();if(b){clearInterval(t);b.click();IdleterinoBau.postMessage('ok');return}if(++n>80){clearInterval(t);IdleterinoBau.postMessage('nao')}},500)})();"

    private fun enfileirarBau(slug: String) {
        val agora = System.currentTimeMillis()
        if (bauOcupado == slug || slug in filaBau || agora - (tentativaBau[slug] ?: 0L) < 90_000L) return
        filaBau.addLast(slug)
        rodarBau()
    }
    private fun rodarBau() {
        if (bauOcupado != null || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        val slug = filaBau.removeFirstOrNull() ?: return
        bauOcupado = slug
        tentativaBau[slug] = System.currentTimeMillis()
        val w = novaWeb()
        // O chat do site de computador (o do celular não tem o mesmo botão do baú).
        val chrome = Regex("Chrome/[\\d.]+").find(w.settings.userAgentString)?.value ?: "Chrome/130.0.0.0"
        w.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        w.settings.useWideViewPort = true
        var rodou = false
        var acabou = false
        fun terminar(ok: Boolean) {
            if (acabou) return
            acabou = true
            val fechar = {
                if (bauWeb == w) bauWeb = null
                destruir(w)
                bauOcupado = null
                if (ok) {
                    val e = est(slug); e.bau = false
                    prefs.edit().putString("diaBau", hoje()).putInt("baus", bausHoje() + 1).apply()
                    android.widget.Toast.makeText(a, "🎁 Baú pego em ${e.nome.ifBlank { slug }}: +50 pts", android.widget.Toast.LENGTH_SHORT).show()
                    desenhar()
                }
                rodarBau()
            }
            // Deixa a Twitch confirmar antes de fechar.
            if (ok) laco.postDelayed(fechar, 3000) else laco.post(fechar)
        }
        WebViewCompat.addWebMessageListener(w, "IdleterinoBau", setOf(base)) { _, msg, _, principal, _ ->
            if (principal) terminar(msg.data == "ok")
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val h = r.url.host ?: return true
                return !(h == dominio || h.endsWith(".$dominio"))
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? = interceptar(r)
            override fun onPageFinished(v: WebView, url: String) {
                if (!rodou && url.startsWith(base)) { rodou = true; v.evaluateJavascript(ACHAR_BAU, null) }
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) { if (r.isForMainFrame) terminar(false) }
            override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean { terminar(false); return true }
        }
        fundo.addView(w, FrameLayout.LayoutParams(dp(400), dp(640)))
        bauWeb = w
        laco.postDelayed({ terminar(false) }, 90_000L)
        w.loadUrl("$base/popout/$slug/chat?popout=")
    }

    // ---------------- as lives escondidas ----------------
    private fun organizar() {
        if (!(ligado && logado && !(soWifi && medido()))) { vigias.keys.toList().forEach { pararVigia(it) }; return }
        val noAr = canais().filter { est(it).live && est(it).erro == null }
        vigias.keys.toList().filter { it !in noAr }.forEach { pararVigia(it) }
        val agora = System.currentTimeMillis()
        val esperando = noAr.filter { it !in vigias }
        // Rodízio: quem está há mais de 45 min dá a vez a outra live oficial que está esperando.
        if (esperando.isNotEmpty()) vigias.keys.toList().filter { agora - est(it).desde > 45 * 60_000L }.take(esperando.size).forEach { pararVigia(it) }
        esperando.sortedBy { est(it).vistoEm }.take(MAX - vigias.size).forEach { iniciarVigia(it) }
    }

    private val MUDO = "(function(){var m=function(){document.querySelectorAll('video,audio').forEach(function(v){v.muted=true;v.volume=0})};" +
        "document.addEventListener('play',m,true);document.addEventListener('playing',m,true);setInterval(m,1500)})();"

    private fun iniciarVigia(slug: String) {
        if (vigias.containsKey(slug)) return
        val w = novaWeb()
        w.settings.blockNetworkImage = true
        val url = if (twitch) "https://player.twitch.tv/?channel=$slug&parent=www.twitch.tv&muted=true" else "$base/$slug"
        val origens = setOf(if (twitch) "https://player.twitch.tv" else base)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) WebViewCompat.addDocumentStartJavaScript(w, MUDO, origens)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val h = r.url.host ?: return true
                return !(h == dominio || h.endsWith(".$dominio"))
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? = interceptar(r)
            override fun onPageFinished(v: WebView, url: String) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) v.evaluateJavascript(MUDO, null)
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) laco.post { if (vigias[slug] == v) pararVigia(slug) }
            }
            override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                laco.post { if (vigias[slug] == v) { vigias.remove(slug); destruir(v) } }
                return true
            }
        }
        fundo.addView(w, FrameLayout.LayoutParams(dp(320), dp(180)))
        vigias[slug] = w
        est(slug).desde = System.currentTimeMillis()
        w.loadUrl(url)
    }
    private fun pararVigia(slug: String) {
        val w = vigias.remove(slug) ?: return
        est(slug).vistoEm = System.currentTimeMillis()
        destruir(w)
    }

    private val BLOQUEADOS = listOf("googlesyndication.com", "doubleclick.net", "googletagmanager.com", "google-analytics.com",
        "googleadservices.com", "adservice.google.com", "imasdk.googleapis.com", "amazon-adsystem.com", "facebook.net",
        "criteo.com", "criteo.net", "adnxs.com", "rubiconproject.com", "pubmatic.com", "taboola.com", "hotjar.com", "clarity.ms", "scorecardresearch.com")

    /** Anúncios fora; e a lista de qualidades da live só com a menor (160p), como escolher 160p no player. */
    private fun interceptar(r: WebResourceRequest): WebResourceResponse? {
        val u = r.url
        val h = u.host ?: return null
        if (BLOQUEADOS.any { h == it || h.endsWith(".$it") }) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        if (r.method != "GET" || u.path?.endsWith(".m3u8") != true) return null
        val mestre = if (twitch) h == "usher.ttvnw.net" else h.endsWith("live-video.net") && u.path?.contains("/api/video/v1/") == true
        if (!mestre) return null
        return try {
            val c = URL(u.toString()).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            for ((k, v) in r.requestHeaders) if (!k.equals("Accept-Encoding", true)) c.setRequestProperty(k, v)
            if (c.responseCode != 200) { c.disconnect(); return null }
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val corpo = menorQualidade(txt) ?: txt
            val origem = r.requestHeaders.entries.firstOrNull { it.key.equals("Origin", true) }?.value
            val cab = hashMapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to (origem ?: "*"))
            if (origem != null) cab["Access-Control-Allow-Credentials"] = "true"
            WebResourceResponse("application/vnd.apple.mpegurl", "utf-8", 200, "OK", cab, ByteArrayInputStream(corpo.toByteArray()))
        } catch (_: Exception) { null }
    }
    private fun menorQualidade(lista: String): String? {
        val linhas = lista.split("\n")
        val resto = mutableListOf<String>(); val variantes = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < linhas.size) {
            if (linhas[i].startsWith("#EXT-X-STREAM-INF") && i + 1 < linhas.size) { variantes.add(linhas[i] to linhas[i + 1]); i += 2 }
            else { resto.add(linhas[i]); i++ }
        }
        val video = variantes.filter { it.first.contains("RESOLUTION=") && !it.first.contains("audio_only", true) }
        if (video.size < 2) return null
        val menor = video.minByOrNull { Regex("BANDWIDTH=(\\d+)").find(it.first)?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE } ?: return null
        return (resto.filter { it.isNotBlank() } + menor.first + menor.second).joinToString("\n") + "\n"
    }

    // ---------------- canais oficiais novos (lista do site do Idleterino) ----------------
    private fun buscarParceiros() = thread {
        try {
            val c = URL(parceiros).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            if (c.responseCode != 200) return@thread
            val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("channels") ?: return@thread
            val l = (0 until minOf(arr.length(), 60)).map { arr.optString(it).lowercase() }.filter { slugOk.matches(it) }
            laco.post {
                val vistos = (prefs.getStringSet("vistos", null) ?: padrao.toSet()).toMutableSet()
                val cs = canais()
                val novos = l.filter { it !in vistos && it !in cs }
                vistos += l; vistos += padrao
                prefs.edit().putStringSet("vistos", vistos).apply()
                if (novos.isNotEmpty()) { gravarCanais(cs + novos); ler() }
            }
        } catch (_: Exception) {}
    }

    // ---------------- tela ----------------
    private fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()
    private fun nf(n: Long) = String.format(Locale("pt", "BR"), "%,d", n)

    private fun montarTela() {
        tela.setBackgroundColor(Color.parseColor("#0B0A16"))
        lista.orientation = LinearLayout.VERTICAL
        lista.setPadding(dp(10), dp(10), dp(10), dp(16))
        rolagem.addView(lista)
        tela.addView(rolagem, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        siteBox.orientation = LinearLayout.VERTICAL
        siteBox.visibility = View.GONE
        siteBox.setBackgroundColor(Color.parseColor("#0B0A16"))
        val topo = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.parseColor("#1C1733")) }
        topo.addView(Button(a).apply { text = "← Lista"; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { fecharSite() } })
        siteTitulo.setTextColor(Color.WHITE); siteTitulo.textSize = 14f; siteTitulo.typeface = Typeface.DEFAULT_BOLD
        topo.addView(siteTitulo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        siteBox.addView(topo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        tela.addView(siteBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        logado = cookieDeLogin() != null
        desenhar()
    }

    private fun abrirSite(url: String, titulo: String) {
        val w = site ?: novaWeb().also { w ->
            w.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val u = r.url
                    if (u.scheme != "https") return true
                    if (confiavel(u.host ?: "")) return false
                    a.abrirFora(u.toString()); return true
                }
                override fun onPageFinished(v: WebView, url: String) {
                    if (!logado && cookieDeLogin() != null) { logado = true; CookieManager.getInstance().flush(); a.runOnUiThread { android.widget.Toast.makeText(a, "Pronto, você entrou na $nome!", android.widget.Toast.LENGTH_SHORT).show() } }
                }
                override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                    laco.post { if (site == v) { destruir(v); site = null; fecharSite() } }
                    return true
                }
            }
            siteBox.addView(w, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            site = w
        }
        siteTitulo.text = titulo
        w.loadUrl(url)
        rolagem.visibility = View.GONE
        siteBox.visibility = View.VISIBLE
    }
    private fun fecharSite() {
        siteBox.visibility = View.GONE
        rolagem.visibility = View.VISIBLE
        site?.loadUrl("about:blank")
        logado = cookieDeLogin() != null
        desenhar()
        laco.postDelayed({ ler() }, 1200)
    }
    /** Botão "voltar" do celular nesta aba. */
    fun voltar(): Boolean {
        if (siteBox.visibility != View.VISIBLE) return false
        val w = site
        if (w != null && w.canGoBack() && w.url != "about:blank") w.goBack() else fecharSite()
        return true
    }

    private fun loja(slug: String) = if (twitch) "$base/popout/$slug/chat?popout=" else "$base/popout/$slug/chat"

    private fun cartao() = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply { setColor(Color.parseColor("#17132A")); cornerRadius = dp(14).toFloat(); setStroke(1, Color.parseColor("#2A2445")) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
    }
    private fun texto(t: String, tam: Float = 13f, negrito: Boolean = false, c: String = "#E8E4FF") = TextView(a).apply {
        text = t; textSize = tam; setTextColor(Color.parseColor(c)); if (negrito) typeface = Typeface.DEFAULT_BOLD; setLineSpacing(0f, 1.15f)
    }
    private fun botao(t: String, cheio: Boolean, f: () -> Unit) = Button(a).apply {
        text = t; isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (cheio) Color.parseColor("#141024") else Color.WHITE)
        background = GradientDrawable().apply { setColor(if (cheio) cor else Color.parseColor("#2A2445")); cornerRadius = dp(10).toFloat() }
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) }
    }
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun chave(t: String, v: Boolean, f: (Boolean) -> Unit) = Switch(a).apply {
        text = t; isChecked = v; setTextColor(Color.parseColor("#E8E4FF")); textSize = 14f
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, x -> f(x) }
    }

    private fun desenhar() {
        lista.removeAllViews()
        val cab = cartao()
        cab.addView(texto(if (logado) "$nome · ${usuario ?: "conectado"}" else nome, 18f, true))
        if (!logado) {
            cab.addView(texto("Entre na sua conta da $nome para o Idleterino assistir às lives oficiais do PokeIdle escondidas, sem som, e juntar pontos de canal para os bônus do jogo." +
                if (twitch) "" else "\nNa Kick, use e-mail e senha (o Google não deixa entrar por app)."))
            cab.addView(botao("Entrar na $nome", true) { abrirSite(if (twitch) "$base/login" else base, "Entrar na $nome") })
        } else {
            cab.addView(chave("Farmar pontos (até 2 lives escondidas, sem som, em 160p)", ligado) { ligado = it; organizar(); desenhar() })
            cab.addView(chave("Só no Wi-Fi (não gasta o plano de dados)", soWifi) { soWifi = it; organizar(); desenhar() })
            if (twitch) cab.addView(chave("Pegar o baú de bônus sozinho (+50 pts, não gasta nada)", autoBau) { autoBau = it; if (it) ler(); desenhar() })
            val st = when {
                !ligado -> "Parado."
                soWifi && medido() -> "Pausado: você está nos dados móveis."
                vigias.isEmpty() -> "Nenhuma live oficial no ar agora. O app confere a cada 1,5 min."
                else -> "▶ Assistindo: " + vigias.keys.joinToString(", ") { est(it).nome.ifBlank { it } }
            }
            cab.addView(texto(if (Elo.pareado(a)) "🖥 Com o Wi-Fi de casa, o Idleterino do PC para a $nome dele enquanto ela farma aqui e volta quando você fechar o app." else "🖥 Para o PC parar a $nome dele quando você farmar aqui: traga as contas pelo QR do PC de novo (Idleterino do PC atualizado).", 12f, false, "#8F88B8"))
            cab.addView(texto("$st\nHoje: +${nf(ganhoHoje())} pontos" + if (twitch && bausHoje() > 0) " · ${bausHoje()} baú(s) pego(s)" else "", 13f, false, "#B7B0DD"))
        }
        lista.addView(cab)

        val cs = canais()
        val ordem = cs.sortedWith(compareByDescending<String> { vigias.containsKey(it) }.thenByDescending { est(it).live }.thenByDescending { est(it).pontos ?: -1 })
        val card = cartao()
        card.addView(texto("CANAIS OFICIAIS · ${cs.count { est(it).live }} AO VIVO", 11f, true, "#A9A2D4"))
        for (slug in ordem) card.addView(linhaCanal(slug))
        card.addView(texto("Toque no canal para abrir a loja (o chat) e resgatar os bônus do PokeIdle. O app não compra nada na loja sozinho. Segure o dedo para tirar o canal da lista.", 12f, false, "#8F88B8").apply { setPadding(0, dp(8), 0, 0) })
        lista.addView(card)

        val add = cartao()
        add.addView(texto("ADICIONAR CANAL", 11f, true, "#A9A2D4"))
        val campo = EditText(a).apply { hint = "nome do canal na $nome"; setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#6F6896")); isSingleLine = true }
        add.addView(campo)
        add.addView(botao("Adicionar", false) {
            val s = campo.text.toString().trim().lowercase().removePrefix("@").substringAfterLast("/")
            if (!slugOk.matches(s)) { android.widget.Toast.makeText(a, "Nome de canal inválido.", android.widget.Toast.LENGTH_SHORT).show(); return@botao }
            gravarCanais(cs + s); desenhar(); ler()
        })
        lista.addView(add)

        if (logado) lista.addView(cartao().apply {
            addView(botao("Abrir a $nome", false) { abrirSite(base, nome) })
            addView(botao("Sair da conta da $nome", false) {
                AlertDialog.Builder(a).setMessage("Sair da conta da $nome neste celular? As lives escondidas param.")
                    .setPositiveButton("Sair") { _, _ ->
                        vigias.keys.toList().forEach { pararVigia(it) }
                        cookies().removeAllCookies { cookies().flush(); a.runOnUiThread { logado = false; usuario = null; estado.values.forEach { it.pontos = null; it.ganho = 0 }; desenhar() } }
                    }.setNegativeButton("Cancelar", null).show()
            })
        })
    }

    private fun linhaCanal(slug: String): View {
        val s = est(slug)
        val l = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(9), 0, dp(9)) }
        val esq = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val vendo = vigias.containsKey(slug)
        esq.addView(texto((if (s.live) "● " else "○ ") + s.nome.ifBlank { slug } + if (s.bau) "  🎁" else "", 15f, true, if (s.live) (if (twitch) "#C9A8FF" else "#7DFF57") else "#8F88B8"))
        val sub = when {
            s.erro != null -> s.erro!!
            bauOcupado == slug -> "🎁 pegando o baú…"
            vendo && s.ultimoGanho > 0 && System.currentTimeMillis() - s.ultimoGanho < 13 * 60_000L -> "▶ assistindo · ganhando pontos"
            vendo -> "▶ assistindo · o 1º ponto leva uns 5 min"
            s.live -> "ao vivo · ${nf(s.viewers.toLong())} assistindo"
            else -> "offline"
        }
        esq.addView(texto(sub, 12f, false, "#8F88B8"))
        l.addView(esq, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val dir = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        dir.addView(texto(s.pontos?.let { nf(it) } ?: "—", 15f, true, "#FFD166").apply { gravity = Gravity.END })
        if (s.ganho > 0 && vendo) dir.addView(texto("+${nf(s.ganho)}", 12f, true, "#7DF0B0").apply { gravity = Gravity.END })
        l.addView(dir)
        l.setOnClickListener { abrirSite(loja(slug), "Loja · ${s.nome.ifBlank { slug }}") }
        l.setOnLongClickListener {
            AlertDialog.Builder(a).setMessage("Tirar ${s.nome.ifBlank { slug }} da lista?")
                .setPositiveButton("Tirar") { _, _ -> pararVigia(slug); gravarCanais(canais().filter { it != slug }); estado.remove(slug); organizar(); desenhar() }
                .setNegativeButton("Cancelar", null).show()
            true
        }
        return l
    }
}
