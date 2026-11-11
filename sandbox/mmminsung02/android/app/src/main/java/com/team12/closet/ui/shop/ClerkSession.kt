package com.team12.closet.ui.shop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ClerkMessage
import com.team12.closet.data.ClerkRepository
import com.team12.closet.data.MatchingResult

sealed interface MatchingState {
    data object Loading : MatchingState

    data class Ready(
        val result: MatchingResult,
    ) : MatchingState

    data class Failed(
        val message: String,
    ) : MatchingState
}

/**
 * 상품 하나(또는 상품 페이지가 아닌 곳)의 AI 점원 대화와 어울리는 옷 (compare/ai-clerk.md).
 * 패널을 처음 열 때 한 번만 불러온다. 다시 열어도 서버에 다시 묻지 않는다
 */
@Stable
class ClerkSession(
    private val repository: ClerkRepository,
    val productId: Long?,
) {
    var messages by mutableStateOf<List<ClerkMessage>>(emptyList())
        private set

    /** 답을 기다리는 질문 */
    var pending by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var matching by mutableStateOf<MatchingState?>(null)
        private set

    private var opened = false

    suspend fun open() {
        if (opened) return
        opened = true
        if (productId != null) matching = MatchingState.Loading
        (repository.history(productId) as? ApiResult.Ok)?.let { messages = it.value + messages }
        productId?.let { id ->
            matching =
                when (val result = repository.matching(id)) {
                    is ApiResult.Ok -> MatchingState.Ready(result.value)
                    is ApiResult.Error -> MatchingState.Failed(result.message)
                }
        }
    }

    /** 성공하면 true (입력칸을 비운다). 실패하면 질문은 저장되지 않으니 입력칸에 그대로 둔다 */
    suspend fun send(text: String): Boolean {
        val question = text.trim()
        if (question.isEmpty() || pending != null) return false
        pending = question
        error = null
        val result = repository.ask(productId, question)
        pending = null
        return when (result) {
            is ApiResult.Ok -> {
                messages = messages + ClerkMessage(-1, "user", question) + result.value
                true
            }
            is ApiResult.Error -> {
                error = result.message
                false
            }
        }
    }
}
