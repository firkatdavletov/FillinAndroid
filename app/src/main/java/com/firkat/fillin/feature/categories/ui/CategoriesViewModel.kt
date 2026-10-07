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

class CategoriesViewModel(
    private val getCategories: GetCategories,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            CategoriesEvent.Retry -> load()
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val categories = getCategories()
                _uiState.update {
                    it.copy(
                        items = categories,
                        isLoading = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CategoriesException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.toUiError(),
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = CategoriesError.Unknown,
                    )
                }
            }
        }
    }
}

private fun CategoriesException.toUiError(): CategoriesError = when (this) {
    CategoriesException.MissingApiToken -> CategoriesError.MissingApiToken
    CategoriesException.Unauthorized -> CategoriesError.Unauthorized
    CategoriesException.Forbidden -> CategoriesError.Forbidden
    CategoriesException.Network -> CategoriesError.Network
    CategoriesException.InvalidResponse -> CategoriesError.InvalidResponse
    CategoriesException.Unknown -> CategoriesError.Unknown
}
