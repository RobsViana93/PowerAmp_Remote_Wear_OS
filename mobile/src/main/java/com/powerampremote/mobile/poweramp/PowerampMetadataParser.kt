package com.powerampremote.mobile.poweramp

import android.content.Intent
import com.powerampremote.mobile.communication.NowPlayingProtocol

/** Chaves e unidades conferidas em PowerampAPI.Track; pos é sempre extra de topo. */
object PowerampMetadataParser {
    @Suppress("DEPRECATION")
    fun realId(intent: Intent): Long? {
        val extras = intent.extras
        val value = if (extras?.containsKey("realId") == true) extras.get("realId")
            else intent.getBundleExtra("track")?.get("realId")
        return integer(value)?.takeIf { it > 0 }
    }
    @Suppress("DEPRECATION")
    fun parse(intent: Intent, at: Long): NowPlayingProtocol.Update {
        val extras = intent.extras
        val track = intent.getBundleExtra("track")
        fun field(key: String): Any? = if (extras?.containsKey(key) == true) extras.get(key) else track?.get(key)
        fun number(key: String): Long? = integer(field(key))
        return NowPlayingProtocol.Update(
            at = at,
            trackEvent = intent.action == PowerampContract.TRACK,
            trackId = number("id")?.takeIf { it >= -3 && it != -1L && it != 0L },
            title = field("title") as? String,
            artist = field("artist") as? String,
            album = field("album") as? String,
            durationMs = NowPlayingProtocol.durationMs(number("durMs"), number("dur")),
            positionMs = NowPlayingProtocol.secondsToMs(integer(extras?.get("pos")))
        )
    }

    private fun integer(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }
}
