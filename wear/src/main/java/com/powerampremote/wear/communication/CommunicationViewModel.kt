package com.powerampremote.wear.communication

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.wearable.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.os.PowerManager
import android.os.SystemClock

/** Estado imutável que a interface observa; conexão e resultado são separados. */
data class CommunicationState(
    val connection: Connection = Connection.CHECKING,
    val phase: TestPhase = TestPhase.IDLE,
    val busy: Boolean = false,
    val playerAvailable: Boolean = false,
    val playerSnapshot: PlayerProtocol.Snapshot = PlayerProtocol.Snapshot(),
    val playerStage: PlayerProtocol.Stage? = null,
    val playerNotice: PlayerNotice = PlayerNotice.IDLE,
    val playerCommand: PlayerProtocol.Command? = null,
    val nowPlaying: NowPlayingProtocol.Snapshot = NowPlayingProtocol.Snapshot(),
    val metadataAvailable: Boolean = false,
    val syncStage: NowPlayingProtocol.SyncStage? = null,
    val syncNotice: SyncNotice = SyncNotice.IDLE,
    val visualAvailable: Boolean = false,
    val artwork: Bitmap? = null,
    val seekStage: VisualProtocol.Stage? = null,
    val seekFailed: Boolean = false,
    val controlsAvailable: Boolean = false,
    val controls: ControlsProtocol.Snapshot = ControlsProtocol.Snapshot(),
    val controlsStage: ControlsProtocol.Stage? = null,
    val controlsAction: ControlsProtocol.Action? = null,
    val controlsFailed: Boolean = false,
    val equalizerAvailable: Boolean = false,
    val equalizer: EqualizerProtocol.Snapshot = EqualizerProtocol.Snapshot(),
    val equalizerStage: EqualizerProtocol.Stage? = null,
    val equalizerFailed: Boolean = false,
    val libraryAvailable: Boolean = false,
    val library: LibraryState = LibraryState(),
    val presetsAvailable: Boolean = false,
    val presets: PresetState = PresetState(),
    val estimatedPositionMs: Long? = null,
    val autoUpdatesActive: Boolean = false,
    val autoUpdatesFailed: Boolean = false
)
enum class SyncNotice { IDLE, LOADING, UPDATE_COMPANION, NO_PHONE, FAILED }
enum class PlayerNotice { IDLE, SENDING, WAITING, TIMEOUT, FAILED, CANCELLED, INCOMPATIBLE, NO_PHONE }
enum class Connection { CHECKING, AVAILABLE, UNAVAILABLE, ERROR }
enum class TestPhase { IDLE, SENDING, WAITING_ACK, CONFIRMED, NO_PHONE, FAILED, TIMEOUT, CANCELLED }

/** Mantém o teste e seu estado durante recomposições e mudanças de configuração. */
class CommunicationViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener, DataClient.OnDataChangedListener {
    private val messages = Wearable.getMessageClient(application)
    private val capabilities = Wearable.getCapabilityClient(application)
    private val dataClient = Wearable.getDataClient(application)
    private val mutableState = MutableStateFlow(CommunicationState())
    val state = mutableState.asStateFlow()
    private val registrationMutex = Mutex()
    private var listenersReady = false
    private val refreshMutex = Mutex()
    private var nodes: Set<Node> = emptySet()
    private var testJob: Job? = null
    private var pending: PendingTest? = null
    private var playerJob: Job? = null
    private var pendingPlayer: PendingPlayer? = null
    private var selectedPhoneId: String? = null
    private var syncJob: Job? = null
    private var pendingSync: PendingSync? = null
    private var seekJob: Job? = null
    private var pendingSeek: PendingSeek? = null
    private var artJob: Job? = null
    private var artKey: String? = null
    private var controlsJob: Job? = null
    private var pendingControls: PendingControls? = null
    private var equalizerJob: Job? = null
    private var pendingEqualizer: PendingEqualizer? = null
    private var libraryJob: Job? = null
    private var pendingLibrary: PendingLibrary? = null
    private var playerVisible = false
    private var presetJob: Job? = null
    private var pendingPreset: PendingPreset? = null
    private var presetHistory = emptyList<Long>()
    private data class PendingPreset(val request: PresetProtocol.Request, val nodeId: String,
                                     val result: CompletableDeferred<PresetProtocol.Result>)
    private var albumCursor = ""
    private var trackCursor = ""
    private var albumHistory = emptyList<String>()
    private var trackHistory = emptyList<String>()
    private data class PendingLibrary(val request: LibraryProtocol.Request, val nodeId: String,
                                      val result: CompletableDeferred<LibraryProtocol.Result>)
    private data class PendingEqualizer(val request: EqualizerProtocol.Request, val nodeId: String,
                                        val result: CompletableDeferred<EqualizerProtocol.Result>)
    private var sessionSequence = 0L
    private var foregroundSession = Long.MAX_VALUE
    private var estimate: Estimate? = null
    private var autoFailures = 0
    private data class Estimate(val trackId: Long, val trackAt: Long, val positionMs: Long,
                                val receivedElapsed: Long, val sourceAt: Long)
    private data class PendingControls(val request: ControlsProtocol.Request, val nodeId: String,
                                       val result: CompletableDeferred<ControlsProtocol.Result>)
    @Volatile private var cleared = false

