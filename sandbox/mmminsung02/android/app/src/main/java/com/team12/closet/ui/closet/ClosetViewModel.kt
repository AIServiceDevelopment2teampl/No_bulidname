package com.team12.closet.ui.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team12.closet.data.ApiResult
import com.team12.closet.data.Cloth
import com.team12.closet.data.ClothFilter
import com.team12.closet.data.ClothUpdate
import com.team12.closet.data.ClothesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClosetUiState(
    val filter: ClothFilter = ClothFilter(),
    val items: List<Cloth> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

class ClosetViewModel(
    private val repository: ClothesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClosetUiState())
    val state: StateFlow<ClosetUiState> = _state.asStateFlow()

    /** 화면(ClosetScreen)에 들어올 때마다 부른다 */
    fun load() {
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.listClothes(filter)) {
                is ApiResult.Ok -> {
                    // 그 사이 필터가 또 바뀌었으면 늦게 온 결과는 버린다
                    if (_state.value.filter != filter) return@launch
                    _state.update {
                        it.copy(items = result.value.items, total = result.value.total, loading = false)
                    }
                }

                is ApiResult.Error -> {
                    _state.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    /** 같은 칩을 다시 누르면 필터를 푼다 */
    fun selectCategory(category: String?) {
        _state.update { it.copy(filter = it.filter.copy(category = category)) }
        load()
    }

    fun selectColor(color: String?) {
        val next = if (_state.value.filter.color == color) null else color
        _state.update { it.copy(filter = it.filter.copy(color = next)) }
        load()
    }

    fun cloth(id: Long): Cloth? = _state.value.items.firstOrNull { it.id == id }

    suspend fun save(
        id: Long,
        update: ClothUpdate,
    ): ApiResult<Cloth> {
        val result = repository.updateCloth(id, update)
        if (result is ApiResult.Ok) load()
        return result
    }

    suspend fun delete(id: Long): ApiResult<Unit> {
        val result = repository.deleteCloth(id)
        if (result is ApiResult.Ok) load()
        return result
    }

    fun imageUrl(cloth: Cloth): String? = repository.imageUrl(cloth.imageUrl)
}
