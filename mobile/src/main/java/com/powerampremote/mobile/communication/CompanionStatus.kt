package com.powerampremote.mobile.communication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Diagnóstico apenas em memória; não representa estado de reprodução. */
object CompanionStatus {
    private val mutableStatus = MutableStateFlow(ReceiptStatus.WAITING)
    val status = mutableStatus.asStateFlow()

    fun update(status: ReceiptStatus) { mutableStatus.value = status }
}

enum class ReceiptStatus { WAITING, RECEIVED, ACK_SENT, ACK_FAILED }
