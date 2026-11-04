package com.team12.closet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 서버 GET /v1/clothes 의 옷 하나. 모르는 값은 null (= 정보 없음) */
@Serializable
data class Cloth(
    val id: Long,
    @SerialName("image_url") val imageUrl: String? = null,
    val category: String,
    val color: String,
    val name: String? = null,
    val brand: String? = null,
    val material: String? = null,
    val size: String? = null,
    val source: String,
    val confirmed: Boolean,
    @SerialName("needs_category") val needsCategory: Boolean = false,
    @SerialName("needs_photo") val needsPhoto: Boolean = false,
)

@Serializable
data class ClothesPage(
    val items: List<Cloth>,
    val total: Int,
)

/** PATCH /v1/clothes/{id}. null인 값은 보내지 않는다 (빈 글자 ""는 지우기) */
@Serializable
data class ClothUpdate(
    val category: String? = null,
    val color: String? = null,
    val name: String? = null,
    val brand: String? = null,
    val material: String? = null,
    val size: String? = null,
)

data class ClothFilter(
    val category: String? = null,
    val color: String? = null,
)

sealed interface ApiResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : ApiResult<T>

    data class Error(
        val message: String,
    ) : ApiResult<Nothing>
}
