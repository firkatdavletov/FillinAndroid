package com.firkat.fillin.feature.appaccess.ui

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AboutAppViewModel(private val access: AppAccess) : ViewModel() {
    private val activeInstallationId = MutableStateFlow<String?>(null)
    val installationId = activeInstallationId.asStateFlow()
    val checking = access.checking
    private val result = MutableStateFlow<Boolean?>(null)
    val refreshResult = result.asStateFlow()

    fun open() {
        viewModelScope.launch(Dispatchers.IO) {
            activeInstallationId.value = runCatching { access.installationId() }.getOrDefault("")
        }
    }

    fun close() {
        activeInstallationId.value = null
        result.value = null
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { result.value = access.refresh(manual = true) }
    }
}
