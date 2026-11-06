package com.example.roombooking.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.roombooking.AppContainer
import com.example.roombooking.RoomBookingApp

/** Every screen that loads data shows exactly one of these (UX requirement: Loading / Error / Empty). */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun <T> Result<List<T>>.toListState(): UiState<List<T>> = fold(
    onSuccess = { if (it.isEmpty()) UiState.Empty else UiState.Success(it) },
    onFailure = { UiState.Error(it.message ?: "เกิดข้อผิดพลาด") },
)

fun <T> Result<T>.toState(): UiState<T> = fold(
    onSuccess = { UiState.Success(it) },
    onFailure = { UiState.Error(it.message ?: "เกิดข้อผิดพลาด") },
)

/**
 * Builds a ViewModel with access to the AppContainer and the navigation arguments
 * (SavedStateHandle contains route args such as "roomId").
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (container: AppContainer, handle: SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RoomBookingApp
        create(app.container, createSavedStateHandle())
    }
}
