package com.rommelguerra.prototipomusichaptic.integracion

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.indoamerica.haptica.soundengine.SoundEvent

internal class AvisosSonidoAndroid(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val canal = "sonidos_cotidianos"

    init {
        manager.createNotificationChannel(
            NotificationChannel(canal, "Sonidos cotidianos", NotificationManager.IMPORTANCE_HIGH).apply {
                // Evita que el sonido del propio aviso vuelva a entrar al micrófono.
                setSound(null, null)
            }
        )
    }

    fun habilitadas(): Boolean = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(canal)?.importance != NotificationManager.IMPORTANCE_NONE

    @SuppressLint("MissingPermission")
    fun publicar(evento: SoundEvent): Boolean {
        if (!habilitadas()) return false
        val aviso = Notification.Builder(context, canal)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(CatalogoCotidianoEquipo.etiqueta(evento.clase))
            .setContentText("Detectado en ${evento.contexto} · ${(evento.confianza * 100).toInt()} % de confianza")
            .setCategory(Notification.CATEGORY_EVENT)
            .setAutoCancel(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            aviso.setContentIntent(PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE))
        }
        return try {
            manager.notify(1, aviso.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
