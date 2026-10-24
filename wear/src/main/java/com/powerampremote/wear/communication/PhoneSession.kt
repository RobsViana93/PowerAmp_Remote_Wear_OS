package com.powerampremote.wear.communication

/** IDs de faixas, álbuns e presets pertencem ao celular que os forneceu. */
internal object PhoneSession {
    fun canSend(origin: String?, target: String?, requiresOrigin: Boolean): Boolean =
        !target.isNullOrBlank() && (!requiresOrigin || (!origin.isNullOrBlank() && origin == target))

    fun acceptsReply(selected: String?, expected: String, source: String): Boolean =
        canSend(expected, selected, requiresOrigin = true) && expected == source
}
