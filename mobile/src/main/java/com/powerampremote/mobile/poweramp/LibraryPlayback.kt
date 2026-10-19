package com.powerampremote.mobile.poweramp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Transporte público da sessão de mídia: não abre uma Activity a partir do serviço do Companion. */
object LibraryPlayback {
    fun request(context: Context, mediaId: String, observe: (MediaController) -> Boolean): Boolean? {
        @Suppress("DEPRECATION")
        val service = context.packageManager.resolveService(Intent("android.media.browse.MediaBrowserService")
            .setPackage(PowerampContract.PACKAGE), 0)?.serviceInfo ?: return null
        if (!service.exported || !service.enabled) return null
        val main = Handler(Looper.getMainLooper())
        val done = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val sent = AtomicBoolean(false)
        val reference = AtomicReference<MediaBrowser?>()
        val controller = AtomicReference<MediaController?>()
        main.post {
            if (closed.get()) return@post
            try {
                val browser = MediaBrowser(context.applicationContext,
                    ComponentName(service.packageName, service.name), object : MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            try {
                                val current = reference.get()
                                if (!closed.get() && current != null && current.isConnected) {
                                    // IDs relativos devolvidos pelo MediaBrowser público do Poweramp.
                                    val session = MediaController(context, current.sessionToken)
                                    controller.set(session)
                                    session.transportControls.playFromMediaId(mediaId, null)
                                    sent.set(true)
                                }
                            } catch (_: RuntimeException) { /* O resultado permanece sem envio confirmado. */ }
                            finally { done.countDown() }
                        }
                        override fun onConnectionFailed() { done.countDown() }
                        override fun onConnectionSuspended() { done.countDown() }
                    }, null)
                reference.set(browser)
                browser.connect()
            } catch (_: RuntimeException) { done.countDown() }
        }
        try {
            if (!done.await(5, TimeUnit.SECONDS) || !sent.get()) return null
            // Mantém o navegador conectado enquanto a sessão processa o comando assíncrono.
            return controller.get()?.let(observe)
        }
        finally {
            closed.set(true)
            main.post { try { reference.get()?.disconnect() } catch (_: RuntimeException) { } }
        }
    }
}
