package com.powerampremote.mobile.poweramp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.powerampremote.mobile.communication.PlayerProtocol
import com.powerampremote.mobile.communication.PlayerProtocol.Result
import com.powerampremote.mobile.communication.PlayerProtocol.Stage

/** Traduz apenas a lista fechada de comandos para o receiver público oficial. */
class PowerampController(private val context: Context) {
    fun execute(request: PlayerProtocol.Request, onRequested: () -> Unit): Result {
        fun result(stage: Stage) = Result(request, stage, PowerampRepository.read(context))
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PowerampContract.PACKAGE, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return result(Stage.NOT_INSTALLED)
        }
        val receiver = ComponentName(PowerampContract.PACKAGE, PowerampContract.RECEIVER)
        try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getReceiverInfo(receiver, 0)
            if (!info.exported || !info.enabled) return result(Stage.UNSUPPORTED)
        } catch (_: PackageManager.NameNotFoundException) {
            return result(Stage.UNSUPPORTED)
        }
        val before = PowerampRepository.read(context)
        val issuedAt = System.currentTimeMillis()
        return try {
            context.sendBroadcast(Intent(PowerampContract.COMMAND).setComponent(receiver)
                .putExtra("cmd", request.command.apiCode))
            onRequested()
            val observed = PowerampRepository.awaitEvidence(context, request.command, before, issuedAt)
            result(if (observed) Stage.OBSERVED else Stage.NO_UPDATE)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            result(Stage.ERROR)
        } catch (_: RuntimeException) {
            result(Stage.ERROR)
        }
    }
}
