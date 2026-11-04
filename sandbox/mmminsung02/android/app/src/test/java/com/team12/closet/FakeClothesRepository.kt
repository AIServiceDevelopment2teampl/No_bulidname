package com.team12.closet

import com.team12.closet.data.ClothesRepository
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.RegisteredCloth
import com.team12.closet.data.UploadOutcome

/** 서버 대신 쓰는 가짜 저장소. 사진 이름별로 돌려줄 결과를 정해 둔다 */
class FakeClothesRepository : ClothesRepository {
    val outcomes = mutableMapOf<String, ArrayDeque<UploadOutcome>>()
    val uploaded = mutableListOf<PickedPhoto>()

    fun willReturn(
        name: String,
        vararg results: UploadOutcome,
    ) {
        outcomes[name] = ArrayDeque(results.toList())
    }

    override suspend fun uploadPhoto(photo: PickedPhoto): UploadOutcome {
        uploaded += photo
        return outcomes[photo.name]?.removeFirstOrNull() ?: registered(category = "top", color = "black")
    }

    companion object {
        fun registered(
            category: String,
            color: String,
            needsCategory: Boolean = false,
            duplicate: Boolean = false,
        ) = UploadOutcome.Registered(
            cloth = RegisteredCloth(1, category, color, confirmed = false, needsCategory = needsCategory),
            duplicate = duplicate,
        )

        fun photo(name: String) = PickedPhoto(name, "image/jpeg", byteArrayOf(1, 2, 3))
    }
}
