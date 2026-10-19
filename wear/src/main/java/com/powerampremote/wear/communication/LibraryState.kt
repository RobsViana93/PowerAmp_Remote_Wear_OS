package com.powerampremote.wear.communication

data class LibraryState(
    val albums: LibraryProtocol.Result? = null,
    val tracks: LibraryProtocol.Result? = null,
    val album: LibraryProtocol.Item? = null,
    val stage: LibraryProtocol.Stage? = null,
    val failed: Boolean = false,
    val canGoPrevious: Boolean = false
)

sealed interface LibraryIntent {
    data object Open : LibraryIntent
    data object Refresh : LibraryIntent
    data object Next : LibraryIntent
    data object Previous : LibraryIntent
    data object Albums : LibraryIntent
    data object PlayAlbum : LibraryIntent
    data class OpenAlbum(val album: LibraryProtocol.Item) : LibraryIntent
    data class PlayTrack(val id: Long) : LibraryIntent
    data object Close : LibraryIntent
}
