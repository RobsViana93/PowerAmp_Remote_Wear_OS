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
    val seekFailed: Boolean = false
)
enum class SyncNotice { IDLE, LOADING, UPDATE_COMPANION, FAILED }
enum class PlayerNotice { IDLE, SENDING, WAITING, TIMEOUT, FAILED, CANCELLED, INCOMPATIBLE }
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
                messages.addListener(this@CommunicationViewModel)
                    .addOnSuccessListener { if (cleared) messages.removeListener(this@CommunicationViewModel) }
                    .await()
                capabilities.addListener(this@CommunicationViewModel, TestProtocol.CAPABILITY)
                    .addOnSuccessListener {
                        if (cleared) capabilities.removeListener(this@CommunicationViewModel, TestProtocol.CAPABILITY)
                    }.await()
                capabilities.addListener(this@CommunicationViewModel, PlayerProtocol.CAPABILITY)
                    .addOnSuccessListener {
                        if (cleared) capabilities.removeListener(this@CommunicationViewModel, PlayerProtocol.CAPABILITY)
                    }.await()
                capabilities.addListener(this@CommunicationViewModel, NowPlayingProtocol.CAPABILITY)
                    .addOnSuccessListener {
                        if (cleared) capabilities.removeListener(this@CommunicationViewModel, NowPlayingProtocol.CAPABILITY)
                    }.await()
                capabilities.addListener(this@CommunicationViewModel, VisualProtocol.CAPABILITY)
                    .addOnSuccessListener { if (cleared) capabilities.removeListener(this@CommunicationViewModel, VisualProtocol.CAPABILITY) }.await()
                dataClient.addListener(this@CommunicationViewModel)
                    .addOnSuccessListener { if (cleared) dataClient.removeListener(this@CommunicationViewModel) }
                    .await()
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
                val testNodes = capabilities.getCapability(TestProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                val playerNodes = capabilities.getCapability(PlayerProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                val metadataNodes = capabilities.getCapability(NowPlayingProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                val visualNodes = capabilities.getCapability(VisualProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                listOf(testNodes, playerNodes, metadataNodes, visualNodes)
            }
            nodes = discovery[0]
            val phone = nodes.sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()
            if (phone?.id != selectedPhoneId) {
                mutableState.update { it.copy(playerSnapshot = PlayerProtocol.Snapshot(),
                    nowPlaying = NowPlayingProtocol.Snapshot(), artwork = null, syncStage = null, syncNotice = SyncNotice.IDLE) }
                artJob?.cancel()
                artKey = null
            }
            selectedPhoneId = phone?.id
            mutableState.update {
                it.copy(connection = if (nodes.isEmpty()) Connection.UNAVAILABLE else Connection.AVAILABLE,
                    playerAvailable = discovery[1].any { node -> node.id == phone?.id },
                    metadataAvailable = discovery[2].any { node -> node.id == phone?.id },
                    visualAvailable = discovery[3].any { node -> node.id == phone?.id })
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
        if (event.path == VisualProtocol.RESULT_PATH) {
            val result = VisualProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val request = pendingSeek ?: return@launch
                if (source == request.nodeId && result.id == request.id) request.result.complete(result)
            }
            return
        }
        if (event.path == NowPlayingProtocol.REFRESH_RESULT_PATH) {
            val result = NowPlayingProtocol.decodeSyncResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val request = pendingSync ?: return@launch
                if (request.nodeId == source && request.id == result.id) request.result.complete(result)
            }
            return
        }
        if (event.path == PlayerProtocol.RESULT_PATH) {
            val result = PlayerProtocol.decodeResult(event.data) ?: return
            val source = event.sourceNodeId
            viewModelScope.launch {
                val request = pendingPlayer ?: return@launch
                if (result.request != request.request || source != request.nodeId) return@launch
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
            if (request.id == id && request.nodeId == source) request.acknowledgement.complete(Unit)
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name in setOf(TestProtocol.CAPABILITY, PlayerProtocol.CAPABILITY, NowPlayingProtocol.CAPABILITY,
                VisualProtocol.CAPABILITY)) refreshConnection()
    }

    /** Ao sair da tela, não deixa um teste antigo continuar em segundo plano. */
    fun cancelTest() { testJob?.cancel(); playerJob?.cancel(); syncJob?.cancel(); seekJob?.cancel(); artJob?.cancel(); artKey = null }

    fun seekTo(seconds: Int, trackId: Long, trackAt: Long) {
        val state = mutableState.value
        if (state.busy || !VisualProtocol.validTarget(VisualProtocol.Seek("", trackId, trackAt, seconds),
                state.playerSnapshot.trackId, state.playerSnapshot.trackReportedAt, state.nowPlaying.durationMs)) return
        mutableState.update { it.copy(busy = true, seekStage = null, seekFailed = false) }
        seekJob = viewModelScope.launch {
            try {
                withTimeout(15_000) {
                    check(ensureListeners())
                    val phone = queryPhone() ?: error("no phone")
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

    /** Somente o botão Atualizar solicita POS_SYNC; callbacks de conexão apenas leem cache. */
    fun refreshNowPlaying() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, syncStage = null, syncNotice = SyncNotice.LOADING) }
        syncJob = viewModelScope.launch {
            try {
                withTimeout(12_000) {
                    check(ensureListeners())
                    val phone = queryPhone()
                    if (phone == null || !mutableState.value.metadataAvailable) {
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
                    loadPlayerState(phone.id)
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(syncNotice = SyncNotice.FAILED) }
                if (cancelled !is TimeoutCancellationException) throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(syncNotice = SyncNotice.FAILED) }
            } finally {
                pendingSync = null
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    fun sendPlayerCommand(command: PlayerProtocol.Command) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, playerStage = null,
            playerNotice = PlayerNotice.SENDING, playerCommand = command) }
        playerJob = viewModelScope.launch {
            try {
                withTimeout(25_000) {
                    check(ensureListeners())
                    val phone = queryPhone()
                    if (phone == null || !mutableState.value.playerAvailable) {
                        mutableState.update { it.copy(playerNotice = PlayerNotice.INCOMPATIBLE) }
                        return@withTimeout
                    }
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
        mutableState.update { it.copy(nowPlaying = merged) }
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
