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
    val syncNotice: SyncNotice = SyncNotice.IDLE
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
                Triple(testNodes, playerNodes, metadataNodes)
            }
            nodes = discovery.first
            val phone = nodes.sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()
            if (phone?.id != selectedPhoneId) {
                mutableState.update { it.copy(playerSnapshot = PlayerProtocol.Snapshot(),
                    nowPlaying = NowPlayingProtocol.Snapshot(), syncStage = null, syncNotice = SyncNotice.IDLE) }
            }
            selectedPhoneId = phone?.id
            mutableState.update {
                it.copy(connection = if (nodes.isEmpty()) Connection.UNAVAILABLE else Connection.AVAILABLE,
                    playerAvailable = discovery.second.any { node -> node.id == phone?.id },
                    metadataAvailable = discovery.third.any { node -> node.id == phone?.id })
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
        if (info.name in setOf(TestProtocol.CAPABILITY, PlayerProtocol.CAPABILITY, NowPlayingProtocol.CAPABILITY)) refreshConnection()
    }

    /** Ao sair da tela, não deixa um teste antigo continuar em segundo plano. */
    fun cancelTest() { testJob?.cancel(); playerJob?.cancel(); syncJob?.cancel() }

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
            mutableState.update { it.copy(playerSnapshot = snapshot) }
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
            viewModelScope.launch { if (source == selectedPhoneId) acceptDataMap(copiedData) }
        }
    }

    override fun onCleared() {
        cleared = true
        messages.removeListener(this)
        capabilities.removeListener(this, TestProtocol.CAPABILITY)
        capabilities.removeListener(this, PlayerProtocol.CAPABILITY)
        capabilities.removeListener(this, NowPlayingProtocol.CAPABILITY)
        dataClient.removeListener(this)
        super.onCleared()
    }
}
