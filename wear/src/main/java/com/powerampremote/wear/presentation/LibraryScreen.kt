package com.powerampremote.wear.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.powerampremote.wear.R
import com.powerampremote.wear.communication.*
import kotlinx.coroutines.launch

@Composable
internal fun LibraryScreen(state: CommunicationState, onAction: (LibraryIntent) -> Unit,
                           onVisibleAlbumArt: (List<Long>) -> Unit,
                           onClose: () -> Unit, onPlayer: () -> Unit) {
    val library = state.library
    val album = library.album
    val page = if (album == null) library.albums else library.tracks
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val available = state.connection == Connection.AVAILABLE && state.libraryAvailable && !state.busy
    val failed = library.failed || library.stage in setOf(LibraryProtocol.Stage.DENIED,
        LibraryProtocol.Stage.UNAVAILABLE, LibraryProtocol.Stage.NOT_FOUND, LibraryProtocol.Stage.EXPIRED, LibraryProtocol.Stage.ERROR)
    LaunchedEffect(album?.id, page?.request?.id, failed) { list.scrollToItem(0); focus.requestFocus() }
    LaunchedEffect(album?.id, page?.request?.id) {
        if (album != null) onVisibleAlbumArt(emptyList())
        else snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } }
            .distinctUntilChanged().collectLatest { onVisibleAlbumArt(it) }
    }
    LazyColumn(Modifier.fillMaxSize().background(Color.Black)
        .onRotaryScrollEvent { event -> scope.launch { list.scrollBy(event.verticalScrollPixels) }; true }
        .focusRequester(focus).focusable(), state = list,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(if (album == null) R.string.library_title else R.string.library_tracks_title),
                color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        if (album != null) item {
            Text(album.title.ifBlank { stringResource(R.string.library_unknown_album) }, color = Color(0xFF80CBC4),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
            Button(onClick = { onAction(LibraryIntent.PlayAlbum) }, enabled = available && !failed && page?.items?.isNotEmpty() == true) {
                Text(stringResource(R.string.library_play_album), textAlign = TextAlign.Center)
            }
        }
        val notice = when {
            state.busy -> R.string.library_loading
            state.connection != Connection.AVAILABLE -> R.string.library_disconnected
            !state.libraryAvailable -> R.string.controls_update_companion
            library.failed -> R.string.library_failed
            library.stage == LibraryProtocol.Stage.DENIED -> R.string.library_denied
            library.stage == LibraryProtocol.Stage.UNAVAILABLE -> R.string.library_unavailable
            library.stage == LibraryProtocol.Stage.NOT_FOUND -> R.string.library_not_found
            library.stage == LibraryProtocol.Stage.EXPIRED -> R.string.library_expired
            library.stage == LibraryProtocol.Stage.ERROR -> R.string.library_failed
            library.stage == LibraryProtocol.Stage.OBSERVED -> R.string.library_observed
            library.stage == LibraryProtocol.Stage.REQUESTED -> R.string.library_requested
            page?.items?.isEmpty() == true -> if (album == null) R.string.library_empty else R.string.library_empty_album
            album != null -> R.string.library_track_hint
            else -> R.string.library_album_hint
        }
        item { Text(stringResource(notice), color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center) }
        if (!failed && page != null) items(page.items, key = { it.id }) { entry ->
            if (album == null) {
                AlbumTile(entry, state.albumArt[entry.id], available) { onAction(LibraryIntent.OpenAlbum(entry)) }
            } else {
                Button(onClick = { onAction(LibraryIntent.PlayTrack(entry.id)) },
                    enabled = available, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(entry.title.ifBlank { stringResource(R.string.library_unknown_track) },
                            maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(entry.subtitle.ifBlank { album.subtitle.ifBlank { stringResource(R.string.library_unknown_artist) } },
                            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (library.canGoPrevious && !failed) item {
            Button(onClick = { onAction(LibraryIntent.Previous) }, enabled = available) { Text(stringResource(R.string.library_previous)) }
        }
        if (!failed && !page?.next.isNullOrEmpty()) item {
            Button(onClick = { onAction(LibraryIntent.Next) }, enabled = available) { Text(stringResource(R.string.library_next)) }
        }
        item {
            Button(onClick = { onAction(LibraryIntent.Refresh) }, enabled = available) { Text(stringResource(R.string.library_refresh)) }
            Text(stringResource(R.string.library_changes), color = Color.White, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center)
        }
        if (album != null) item {
            Button(onClick = { onAction(LibraryIntent.Albums) }) { Text(stringResource(R.string.library_back_albums)) }
        }
        item {
            Button(onClick = onPlayer) { Text(stringResource(R.string.library_player)) }
            Button(onClick = onClose) { Text(stringResource(R.string.visual_back)) }
        }
    }
}

/** Alvo único para toque e TalkBack; a capa pode chegar depois do texto. */
@Composable
private fun AlbumTile(entry: LibraryProtocol.Item, artwork: android.graphics.Bitmap?,
                      enabled: Boolean, onClick: () -> Unit) {
    val title = entry.title.ifBlank { stringResource(R.string.library_unknown_album) }
    val artist = entry.subtitle.ifBlank { stringResource(R.string.library_unknown_artist) }
    val targetSize = if (LocalDensity.current.fontScale > 1.2f) 160.dp else 128.dp
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val size = minOf(targetSize, maxWidth)
        Box(Modifier.size(size).clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF263238))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$title, $artist"
                role = Role.Button
                if (enabled) onClick { onClick(); true } else disabled()
            }) {
            if (artwork != null) Image(artwork.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Transparent, Color(0xAA000000), Color(0xEE000000)))))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(artist, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
