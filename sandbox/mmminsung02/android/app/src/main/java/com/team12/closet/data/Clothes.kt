package com.team12.closet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 서버 POST /v1/clothes 응답 */
@Serializable
data class UploadResponse(
    val id: Long,
    val category: String,
    val color: String,
    val source: String,
    val confirmed: Boolean,
    @SerialName("needs_category") val needsCategory: Boolean,
    val duplicate: Boolean,
    val message: String? = null,
)

@Serializable
data class ErrorResponse(
    val detail: String,
)

/** 사용자가 고른 사진 한 장 */
data class PickedPhoto(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?) = other is PickedPhoto && name == other.name && bytes.contentEquals(other.bytes)

    override fun hashCode() = 31 * name.hashCode() + bytes.contentHashCode()
}

data class RegisteredCloth(
    val id: Long,
    val category: String,
    val color: String,
    val confirmed: Boolean,
    val needsCategory: Boolean,
)

sealed interface UploadOutcome {
    data class Registered(
        val cloth: RegisteredCloth,
        val duplicate: Boolean,
    ) : UploadOutcome

    /** 서버가 사진을 받지 않음 (이미지가 아님, 너무 큼). 다시 시도해도 같다 */
    data class Rejected(
        val message: String,
    ) : UploadOutcome

    /** 연결 실패 등. 다시 시도할 수 있다 */
    data class Failed(
        val message: String,
    ) : UploadOutcome
}

object ClothLabels {
    val categories =
        mapOf(
            "top" to "상의",
            "bottom" to "하의",
            "outer" to "아우터",
            "other" to "기타",
        )

    // 서버 server/domain.py COLORS와 같은 목록
    val colors =
        mapOf(
            "black" to "블랙",
            "white" to "화이트",
            "gray" to "그레이",
            "navy" to "네이비",
            "blue" to "블루",
            "beige" to "베이지",
            "brown" to "브라운",
            "khaki" to "카키",
            "green" to "그린",
            "red" to "레드",
            "pink" to "핑크",
            "purple" to "퍼플",
            "yellow" to "옐로",
            "orange" to "오렌지",
            "multi" to "멀티(패턴)",
        )

    // 필터 칩 순서 (시안: 전체 / 아우터 / 상의 / 하의 / 기타)
    val categoryOrder = listOf("outer", "top", "bottom", "other")

    // 색 동그라미. 앞의 10개는 docs/app-sitemap/index.html 시안 값, 나머지 5개는 시안에 없어 새로 정함
    val swatches =
        mapOf(
            "black" to 0xFF1C1C22,
            "white" to 0xFFF4F4F0,
            "gray" to 0xFF9A9AA2,
            "navy" to 0xFF26345A,
            "blue" to 0xFF3F6FD1,
            "beige" to 0xFFD8C4A3,
            "brown" to 0xFF7A5233,
            "khaki" to 0xFF6E7445,
            "pink" to 0xFFE7849E,
            "red" to 0xFFC9373D,
            "green" to 0xFF3E8E5A,
            "purple" to 0xFF7B5BB5,
            "yellow" to 0xFFE8C547,
            "orange" to 0xFFE98A3B,
            "multi" to 0xFFB0A8C8,
        )

    fun category(code: String) = categories[code] ?: code

    fun color(code: String) = colors[code] ?: code
}