    private data class PendingTest(
        val id: String,
        val nodeId: String,
        val acknowledgement: CompletableDeferred<Unit>
    )
    private data class PendingPlayer(
        val request: PlayerProtocol.Request,
        val nodeId: String,
        val result: CompletableDeferred<PlayerProtocol.Result>
    )
    private data class PendingSync(val id: String, val nodeId: String,
                                   val result: CompletableDeferred<NowPlayingProtocol.SyncResult>)
    private data class PendingSeek(val id: String, val nodeId: String,
                                  val result: CompletableDeferred<VisualProtocol.Result>)

    /** Também permite repetir a inicialização após indisponibilidade do Play services. */
    private suspend fun ensureListeners(): Boolean = registrationMutex.withLock {
        if (listenersReady) return@withLock true
        try {
            withTimeout(5_000) {
                // Inicia registros independentes juntos; aguardar um por vez somava a latência do Play services.
                val registrations = mutableListOf(
                    messages.addListener(this@CommunicationViewModel)
                        .addOnSuccessListener { if (cleared) messages.removeListener(this@CommunicationViewModel) },
                    dataClient.addListener(this@CommunicationViewModel)
                        .addOnSuccessListener { if (cleared) dataClient.removeListener(this@CommunicationViewModel) })
                for (capability in listOf(TestProtocol.CAPABILITY, PlayerProtocol.CAPABILITY, NowPlayingProtocol.CAPABILITY,
                    VisualProtocol.CAPABILITY, ControlsProtocol.CAPABILITY, EqualizerProtocol.CAPABILITY, LibraryProtocol.CAPABILITY,
                    PresetProtocol.CAPABILITY)) {
                    registrations += capabilities.addListener(this@CommunicationViewModel, capability)
                        .addOnSuccessListener { if (cleared) capabilities.removeListener(this@CommunicationViewModel, capability) }
                }
                registrations.forEach { it.await() }
            }
            listenersReady = true
            true
        } catch (_: TimeoutCancellationException) {
            mutableState.update { it.copy(connection = Connection.ERROR) }
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(connection = Connection.ERROR) }
            false
        }
    }

    /** Consulta ao abrir a tela ou por ação explícita; não há polling contínuo. */
    fun refreshConnection() {
        viewModelScope.launch {
            if (ensureListeners()) {
                val phone = queryPhone()
                if (phone != null) loadPlayerState(phone.id)
            }
        }
    }

    private suspend fun queryPhone(): Node? = refreshMutex.withLock {
        mutableState.update { it.copy(connection = Connection.CHECKING) }
        try {
            val discovery = withTimeout(5_000) {
                coroutineScope {
                    listOf(TestProtocol.CAPABILITY, PlayerProtocol.CAPABILITY, NowPlayingProtocol.CAPABILITY,
                        VisualProtocol.CAPABILITY, ControlsProtocol.CAPABILITY, EqualizerProtocol.CAPABILITY,
                        LibraryProtocol.CAPABILITY, PresetProtocol.CAPABILITY).map { capability -> async {
                        capabilities.getCapability(capability, CapabilityClient.FILTER_REACHABLE).await().nodes
                    } }.awaitAll()
                }
            }
            nodes = discovery[0]
            val phone = nodes.sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()
            if (phone?.id != selectedPhoneId) {
                mutableState.update { it.copy(playerSnapshot = PlayerProtocol.Snapshot(),
                    nowPlaying = NowPlayingProtocol.Snapshot(), artwork = null, syncStage = null, syncNotice = SyncNotice.IDLE) }
                artJob?.cancel()
                artKey = null
                estimate = null
                mutableState.update { it.copy(controls = ControlsProtocol.Snapshot(), estimatedPositionMs = null,
                    controlsStage = null, controlsAction = null, controlsFailed = false,
                    equalizer = EqualizerProtocol.Snapshot(), equalizerStage = null, equalizerFailed = false,
                    library = LibraryState(), presets = PresetState()) }
                presetHistory = emptyList()
                albumCursor = ""; trackCursor = ""; albumHistory = emptyList(); trackHistory = emptyList()
            }
            selectedPhoneId = phone?.id
            mutableState.update {
                it.copy(connection = if (nodes.isEmpty()) Connection.UNAVAILABLE else Connection.AVAILABLE,
                    playerAvailable = discovery[1].any { node -> node.id == phone?.id },
                    metadataAvailable = discovery[2].any { node -> node.id == phone?.id },
                    visualAvailable = discovery[3].any { node -> node.id == phone?.id },
                    controlsAvailable = discovery[4].any { node -> node.id == phone?.id },
                    equalizerAvailable = discovery[5].any { node -> node.id == phone?.id },
                    libraryAvailable = discovery[6].any { node -> node.id == phone?.id },
                    presetsAvailable = discovery[7].any { node -> node.id == phone?.id })
            }
            // Prefere o celular próximo; escolha determinística se houver mais de um.
            phone
        } catch (_: TimeoutCancellationException) {
            nodes = emptySet()
            mutableState.update { it.copy(connection = Connection.ERROR, playerAvailable = false) }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            nodes = emptySet()
            mutableState.update { it.copy(connection = Connection.ERROR, playerAvailable = false) }
            null
        }
    }

    fun sendTest() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, phase = TestPhase.SENDING) }
        testJob = viewModelScope.launch {
            try {
                withTimeout(15_000) {
                    if (!ensureListeners()) {
                        mutableState.update { it.copy(phase = TestPhase.FAILED) }
                        return@withTimeout
                    }
                    val phone = queryPhone()
                    if (phone == null) {
                        mutableState.update {
                            it.copy(phase = if (it.connection == Connection.ERROR) TestPhase.FAILED else TestPhase.NO_PHONE)
                        }
                        return@withTimeout
                    }
                    val request = PendingTest(UUID.randomUUID().toString(), phone.id, CompletableDeferred())
                    pending = request // Instala a espera antes de enviar; uma resposta rápida não se perde.
                    messages.sendMessage(phone.id, TestProtocol.REQUEST_PATH, TestProtocol.encode(request.id)).await()
                    mutableState.update { it.copy(phase = TestPhase.WAITING_ACK) }
                    request.acknowledgement.await()
                    mutableState.update { it.copy(phase = TestPhase.CONFIRMED) }
                }
            } catch (_: TimeoutCancellationException) {
                mutableState.update { it.copy(phase = TestPhase.TIMEOUT) }
                refreshConnection()
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(phase = TestPhase.CANCELLED) }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(phase = TestPhase.FAILED) }
                refreshConnection()
            } finally {
                pending = null
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == PresetProtocol.RESULT_PATH) {
            val result = PresetProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val pending = pendingPreset ?: return@launch
                if (source == selectedPhoneId && source == pending.nodeId && pending.request == result.request)
                    pending.result.complete(result)
            }
            return
        }
        if (event.path == LibraryProtocol.RESULT_PATH) {
            val result = LibraryProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val pending = pendingLibrary ?: return@launch
                if (source == selectedPhoneId && source == pending.nodeId && pending.request == result.request)
                    pending.result.complete(result)
            }
            return
        }
        if (event.path == EqualizerProtocol.RESULT_PATH) {
            val result = EqualizerProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val pending = pendingEqualizer ?: return@launch
                if (source == selectedPhoneId && pending.nodeId == source && pending.request == result.request)
                    pending.result.complete(result)
            }
            return
        }
        if (event.path == ControlsProtocol.RESULT_PATH) {
            val result = ControlsProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val pending = pendingControls ?: return@launch
                if (PhoneSession.acceptsReply(selectedPhoneId, pending.nodeId, source) && pending.request == result.request)
                    pending.result.complete(result)
            }
            return
        }
        if (event.path == VisualProtocol.RESULT_PATH) {
            val result = VisualProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val request = pendingSeek ?: return@launch
                if (PhoneSession.acceptsReply(selectedPhoneId, request.nodeId, source) && result.id == request.id)
                    request.result.complete(result)
            }
            return
        }
        if (event.path == NowPlayingProtocol.REFRESH_RESULT_PATH) {
            val result = NowPlayingProtocol.decodeSyncResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val request = pendingSync ?: return@launch
                if (PhoneSession.acceptsReply(selectedPhoneId, request.nodeId, source) && request.id == result.id)
                    request.result.complete(result)
            }
            return
        }
        if (event.path == PlayerProtocol.RESULT_PATH) {
            val result = PlayerProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val request = pendingPlayer ?: return@launch
                if (result.request != request.request || !PhoneSession.acceptsReply(selectedPhoneId, request.nodeId, source)) return@launch
                acceptSnapshot(result.snapshot)
                mutableState.update { it.copy(playerStage = result.stage) }
                if (result.stage !in setOf(PlayerProtocol.Stage.RECEIVED, PlayerProtocol.Stage.REQUESTED)) {
                    request.result.complete(result)
                }
            }
            return
        }
        if (event.path != TestProtocol.ACK_PATH) return
        val id = TestProtocol.decode(event.data) ?: return
        val source = event.sourceNodeId
        viewModelScope.launch {
            val request = pending ?: return@launch
            if (request.id == id && PhoneSession.acceptsReply(selectedPhoneId, request.nodeId, source))
                request.acknowledgement.complete(Unit)
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name in setOf(TestProtocol.CAPABILITY, PlayerProtocol.CAPABILITY, NowPlayingProtocol.CAPABILITY,
                VisualProtocol.CAPABILITY, ControlsProtocol.CAPABILITY, EqualizerProtocol.CAPABILITY,
                LibraryProtocol.CAPABILITY, PresetProtocol.CAPABILITY)) refreshConnection()
    }

    /** Ao sair da tela, não deixa um teste antigo continuar em segundo plano. */
    fun cancelTest() { testJob?.cancel(); playerJob?.cancel(); syncJob?.cancel(); seekJob?.cancel(); artJob?.cancel(); controlsJob?.cancel(); equalizerJob?.cancel(); libraryJob?.cancel(); presetJob?.cancel(); artKey = null }

    /** A composição informa qual tela está visível, inclusive após recriação da Activity. */
    fun setPlayerVisible(visible: Boolean) { playerVisible = visible }

    fun presetAction(intent: PresetIntent) {
        if (intent == PresetIntent.Close) { presetJob?.cancel(); return }
        if (mutableState.value.busy) return
        val before = mutableState.value.presets
        if (intent is PresetIntent.Select) {
            if (before.page?.items?.contains(intent.item) == true)
                mutableState.update { it.copy(presets = it.presets.copy(selected = intent.item, stage = null, failed = false)) }
            return
        }
        val page = before.page
        val afterId = when (intent) {
            PresetIntent.Next -> {
                if (page?.hasMore != true) return
                page.items.last().id
            }
            PresetIntent.Previous -> presetHistory.lastOrNull() ?: return
            else -> 0L
        }
        val selection = if (intent == PresetIntent.Apply) before.selected ?: return else null
        if (selection != null && page?.items?.contains(selection) != true) return
        val owner = selectedPhoneId
        val request = PresetProtocol.Request(UUID.randomUUID().toString(),
            if (selection == null) PresetProtocol.Action.LIST else PresetProtocol.Action.APPLY,
            afterId, selection?.id ?: 0)
        mutableState.update { it.copy(busy = true, presets = it.presets.copy(stage = null, failed = false)) }
        presetJob = viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    check(ensureListeners())
                    val phone = queryPhone()
                    check(phone != null && mutableState.value.presetsAvailable)
                    // Não reutiliza seleção ou cursor de outro celular.
                    check(PhoneSession.canSend(owner, phone.id, selection != null || afterId != 0L))
                    val waiting = PendingPreset(request, phone.id, CompletableDeferred())
                    pendingPreset = waiting
                    messages.sendMessage(phone.id, PresetProtocol.PATH, PresetProtocol.encode(request)).await()
                    val result = waiting.result.await()
                    if (selectedPhoneId != phone.id) return@withTimeout
                    if (result.stage == PresetProtocol.Stage.PAGE) {
                        presetHistory = when (intent) {
                            PresetIntent.Next -> (presetHistory + (page?.request?.afterId ?: 0)).takeLast(64)
                            PresetIntent.Previous -> presetHistory.dropLast(1)
                            else -> emptyList()
                        }
                        mutableState.update { it.copy(presets = it.presets.copy(page = result, selected = null,
                            stage = result.stage, canGoPrevious = presetHistory.isNotEmpty(),
                            canGoNext = result.hasMore)) }
                    } else mutableState.update { it.copy(presets = it.presets.copy(stage = result.stage,
                        lastRequested = if (result.stage in setOf(PresetProtocol.Stage.REQUESTED, PresetProtocol.Stage.OBSERVED))
                            selection else it.presets.lastRequested)) }
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException)
                    mutableState.update { it.copy(presets = it.presets.copy(failed = true)) }
                else throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(presets = it.presets.copy(failed = true)) }
            } finally {
                pendingPreset = null
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun libraryAction(intent: LibraryIntent) {
        if (intent == LibraryIntent.Close) { libraryJob?.cancel(); return }
        if (intent == LibraryIntent.Albums) {
            libraryJob?.cancel()
            mutableState.update { it.copy(library = it.library.copy(album = null, tracks = null, stage = null,
                failed = false, canGoPrevious = albumHistory.isNotEmpty())) }
            return
        }
        if (mutableState.value.busy) return
        if (intent == LibraryIntent.Open) {
            albumCursor = ""; trackCursor = ""; albumHistory = emptyList(); trackHistory = emptyList()
            mutableState.update { it.copy(library = LibraryState()) }
        }
        if (intent is LibraryIntent.OpenAlbum) {
            // Accepte apenas um ID que o Companion acabou de listar.
            if (mutableState.value.library.albums?.items?.none { it == intent.album } != false) return
            trackCursor = ""; trackHistory = emptyList()
            mutableState.update { it.copy(library = it.library.copy(album = intent.album, tracks = null,
                stage = null, failed = false, canGoPrevious = false)) }
        }
        val album = mutableState.value.library.album
        val tracks = album != null
        val page = if (tracks) mutableState.value.library.tracks else mutableState.value.library.albums
        val current = if (tracks) trackCursor else albumCursor
        val history = if (tracks) trackHistory else albumHistory
        val cursor = when (intent) {
            LibraryIntent.Next -> page?.next?.takeIf { it.isNotEmpty() } ?: return
            LibraryIntent.Previous -> history.lastOrNull() ?: return
            LibraryIntent.Refresh -> ""
            else -> current
        }
        val action = when (intent) {
            LibraryIntent.PlayAlbum -> if (album != null) LibraryProtocol.Action.PLAY_ALBUM else return
            is LibraryIntent.PlayTrack -> {
                if (album == null || page?.items?.none { it.id == intent.id } != false) return
                LibraryProtocol.Action.PLAY_TRACK
            }
            else -> if (tracks) LibraryProtocol.Action.TRACKS else LibraryProtocol.Action.ALBUMS
        }
        val request = LibraryProtocol.Request(UUID.randomUUID().toString(), action, album?.id ?: 0,
            if (intent is LibraryIntent.PlayTrack) intent.id else 0,
            if (LibraryProtocol.browsing(action)) cursor else "")
        val owner = selectedPhoneId
        mutableState.update { it.copy(busy = true, library = it.library.copy(stage = null, failed = false)) }
        libraryJob = viewModelScope.launch {
            try {
                withTimeout(35_000) {
                    check(ensureListeners())
                    val phone = queryPhone()
                    check(phone != null && mutableState.value.libraryAvailable)
                    check(PhoneSession.canSend(owner, phone.id, album != null || cursor.isNotEmpty()))
                    val waiting = PendingLibrary(request, phone.id, CompletableDeferred())
                    pendingLibrary = waiting
                    messages.sendMessage(phone.id, LibraryProtocol.PATH, LibraryProtocol.encode(request)).await()
                    val result = waiting.result.await()
                    if (selectedPhoneId != phone.id || (tracks && mutableState.value.library.album?.id != album?.id)) return@withTimeout
                    if (result.stage == LibraryProtocol.Stage.PAGE) {
                        val updatedHistory = when (intent) {
                            LibraryIntent.Next -> (history + current).takeLast(64)
                            LibraryIntent.Previous -> history.dropLast(1)
                            LibraryIntent.Refresh, LibraryIntent.Open -> emptyList()
                            else -> history
                        }
                        if (tracks) { trackCursor = cursor; trackHistory = updatedHistory }
                        else { albumCursor = cursor; albumHistory = updatedHistory }
                        mutableState.update { it.copy(library = it.library.copy(
                            albums = if (tracks) it.library.albums else result,
                            tracks = if (tracks) result else null, stage = result.stage,
                            canGoPrevious = updatedHistory.isNotEmpty())) }
                    } else mutableState.update { it.copy(library = it.library.copy(stage = result.stage)) }
                    if (!LibraryProtocol.browsing(action)) loadPlayerState(phone.id)
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) mutableState.update { it.copy(library = it.library.copy(failed = true)) }
                else throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(library = it.library.copy(failed = true)) }
            } finally {
                pendingLibrary = null
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun sendEqualizerAction(action: EqualizerProtocol.Action) {
        if (mutableState.value.busy) return
        val owner = selectedPhoneId
        mutableState.update { it.copy(busy = true, equalizerStage = null, equalizerFailed = false) }
        equalizerJob = viewModelScope.launch {
            try {
                withTimeout(12_000) {
                    check(ensureListeners())
                    val phone = queryPhone()
                    check(phone != null && mutableState.value.equalizerAvailable)
                    check(PhoneSession.canSend(owner, phone.id, action != EqualizerProtocol.Action.READ))
                    val request = EqualizerProtocol.Request(UUID.randomUUID().toString(), action)
                    val waiting = PendingEqualizer(request, phone.id, CompletableDeferred())
                    pendingEqualizer = waiting
                    messages.sendMessage(phone.id, EqualizerProtocol.PATH, EqualizerProtocol.encode(request)).await()
                    val result = waiting.result.await()
                    if (selectedPhoneId == phone.id) mutableState.update {
                        it.copy(equalizer = if (result.snapshot.reportedAt >= it.equalizer.reportedAt)
                            result.snapshot else it.equalizer, equalizerStage = result.stage)
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(equalizerFailed = true) }
                if (cancelled !is TimeoutCancellationException) throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(equalizerFailed = true) }
            } finally {
                pendingEqualizer = null
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun sendAdditionalControl(action: ControlsProtocol.Action) {
        if (mutableState.value.busy) return
        autoFailures = 0
        mutableState.update { it.copy(autoUpdatesFailed = false) }
        controlsJob = viewModelScope.launch { requestControls(action, automatic = false) }
    }

    private suspend fun requestControls(action: ControlsProtocol.Action, automatic: Boolean): Boolean? {
        val initial = mutableState.value
        if (initial.busy) return null
        val node = selectedPhoneId ?: return false
        val session = foregroundSession
        if (!initial.controlsAvailable || initial.connection != Connection.AVAILABLE) return false
        mutableState.update { it.copy(busy = true,
            controlsStage = if (automatic || action == ControlsProtocol.Action.QUERY) it.controlsStage else null,
            controlsAction = if (automatic || action == ControlsProtocol.Action.QUERY) it.controlsAction else action,
            controlsFailed = if (automatic) it.controlsFailed else false) }
        return try {
            withTimeout(12_000) {
                val request = ControlsProtocol.Request(UUID.randomUUID().toString(), action)
                val pending = PendingControls(request, node, CompletableDeferred())
                pendingControls = pending
                messages.sendMessage(node, ControlsProtocol.PATH, ControlsProtocol.encode(request)).await()
                val result = pending.result.await()
                check(node == selectedPhoneId)
                mutableState.update {
                    val old = it.controls
                    val fresh = result.snapshot
                    val modes = if (fresh.modesAt >= old.modesAt) fresh else
                        fresh.copy(shuffle = old.shuffle, repeat = old.repeat, modesAt = old.modesAt)
                    it.copy(controls = if (fresh.volumeAt >= old.volumeAt) modes else modes.copy(
                        volume = old.volume, minimum = old.minimum, maximum = old.maximum,
                        fixed = old.fixed, volumeAt = old.volumeAt),
                        controlsStage = if (action == ControlsProtocol.Action.QUERY) it.controlsStage else result.stage)
                }
                if (action == ControlsProtocol.Action.QUERY) {
                    loadPlayerState(node)
                    val report = result.position
                    val current = mutableState.value
                    if (report != null && session == foregroundSession && session != Long.MAX_VALUE &&
                        current.playerSnapshot.trackId == report.trackId && current.playerSnapshot.trackReportedAt == report.trackAt &&
                        current.playerSnapshot.state == PlayerProtocol.State.PLAYING &&
                        NowPlayingProtocol.matchesTrack(current.nowPlaying, report.trackId, report.trackAt)) {
                        val latest = current.nowPlaying
                        val useLatest = latest.positionAt > report.positionAt && latest.positionMs >= 0
                        val base = if (useLatest) latest.positionMs else report.positionMs
                        val at = if (useLatest) latest.positionAt else report.positionAt
                        estimate = Estimate(report.trackId, report.trackAt, base, SystemClock.elapsedRealtime(), at)
                        mutableState.update { it.copy(nowPlaying = if (useLatest) latest else latest.copy(
                            positionMs = report.positionMs, positionAt = report.positionAt, durationMs = report.durationMs)) }
                    }
                }
                result.stage !in setOf(ControlsProtocol.Stage.ERROR, ControlsProtocol.Stage.UNAVAILABLE)
            }
        } catch (cancelled: CancellationException) {
            if (!automatic) mutableState.update { it.copy(controlsFailed = true) }
            if (cancelled !is TimeoutCancellationException) throw cancelled
            false
        } catch (_: Exception) {
            if (!automatic) mutableState.update { it.copy(controlsFailed = true) }
            false
        } finally { pendingControls = null; mutableState.update { it.copy(busy = false) } }
    }

    /** Executado por repeatOnLifecycle(RESUMED); não mantém a tela ligada. */
    suspend fun runForegroundUpdates(): Unit = coroutineScope {
        val power = getApplication<Application>().getSystemService(PowerManager::class.java)
        foregroundSession = ++sessionSequence
        autoFailures = 0
        var nextQuery = SystemClock.elapsedRealtime() + 1500
        var queryJob: Job? = null
        var previouslyPlaying = false
        try {
            while (isActive) {
                val interactive = power.isInteractive
                if (!interactive || !playerVisible) {
                    queryJob?.cancel()
                    queryJob = null
                    estimate = null
                    foregroundSession = Long.MAX_VALUE
                    mutableState.update { it.copy(estimatedPositionMs = null, autoUpdatesActive = false) }
                } else {
                    if (foregroundSession == Long.MAX_VALUE) {
                        foregroundSession = ++sessionSequence
                        nextQuery = 0
                    }
                    val current = mutableState.value
                    mutableState.update { it.copy(autoUpdatesActive = current.controlsAvailable &&
                        current.connection == Connection.AVAILABLE && autoFailures < 2,
                        autoUpdatesFailed = autoFailures >= 2) }
                    val now = SystemClock.elapsedRealtime()
                    val anchor = estimate
                    val playing = current.playerSnapshot.state == PlayerProtocol.State.PLAYING
                    if (playing && !previouslyPlaying) nextQuery = 0
                    previouslyPlaying = playing
                    val predicted = if (anchor != null && playing && current.nowPlaying.durationMs > 0 && current.connection == Connection.AVAILABLE &&
                        anchor.trackId == current.playerSnapshot.trackId && anchor.trackAt == current.playerSnapshot.trackReportedAt)
                        ControlsProtocol.estimatedPosition(anchor.positionMs, current.nowPlaying.durationMs, now - anchor.receivedElapsed) else null
                    mutableState.update { it.copy(estimatedPositionMs = predicted) }
                    if (now >= nextQuery && queryJob?.isActive != true && autoFailures < 2 && current.controlsAvailable &&
                        current.connection == Connection.AVAILABLE && !current.busy) {
                        nextQuery = now + ControlsProtocol.AUTO_INTERVAL_MS
                        queryJob = launch {
                            val result = requestControls(ControlsProtocol.Action.QUERY, automatic = true)
                            if (result != null) autoFailures = if (result) 0 else autoFailures + 1
                        }
                    }
                }
                delay(1000)
            }
        } finally {
            queryJob?.cancel()
            estimate = null
            foregroundSession = Long.MAX_VALUE
            mutableState.update { it.copy(estimatedPositionMs = null, autoUpdatesActive = false) }
        }
    }

    fun seekTo(seconds: Int, trackId: Long, trackAt: Long) {
        val state = mutableState.value
        val owner = selectedPhoneId
        if (state.busy || !VisualProtocol.validTarget(VisualProtocol.Seek("", trackId, trackAt, seconds),
                state.playerSnapshot.trackId, state.playerSnapshot.trackReportedAt, state.nowPlaying.durationMs)) return
        mutableState.update { it.copy(busy = true, seekStage = null, seekFailed = false) }
        seekJob = viewModelScope.launch {
            try {
                withTimeout(15_000) {
                    check(ensureListeners())
                    val phone = queryPhone() ?: error("no phone")
                    check(PhoneSession.canSend(owner, phone.id, requiresOrigin = true))
                    check(capabilities.getCapability(VisualProtocol.CAPABILITY,
                        CapabilityClient.FILTER_REACHABLE).await().nodes.any { it.id == phone.id })
                    val request = VisualProtocol.Seek(UUID.randomUUID().toString(), trackId, trackAt, seconds)
                    val pending = PendingSeek(request.id, phone.id, CompletableDeferred())
                    pendingSeek = pending
                    messages.sendMessage(phone.id, VisualProtocol.SEEK_PATH, VisualProtocol.encode(request)).await()
                    val result = pending.result.await()
                    mutableState.update { it.copy(seekStage = result.stage) }
                    loadPlayerState(phone.id)
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(seekFailed = true) }
                if (cancelled !is TimeoutCancellationException) throw cancelled
            } catch (_: Exception) { mutableState.update { it.copy(seekFailed = true) } }
            finally { pendingSeek = null; mutableState.update { it.copy(busy = false) } }
        }
    }

    /** Atualização manual de posição/capa; callbacks de conexão apenas leem cache. */
    fun refreshNowPlaying() {
        if (mutableState.value.busy) return
        autoFailures = 0
        mutableState.update { it.copy(autoUpdatesFailed = false) }
        mutableState.update { it.copy(busy = true, syncStage = null, syncNotice = SyncNotice.LOADING) }
        syncJob = viewModelScope.launch {
            try {
                withTimeout(12_000) {
                    check(ensureListeners())
                    val phone = queryPhone()
                    if (phone == null) {
                        mutableState.update { it.copy(syncNotice = SyncNotice.NO_PHONE) }
                        return@withTimeout
                    }
                    if (!mutableState.value.metadataAvailable) {
                        mutableState.update { it.copy(syncNotice = SyncNotice.UPDATE_COMPANION) }
                        return@withTimeout
                    }
                    loadPlayerState(phone.id)
                    val pending = PendingSync(UUID.randomUUID().toString(), phone.id, CompletableDeferred())
                    pendingSync = pending
                    messages.sendMessage(phone.id, NowPlayingProtocol.REFRESH_PATH,
                        NowPlayingProtocol.encodeRefresh(pending.id)).await()
                    val result = pending.result.await()
                    mutableState.update { it.copy(syncStage = result.stage, syncNotice = SyncNotice.IDLE) }
                    if (result.stage in setOf(NowPlayingProtocol.SyncStage.ERROR, NowPlayingProtocol.SyncStage.NOT_INSTALLED))
                        autoFailures = 2
                    loadPlayerState(phone.id)
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) autoFailures = 2
                mutableState.update { it.copy(syncNotice = SyncNotice.FAILED) }
                if (cancelled !is TimeoutCancellationException) throw cancelled
            } catch (_: Exception) {
                autoFailures = 2
                mutableState.update { it.copy(syncNotice = SyncNotice.FAILED) }
            } finally {
                pendingSync = null
                mutableState.update { it.copy(busy = false) }
                val updated = mutableState.value
                if (updated.controlsAvailable && playerVisible && updated.connection == Connection.AVAILABLE &&
                    updated.syncNotice == SyncNotice.IDLE && updated.syncStage in setOf(
                        NowPlayingProtocol.SyncStage.SYNCED, NowPlayingProtocol.SyncStage.NO_POSITION) &&
                    foregroundSession != Long.MAX_VALUE &&
                    kotlinx.coroutines.currentCoroutineContext().isActive) sendAdditionalControl(ControlsProtocol.Action.QUERY)
            }
        }
    }

    fun sendPlayerCommand(command: PlayerProtocol.Command) {
        if (mutableState.value.busy) return
        val owner = selectedPhoneId
        mutableState.update { it.copy(busy = true, playerStage = null,
            playerNotice = PlayerNotice.SENDING, playerCommand = command) }
        playerJob = viewModelScope.launch {
            try {
                withTimeout(25_000) {
                    check(ensureListeners())
                    val phone = queryPhone()
                    if (phone == null) {
                        mutableState.update { it.copy(playerNotice = PlayerNotice.NO_PHONE) }
                        return@withTimeout
                    }
                    if (!mutableState.value.playerAvailable) {
                        mutableState.update { it.copy(playerNotice = PlayerNotice.INCOMPATIBLE) }
                        return@withTimeout
                    }
                    check(PhoneSession.canSend(owner, phone.id, requiresOrigin = true))
                    val request = PlayerProtocol.Request(UUID.randomUUID().toString(), command)
                    val waiting = PendingPlayer(request, phone.id, CompletableDeferred())
                    pendingPlayer = waiting
                    messages.sendMessage(phone.id, PlayerProtocol.COMMAND_PATH, PlayerProtocol.encodeRequest(request)).await()
                    mutableState.update { it.copy(playerNotice = PlayerNotice.WAITING) }
                    waiting.result.await()
                }
            } catch (_: TimeoutCancellationException) {
                mutableState.update { it.copy(playerStage = null, playerNotice = PlayerNotice.TIMEOUT) }
                refreshConnection()
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(playerStage = null, playerNotice = PlayerNotice.CANCELLED) }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(playerStage = null, playerNotice = PlayerNotice.FAILED) }
                refreshConnection()
            } finally {
                pendingPlayer = null
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun acceptSnapshot(snapshot: PlayerProtocol.Snapshot) {
        val current = mutableState.value.playerSnapshot
        if (snapshot.reportedAt >= current.reportedAt && snapshot.trackReportedAt >= current.trackReportedAt) {
            val changed = snapshot.trackId != current.trackId || snapshot.trackReportedAt != current.trackReportedAt
            if (changed || snapshot.state != PlayerProtocol.State.PLAYING) estimate = null
            if (changed) { artJob?.cancel(); artKey = null }
            mutableState.update { it.copy(playerSnapshot = snapshot, artwork = if (changed) null else it.artwork,
                seekStage = if (changed) null else it.seekStage) }
        }
    }

    private suspend fun loadPlayerState(nodeId: String) {
        try {
            val items = withTimeout(5_000) {
                dataClient.getDataItems(Uri.parse("wear://$nodeId${PlayerProtocol.STATE_PATH}")).await()
            }
            try {
                for (item in items) {
                    if (nodeId == selectedPhoneId) acceptDataMap(DataMapItem.fromDataItem(item).dataMap)
                }
            } finally { items.release() }
            val artItems = withTimeout(5_000) {
                dataClient.getDataItems(Uri.parse("wear://$nodeId${VisualProtocol.ART_PATH}")).await()
            }
            try {
                for (item in artItems) if (nodeId == selectedPhoneId) acceptArt(DataMapItem.fromDataItem(item).dataMap, nodeId)
            } finally { artItems.release() }
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
        } catch (_: Exception) {
            // Ausência de snapshot não impede o envio dos comandos.
        }
    }

    private fun acceptDataMap(data: DataMap) {
        val player = data.getByteArray("snapshot")?.let(PlayerProtocol::decodeSnapshot) ?: return
        acceptSnapshot(player)
        val metadata = data.getByteArray(NowPlayingProtocol.DATA_KEY)?.let(NowPlayingProtocol::decode) ?: return
        val state = mutableState.value
        if (metadata.metadataAt < state.nowPlaying.metadataAt) return
        val currentTrack = state.playerSnapshot
        if (!NowPlayingProtocol.matchesTrack(metadata, currentTrack.trackId, currentTrack.trackReportedAt)) return
        val merged = if (metadata.trackId == state.nowPlaying.trackId && metadata.positionAt < state.nowPlaying.positionAt)
            metadata.copy(positionMs = state.nowPlaying.positionMs, positionAt = state.nowPlaying.positionAt) else metadata
        val anchor = estimate
        if (anchor != null && merged.positionAt > anchor.sourceAt && foregroundSession != Long.MAX_VALUE &&
            currentTrack.state == PlayerProtocol.State.PLAYING && merged.positionMs >= 0 &&
            anchor.trackId == currentTrack.trackId && anchor.trackAt == currentTrack.trackReportedAt) {
            estimate = Estimate(currentTrack.trackId, currentTrack.trackReportedAt, merged.positionMs,
                SystemClock.elapsedRealtime(), merged.positionAt)
        }
        mutableState.update { it.copy(nowPlaying = merged, estimatedPositionMs = if (estimate == null) null else it.estimatedPositionMs) }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        // Copia os bytes enquanto o buffer da API ainda é válido.
        for (event in events) {
            val item = event.dataItem
            if (event.type == DataEvent.TYPE_CHANGED && item.uri.path == VisualProtocol.ART_PATH) {
                val source = item.uri.host ?: continue
                val copy = try {
                    val original = DataMapItem.fromDataItem(item).dataMap
                    DataMap().apply {
                        putLong("track_id", original.getLong("track_id", -1))
                        putLong("track_at", original.getLong("track_at", 0))
                        putBoolean("available", original.getBoolean("available"))
                        original.getAsset("cover")?.let { putAsset("cover", it) }
                    }
                }
                    catch (_: RuntimeException) { continue }
                viewModelScope.launch { if (source == selectedPhoneId) acceptArt(copy, source) }
                continue
            }
            if (event.type != DataEvent.TYPE_CHANGED || item.uri.path != PlayerProtocol.STATE_PATH) continue
            val source = item.uri.host
            val copiedData = try {
                val original = DataMapItem.fromDataItem(item).dataMap
                DataMap().apply {
                    original.getByteArray("snapshot")?.let { putByteArray("snapshot", it.copyOf()) }
                    original.getByteArray(NowPlayingProtocol.DATA_KEY)?.let {
                        putByteArray(NowPlayingProtocol.DATA_KEY, it.copyOf())
                    }
                }
            } catch (_: RuntimeException) { continue }
            viewModelScope.launch {
                if (source == selectedPhoneId) {
                    acceptDataMap(copiedData)
                    // Capa e estado usam DataItems distintos: tolera chegada em qualquer ordem.
                    if (source != null) loadPlayerState(source)
                }
            }
        }
    }

    override fun onCleared() {
        cleared = true
        messages.removeListener(this)
        capabilities.removeListener(this, TestProtocol.CAPABILITY)
        capabilities.removeListener(this, PlayerProtocol.CAPABILITY)
        capabilities.removeListener(this, NowPlayingProtocol.CAPABILITY)
        capabilities.removeListener(this, VisualProtocol.CAPABILITY)
        capabilities.removeListener(this, ControlsProtocol.CAPABILITY)
        capabilities.removeListener(this, EqualizerProtocol.CAPABILITY)
        capabilities.removeListener(this, LibraryProtocol.CAPABILITY)
        capabilities.removeListener(this, PresetProtocol.CAPABILITY)
        dataClient.removeListener(this)
        super.onCleared()
    }

    private fun acceptArt(data: DataMap, nodeId: String) {
        val trackId = data.getLong("track_id", -1)
        val trackAt = data.getLong("track_at", 0)
        val player = mutableState.value.playerSnapshot
        if (trackId != player.trackId || trackAt != player.trackReportedAt) return
        val asset = data.getAsset("cover")
        val key = "$nodeId:$trackId:$trackAt:${asset?.digest}:${data.getBoolean("available") }"
        if (artKey == key) return
        artJob?.cancel()
        artKey = key
        mutableState.update { it.copy(artwork = null) }
        if (!data.getBoolean("available") || asset == null) return
        artJob = viewModelScope.launch {
            val bitmap = try {
                withTimeout(5_000) { loadArt(asset) }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                null
            } catch (_: Exception) { null }
            val current = mutableState.value.playerSnapshot
            if (nodeId == selectedPhoneId && current.trackId == trackId && current.trackReportedAt == trackAt && artKey == key) {
                mutableState.update { it.copy(artwork = bitmap) }
                if (bitmap == null) artKey = null
            }
        }
    }

    private suspend fun loadArt(asset: Asset): Bitmap? = withContext(Dispatchers.IO) {
        val response = dataClient.getFdForAsset(asset).await()
        try {
            response.inputStream?.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count: Int
                while (stream.read(buffer).also { count = it } != -1) {
                    output.write(buffer, 0, count)
                    if (output.size() > VisualProtocol.MAX_IMAGE_BYTES) return@withContext null
                }
                val bytes = output.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..256 || bounds.outHeight !in 1..256) return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } finally { response.release() }
    }
}
