package com.moadrive.pleos.insurance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InsuranceViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("insurance", 0)
    private val mutableProvider = MutableStateFlow(InsuranceProvider.entries.find { it.name == preferences.getString("provider", null) })
    val provider = mutableProvider.asStateFlow()
    private val usageStore = InsuranceUsageStore(preferences)

    private val session = InsuranceSession(DemoInsuranceContractSource(), viewModelScope,
        initialProvider = mutableProvider.value, persistProvider = { selected ->
            preferences.edit().putString("provider", selected.name).apply()
            mutableProvider.value = selected
        }, restoreUsage = usageStore::restore, persistUsage = usageStore::save)
    val state = session.state

    fun connect(provider: InsuranceProvider) = session.connect(provider)
    fun showProviderPicker() = session.showProviderPicker()
    fun cancelProviderPicker() = session.cancelProviderPicker()
    fun retryConnection() = session.retryConnection()
    fun openRequest(service: RoadsideService) = session.openRequest(service)
    fun submitRequest() = session.submitRequest()
    fun dismissRequest() = session.dismissRequest()
}
