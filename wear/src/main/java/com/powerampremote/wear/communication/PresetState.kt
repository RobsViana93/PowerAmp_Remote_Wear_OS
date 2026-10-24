package com.powerampremote.wear.communication

data class PresetState(
    val page: PresetProtocol.Result? = null,
    val selected: PresetProtocol.Item? = null,
    val lastRequested: PresetProtocol.Item? = null,
    val stage: PresetProtocol.Stage? = null,
    val failed: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false
)

sealed interface PresetIntent {
    data object Open : PresetIntent
    data object Refresh : PresetIntent
    data object Next : PresetIntent
    data object Previous : PresetIntent
    data class Select(val item: PresetProtocol.Item) : PresetIntent
    data object Apply : PresetIntent
    data object Close : PresetIntent
}
