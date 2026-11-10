package com.team12.closet.ui.shop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ProductInfo
import com.team12.closet.data.ProductsRepository
import com.team12.closet.data.SimilarResult

/** 브라우저에서 감지한 상품 하나의 상태: 상품 정보 → 비슷한 옷 */
sealed interface ProductState {
    data object Loading : ProductState

    data class Ready(
        val product: ProductInfo,
        /** null이면 비슷한 옷을 찾지 못함 (similarError에 이유) */
        val similar: SimilarResult?,
        val similarError: String? = null,
    ) : ProductState

    data class Failed(
        val message: String,
    ) : ProductState
}

@Stable
class ProductLookup(
    private val repository: ProductsRepository,
) {
    var state by mutableStateOf<ProductState?>(null)
        private set

    private var currentPage: String? = null

    /** 새 페이지로 가면 이전 상품 결과를 지운다 */
    fun clear() {
        currentPage = null
        state = null
    }

    suspend fun onProductPage(pageJson: String) {
        if (pageJson == currentPage) return
        currentPage = pageJson
        state = ProductState.Loading
        val product =
            when (val result = repository.extract(pageJson)) {
                is ApiResult.Ok -> result.value
                is ApiResult.Error -> {
                    if (currentPage == pageJson) state = ProductState.Failed(result.message)
                    return
                }
            }
        val next =
            if (!product.hasImage) {
                ProductState.Ready(product, similar = null, similarError = "상품 사진을 찾지 못했어요")
            } else {
                when (val similar = repository.findSimilar(product.id)) {
                    is ApiResult.Ok -> ProductState.Ready(product, similar.value)
                    is ApiResult.Error -> ProductState.Ready(product, similar = null, similarError = similar.message)
                }
            }
        // 그 사이 다른 페이지로 갔으면 늦게 온 결과는 버린다
        if (currentPage == pageJson) state = next
    }
}
