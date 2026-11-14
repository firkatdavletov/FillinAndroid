package com.firkat.fillin.feature.categories.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CategoriesViewModel(private val getCategories: GetCategories) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        load()
    }

    fun onEvent(
        event: CategoriesEvent
    ) {
        when (event) {
            CategoriesEvent.Retry -> {
                load()
            }

            CategoriesEvent.Refresh -> {
                load(isRefreshing = true)
            }
        }
    }

    private fun load(
        isRefreshing: Boolean = false
    ) {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return

        _uiState.update {
            it.copy(isLoading = !isRefreshing, isRefreshing = isRefreshing, error = null)
        }
        loadJob?.cancel()
        if (isRefreshing) getCategories.requestSync()
        loadJob =
            viewModelScope.launch {
                try {
                    getCategories.observe().collect { categories ->
                        _uiState.update {
                            it.copy(
                                items = categories,
                                isLoading = false,
                                isRefreshing = false
                            )
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: CategoriesException) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = exception.toUiError()
                        )
                    }
                } catch (exception: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = CategoriesError.Unknown
                        )
                    }
                }
            }
    }
}
