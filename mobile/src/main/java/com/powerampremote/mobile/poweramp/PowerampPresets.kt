package com.powerampremote.mobile.poweramp

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.powerampremote.mobile.communication.PresetProtocol as Protocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Somente leitura do provedor público e comando explícito de seleção; nunca altera o preset salvo. */
class PowerampPresets(private val context: Context) {
    fun execute(request: Protocol.Request): Protocol.Result {
        if (!Protocol.valid(request)) return Protocol.Result(request, Protocol.Stage.ERROR)
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PowerampContract.PACKAGE, 0)
            if (request.action == Protocol.Action.LIST) {
                val items = query("eq_presets._id > ?", request.afterId, Protocol.PAGE_SIZE + 1)
                Protocol.Result(request, Protocol.Stage.PAGE, items.take(Protocol.PAGE_SIZE), items.size > Protocol.PAGE_SIZE)
            } else {
                if (query("eq_presets._id = ?", request.presetId, 1).isEmpty())
                    Protocol.Result(request, Protocol.Stage.NOT_FOUND)
                else apply(request)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            Protocol.Result(request, Protocol.Stage.UNAVAILABLE)
        } catch (_: SecurityException) {
            Protocol.Result(request, Protocol.Stage.DENIED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            Protocol.Result(request, Protocol.Stage.ERROR)
        } catch (error: RuntimeException) {
            android.util.Log.w("PowerampPresets", "Falha na API pública de presets", error)
            Protocol.Result(request, Protocol.Stage.ERROR)
        }
    }

    private fun query(selection: String, id: Long, limit: Int): List<Protocol.Item> {
        val uri = Uri.parse("content://com.maxmpz.audioplayer.data/eq_presets").buildUpon()
            .appendQueryParameter("lim", limit.toString()).build()
        val signal = CancellationSignal()
        val handler = Handler(Looper.getMainLooper())
        val cancel = Runnable { signal.cancel() }
        handler.postDelayed(cancel, 5_000)
        try {
            return context.contentResolver.query(uri, arrayOf("eq_presets._id", "eq_presets.name", "parametric"),
                selection, arrayOf(id.toString()), "eq_presets._id", signal)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        check(size < limit)
                        val presetId = cursor.getLong(0)
                        check(presetId > 0)
                        val name = cursor.getString(1).orEmpty().trim().ifBlank { "Preset #$presetId" }
                        add(Protocol.Item(presetId, name.take(Protocol.TEXT_LIMIT), cursor.getInt(2) != 0))
                    }
                }
            } ?: error("Consulta sem cursor")
        } finally { handler.removeCallbacks(cancel) }
    }

    private fun apply(request: Protocol.Request): Protocol.Result {
        val observed = CountDownLatch(1)
        val issuedAt = AtomicLong(Long.MAX_VALUE)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != "com.maxmpz.audioplayer.EQU_CHANGED") return
                try {
                    @Suppress("DEPRECATION")
                    val id = when (val value = intent.extras?.get("id")) {
                        is Long -> value
                        is Int -> value.toLong()
                        else -> null
                    }
                    if (Protocol.observed(request, id, intent.getLongExtra("ts", 0), issuedAt.get(),
                            System.currentTimeMillis())) observed.countDown()
                } catch (_: RuntimeException) { /* Evento inválido não confirma a seleção. */ }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("com.maxmpz.audioplayer.EQU_CHANGED"),
            ContextCompat.RECEIVER_EXPORTED)
        try {
            issuedAt.set(System.currentTimeMillis())
            context.sendBroadcast(Intent(PowerampContract.COMMAND)
                .setComponent(ComponentName(PowerampContract.PACKAGE, PowerampContract.RECEIVER))
                .putExtra("cmd", 50).putExtra("id", request.presetId))
            return Protocol.Result(request, if (observed.await(2, TimeUnit.SECONDS))
                Protocol.Stage.OBSERVED else Protocol.Stage.REQUESTED)
        } finally { context.unregisterReceiver(receiver) }
    }
}
