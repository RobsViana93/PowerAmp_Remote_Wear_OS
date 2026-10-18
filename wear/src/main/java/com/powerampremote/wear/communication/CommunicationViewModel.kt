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
    val playerCommand: PlayerProtocol.Command? = null
)
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
                testNodes to playerNodes
            }
            nodes = discovery.first
            val phone = nodes.sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()
            if (phone?.id != selectedPhoneId) {
                mutableState.update { it.copy(playerSnapshot = PlayerProtocol.Snapshot()) }
            }
            selectedPhoneId = phone?.id
            mutableState.update {
                it.copy(connection = if (nodes.isEmpty()) Connection.UNAVAILABLE else Connection.AVAILABLE,
                    playerAvailable = discovery.second.any { node -> node.id == phone?.id })
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
        if (info.name in setOf(TestProtocol.CAPABILITY, PlayerProtocol.CAPABILITY)) refreshConnection()
    }

    /** Ao sair da tela, não deixa um teste antigo continuar em segundo plano. */
    fun cancelTest() { testJob?.cancel(); playerJob?.cancel() }

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
                    val bytes = DataMapItem.fromDataItem(item).dataMap.getByteArray("snapshot") ?: continue
                    val snapshot = PlayerProtocol.decodeSnapshot(bytes) ?: continue
                    if (nodeId == selectedPhoneId) acceptSnapshot(snapshot)
                }
            } finally { items.release() }
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
        } catch (_: Exception) {
            // Ausência de snapshot não impede o envio dos comandos.
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        // Copia os bytes enquanto o buffer da API ainda é válido.
        for (event in events) {
            val item = event.dataItem
            if (event.type != DataEvent.TYPE_CHANGED || item.uri.path != PlayerProtocol.STATE_PATH) continue
            val source = item.uri.host
            val snapshot = try {
                val bytes = DataMapItem.fromDataItem(item).dataMap.getByteArray("snapshot") ?: continue
                PlayerProtocol.decodeSnapshot(bytes) ?: continue
            } catch (_: RuntimeException) { continue }
            viewModelScope.launch { if (source == selectedPhoneId) acceptSnapshot(snapshot) }
        }
    }

    override fun onCleared() {
        cleared = true
        messages.removeListener(this)
        capabilities.removeListener(this, TestProtocol.CAPABILITY)
        capabilities.removeListener(this, PlayerProtocol.CAPABILITY)
        dataClient.removeListener(this)
        super.onCleared()
    }
}
