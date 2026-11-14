package com.moagauge.pleos.insurance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Called on the UI dispatcher; owns provider-scoped asynchronous work and confirmation state. */
class InsuranceSession(
    private val source: InsuranceContractSource,
    private val scope: CoroutineScope,
    initialProvider: InsuranceProvider? = null,
    private val persistProvider: (InsuranceProvider) -> Unit = {},
    private val restoreUsage: (InsuranceContract) -> InsuranceRoadsideUsage? = { null },
    private val persistUsage: (InsuranceContract) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(InsuranceUiState())
    val state = mutableState.asStateFlow()
    private var connectionJob: Job? = null
    private var requestJob: Job? = null
    private var connectionGeneration = 0L
    private var requestGeneration = 0L

    init { initialProvider?.let(::connect) }

    fun connect(provider: InsuranceProvider) {
        val generation = ++connectionGeneration
        connectionJob?.cancel()
        dismissRequest()
        mutableState.value = InsuranceUiState(provider = provider, selectingProvider = false,
            connectionStatus = InsuranceConnectionStatus.CONNECTING)
        persistProvider(provider)
        connectionJob = scope.launch {
            try {
                var contract = source.fetchContract(provider)
                require(contract.provider == provider && !contract.endDate.isBefore(contract.startDate)
                    && contract.productName.isNotBlank())
                if (generation == connectionGeneration) {
                    if (contract.source == ContractSource.DEMO) {
                        val baseline = contract.roadsideUsage
                        val restored = runCatching { restoreUsage(contract) }.getOrNull()
                        if (baseline != null && restored != null && restored.totalAllowed == baseline.totalAllowed
                            && RoadsideService.entries.all { restored.usedFor(it) >= baseline.usedFor(it) }) {
                            contract = contract.copy(roadsideUsage = restored)
                        }
                    }
                    mutableState.value = mutableState.value.copy(connectionStatus = InsuranceConnectionStatus.CONNECTED,
                        contract = contract, message = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == connectionGeneration) {
                    mutableState.value = mutableState.value.copy(connectionStatus = InsuranceConnectionStatus.FAILED,
                        contract = null, message = "계약 정보를 연결하지 못했어요. 다시 시도해 주세요.")
                }
            }
        }
    }

    fun retryConnection() { state.value.provider?.let(::connect) }

    fun showProviderPicker() {
        dismissRequest()
        mutableState.value = mutableState.value.copy(selectingProvider = true)
    }

    fun cancelProviderPicker() {
        if (state.value.provider != null) mutableState.value = mutableState.value.copy(selectingProvider = false)
    }

    fun openRequest(service: RoadsideService) {
        val current = state.value
        val provider = current.provider ?: return
        if (current.selectingProvider || current.connectionStatus != InsuranceConnectionStatus.CONNECTED
            || current.contract?.provider != provider) return
        dismissRequest()
        if (current.contract.roadsideUsage?.remaining == 0) {
            mutableState.value = mutableState.value.copy(message = "이 보험기간의 긴급출동 공통 횟수를 모두 사용했어요.")
            return
        }
        mutableState.value = mutableState.value.copy(request = InsuranceRequestState(provider, service, InsuranceRequestPhase.READY))
    }

    fun submitRequest() {
        val current = state.value
        val request = current.request ?: return
        val contract = current.contract ?: return
        if (current.selectingProvider || current.connectionStatus != InsuranceConnectionStatus.CONNECTED
            || request.provider != current.provider
            || request.phase !in setOf(InsuranceRequestPhase.READY, InsuranceRequestPhase.FAILED)) return
        if (contract.roadsideUsage?.remaining == 0) return
        val generation = ++requestGeneration
        mutableState.value = current.copy(request = request.copy(phase = InsuranceRequestPhase.SUBMITTING, message = null))
        requestJob = scope.launch {
            try {
                val receipt = source.requestService(request.provider, request.service)
                require(receipt.provider == request.provider && receipt.service == request.service
                    && receipt.source == contract.source && receipt.requestId.isNotBlank())
                require(receipt.source != ContractSource.DEMO || receipt.requestId.startsWith("DEMO-"))
                if (generation == requestGeneration) {
                    val usage = contract.roadsideUsage
                    val updated = if (usage != null) contract.copy(roadsideUsage =
                        requireNotNull(usage.consume(receipt.service, receipt.requestId))) else contract
                    if (updated.source == ContractSource.DEMO && updated != contract) persistUsage(updated)
                    mutableState.value = mutableState.value.copy(contract = updated, request = request.copy(
                        phase = InsuranceRequestPhase.COMPLETED, result = receipt))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == requestGeneration) {
                    mutableState.value = mutableState.value.copy(request = request.copy(
                        phase = InsuranceRequestPhase.FAILED, message = "요청을 완료하지 못했어요. 다시 시도해 주세요."))
                }
            }
        }
    }

    fun dismissRequest() {
        ++requestGeneration
        requestJob?.cancel()
        requestJob = null
        mutableState.value = mutableState.value.copy(request = null)
    }
}
