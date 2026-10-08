package com.powerampremote.wear.communication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
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
    val busy: Boolean = false
)
enum class Connection { CHECKING, AVAILABLE, UNAVAILABLE, ERROR }
enum class TestPhase { IDLE, SENDING, WAITING_ACK, CONFIRMED, NO_PHONE, FAILED, TIMEOUT, CANCELLED }

/** Mantém o teste e seu estado durante recomposições e mudanças de configuração. */
class CommunicationViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    private val messages = Wearable.getMessageClient(application)
    private val capabilities = Wearable.getCapabilityClient(application)
    private val mutableState = MutableStateFlow(CommunicationState())
    val state = mutableState.asStateFlow()
    private val registrationMutex = Mutex()
    private var listenersReady = false
    private val refreshMutex = Mutex()
    private var nodes: Set<Node> = emptySet()
    private var testJob: Job? = null
    private var pending: PendingTest? = null
    @Volatile private var cleared = false

    private data class PendingTest(
        val id: String,
        val nodeId: String,
        val acknowledgement: CompletableDeferred<Unit>
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
            if (ensureListeners()) queryPhone()
        }
    }

    private suspend fun queryPhone(): Node? = refreshMutex.withLock {
        mutableState.update { it.copy(connection = Connection.CHECKING) }
        try {
            nodes = withTimeout(5_000) {
                capabilities.getCapability(TestProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
            }
            mutableState.update {
                it.copy(connection = if (nodes.isEmpty()) Connection.UNAVAILABLE else Connection.AVAILABLE)
            }
            // Prefere o celular próximo; escolha determinística se houver mais de um.
            nodes.sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()
        } catch (_: TimeoutCancellationException) {
            nodes = emptySet()
            mutableState.update { it.copy(connection = Connection.ERROR) }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            nodes = emptySet()
            mutableState.update { it.copy(connection = Connection.ERROR) }
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
        if (event.path != TestProtocol.ACK_PATH) return
        val id = TestProtocol.decode(event.data) ?: return
        val source = event.sourceNodeId
        viewModelScope.launch {
            val request = pending ?: return@launch
            if (request.id == id && request.nodeId == source) request.acknowledgement.complete(Unit)
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == TestProtocol.CAPABILITY) refreshConnection()
    }

    /** Ao sair da tela, não deixa um teste antigo continuar em segundo plano. */
    fun cancelTest() { testJob?.cancel() }

    override fun onCleared() {
        cleared = true
        messages.removeListener(this)
        capabilities.removeListener(this, TestProtocol.CAPABILITY)
        super.onCleared()
    }
}
