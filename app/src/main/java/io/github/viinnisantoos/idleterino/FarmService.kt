package io.github.viinnisantoos.idleterino

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Enquanto o app está aberto (na tela ou atrás de outro app), este serviço em primeiro plano (com a notificação fixa que o
 * Android exige) mantém o jogo, as ALTs e as lives rodando. Com "tela desligada" ligado, também segura o processador
 * (wake lock) para seguir com a tela apagada. Tirar o app dos recentes = fechar: para tudo.
 */
class FarmService : Service() {
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PARAR) { fecharTudo(); return START_NOT_STICKY }
        val travar = intent?.getBooleanExtra("lock", false) ?: false
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val parar = PendingIntent.getService(this, 1, Intent(this, FarmService::class.java).setAction(PARAR), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, Avisos.CANAL_FARM)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Idleterino farmando")
            .setContentText(if (travar) "Segue farmando com a tela desligada." else "Segue farmando enquanto você usa outros apps.")
            .setContentIntent(abrir)
            .addAction(Notification.Action.Builder(null, "Fechar o Idleterino", parar).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7, n)
        if (travar && lock == null) {
            lock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "idleterino:farm").apply { setReferenceCounted(false); acquire() }
        } else if (!travar) { lock?.let { if (it.isHeld) it.release() }; lock = null }
        ligado = true
        return START_NOT_STICKY
    }

    // A pessoa tirou o app dos recentes: é "fechar o app" — para o jogo, as ALTs e as lives.
    override fun onTaskRemoved(rootIntent: Intent?) { fecharTudo() }

    private fun fecharTudo() {
        ligado = false
        lock?.let { if (it.isHeld) it.release() }
        lock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroy() {
        ligado = false
        lock?.let { if (it.isHeld) it.release() }
        lock = null
        super.onDestroy()
    }

    companion object {
        private const val PARAR = "parar"
        @Volatile var ligado = false
        fun ligar(c: Context, telaDesligada: Boolean) { try { c.startForegroundService(Intent(c, FarmService::class.java).putExtra("lock", telaDesligada)) } catch (_: Exception) {} }
        fun desligar(c: Context) { c.stopService(Intent(c, FarmService::class.java)); ligado = false }
    }
}

object Avisos {
    const val CANAL_FARM = "farm"
    const val CANAL_AVISOS = "avisos"
    private var id = 100
    fun criarCanais(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_FARM, "Modo farm", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CANAL_AVISOS, "Avisos do jogo", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun mostrar(c: Context, titulo: String, texto: String) {
        val abrir = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, CANAL_AVISOS).setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(titulo).setContentText(texto).setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(abrir).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE) // tela bloqueada: sem detalhes
            .setPublicVersion(Notification.Builder(c, CANAL_AVISOS).setSmallIcon(android.R.drawable.star_on).setContentTitle("Idleterino").setContentText("Novidade no jogo").build())
            .build()
        try { c.getSystemService(NotificationManager::class.java).notify(id++, n) } catch (_: SecurityException) {}
    }
}
