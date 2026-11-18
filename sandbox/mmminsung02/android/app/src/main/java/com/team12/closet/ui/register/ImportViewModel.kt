package com.team12.closet.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ImportKind
import com.team12.closet.data.ImportsRepository
import com.team12.closet.data.PickedPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState

    data class Reading(
        val photos: Int,
    ) : ImportUiState

    data class Review(
        val picks: ImportPicks,
        val photos: Int,
        val saving: Boolean = false,
        val error: String? = null,
    ) : ImportUiState

    data class Saved(
        val saved: Int,
        val skipped: Int,
    ) : ImportUiState

    data class Failed(
        val message: String,
    ) : ImportUiState
}

/** 주문내역 캡처(기능 13)·영수증(기능 15): 사진 고르기 → 후보 확인 → 고른 것만 저장 */
class ImportViewModel(
    private val repository: ImportsRepository,
    val kind: ImportKind,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun read(photos: List<PickedPhoto>) {
        if (photos.isEmpty()) return
        _state.value = ImportUiState.Reading(photos.size)
        viewModelScope.launch {
            _state.value =
                when (val result = repository.fromPhotos(kind, photos)) {
                    is ApiResult.Ok -> ImportUiState.Review(ImportPicks(result.value), photos.size)
                    is ApiResult.Error -> ImportUiState.Failed(result.message)
                }
        }
    }

    fun save() {
        val review = _state.value as? ImportUiState.Review ?: return
        if (!review.picks.canSave || review.saving) return
        _state.value = review.copy(saving = true, error = null)
        viewModelScope.launch {
            _state.value =
                when (val result = repository.confirm(review.picks.batch.id, review.picks.choices())) {
                    is ApiResult.Ok -> ImportUiState.Saved(result.value.saved.size, result.value.skipped.size)
                    is ApiResult.Error -> review.copy(saving = false, error = result.message)
                }
        }
    }

    /** 확인 화면을 닫으면 후보를 버린다 (서버의 후보 사진도 지워진다) */
    fun cancel() {
        val review = _state.value as? ImportUiState.Review
        _state.value = ImportUiState.Idle
        if (review != null) viewModelScope.launch { repository.discard(review.picks.batch.id) }
    }
}
