package com.team12.closet.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team12.closet.data.ClothesRepository
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.UploadOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UploadItem(
    val photo: PickedPhoto,
    val outcome: UploadOutcome?,
)

sealed interface RegisterUiState {
    data object Idle : RegisterUiState

    data class Uploading(
        val done: Int,
        val total: Int,
    ) : RegisterUiState

    data class Done(
        val items: List<UploadItem>,
    ) : RegisterUiState
}

class RegisterPhotoViewModel(
    private val repository: ClothesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    /** 고른 사진을 한 장씩 차례로 올린다 (서버는 요청 하나에 사진 1장) */
    fun upload(photos: List<PickedPhoto>) {
        if (photos.isEmpty()) return
        viewModelScope.launch {
            val items = mutableListOf<UploadItem>()
            photos.forEachIndexed { index, photo ->
                _state.value = RegisterUiState.Uploading(done = index, total = photos.size)
                items += UploadItem(photo, repository.uploadPhoto(photo))
            }
            _state.value = RegisterUiState.Done(items.toList())
        }
    }

    fun retry(photo: PickedPhoto) {
        viewModelScope.launch {
            val outcome = repository.uploadPhoto(photo)
            _state.update { current ->
                if (current !is RegisterUiState.Done) return@update current
                current.copy(items = current.items.map { if (it.photo == photo) it.copy(outcome = outcome) else it })
            }
        }
    }

    fun reset() {
        _state.value = RegisterUiState.Idle
    }
}
