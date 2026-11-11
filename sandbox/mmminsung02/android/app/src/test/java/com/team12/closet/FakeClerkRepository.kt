package com.team12.closet

import com.team12.closet.FakeClothesRepository.Companion.cloth
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ClerkMessage
import com.team12.closet.data.ClerkRepository
import com.team12.closet.data.MatchingCloth
import com.team12.closet.data.MatchingResult

class FakeClerkRepository : ClerkRepository {
    var historyResult: ApiResult<List<ClerkMessage>> = ApiResult.Ok(emptyList())
    var askResult: ApiResult<ClerkMessage> = ApiResult.Ok(ClerkMessage(2, "assistant", "손세탁할 수 있어요."))
    var matchingResult: ApiResult<MatchingResult> = ApiResult.Ok(SAMPLE_MATCHING)
    val asked = mutableListOf<Pair<Long?, String>>()
    val historyFor = mutableListOf<Long?>()
    val matchingFor = mutableListOf<Long>()

    override suspend fun history(productId: Long?): ApiResult<List<ClerkMessage>> {
        historyFor += productId
        return historyResult
    }

    override suspend fun ask(
        productId: Long?,
        text: String,
    ): ApiResult<ClerkMessage> {
        asked += productId to text
        return askResult
    }

    override suspend fun matching(productId: Long): ApiResult<MatchingResult> {
        matchingFor += productId
        return matchingResult
    }

    companion object {
        val SAMPLE_MATCHING =
            MatchingResult(
                status = "ok",
                items =
                    listOf(
                        MatchingCloth(cloth(3, "bottom", "navy", "청바지"), "어두운 톤끼리 잘 어울려요"),
                        MatchingCloth(cloth(5, "outer", "beige", "카멜 코트"), "부드러운 색 조합이에요"),
                    ),
            )
    }
}
