package com.powerampremote.mobile.poweramp

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.powerampremote.mobile.communication.NowPlayingProtocol.SyncStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Consulta oficial POS_SYNC, somente por Atualizar; sem polling ou serviço permanente. */
class PowerampPositionSync(private val context: Context) {
    fun request(): SyncStage {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PowerampContract.PACKAGE, 0)
        } catch (_: PackageManager.NameNotFoundException) { return SyncStage.NOT_INSTALLED }
        val baseline = PowerampRepository.readNowPlaying(context)
        if (baseline.metadataAt == 0L || baseline.trackId == -1L) return SyncStage.NO_POSITION
        val latch = CountDownLatch(1)
        val accepted = AtomicBoolean(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != PowerampContract.POSITION_SYNC) return
                try {
                    val position = intent.getIntExtra("pos", -1)
                    accepted.set(PowerampRepository.receivePosition(context, position, baseline))
                } catch (_: RuntimeException) { accepted.set(false) }
                latch.countDown()
            }
        }
        var registered = false
        return try {
            val filter = IntentFilter(PowerampContract.POSITION_SYNC)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
            context.sendBroadcast(Intent(PowerampContract.COMMAND)
                .setComponent(ComponentName(PowerampContract.PACKAGE, PowerampContract.RECEIVER))
                .putExtra("cmd", 16))
            if (latch.await(3, TimeUnit.SECONDS) && accepted.get()) SyncStage.SYNCED else SyncStage.NO_POSITION
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            SyncStage.ERROR
        } catch (_: RuntimeException) { SyncStage.ERROR }
        finally { if (registered) context.unregisterReceiver(receiver) }
    }
}
