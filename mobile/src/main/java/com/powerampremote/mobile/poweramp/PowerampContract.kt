package com.powerampremote.mobile.poweramp

/** Subconjunto conferido em maxmpz/powerampapi, PowerampAPI.java, em 18/10/2026.
 * Os números dos comandos estão em PlayerProtocol.Command; não há APIs privadas.
 */
object PowerampContract {
    const val PACKAGE = "com.maxmpz.audioplayer"
    const val RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
    const val COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
    const val STATUS = "com.maxmpz.audioplayer.STATUS_CHANGED_EXPLICIT"
    const val TRACK = "com.maxmpz.audioplayer.TRACK_CHANGED_EXPLICIT"
    const val POSITION_SYNC = "com.maxmpz.audioplayer.TPOS_SYNC"
}
