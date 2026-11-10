package com.team12.closet

import com.team12.closet.data.ApiResult
import com.team12.closet.data.Cloth
import com.team12.closet.data.ClothFilter
import com.team12.closet.data.ClothUpdate
import com.team12.closet.data.ClothesPage
import com.team12.closet.data.ClothesRepository
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.RegisteredCloth
import com.team12.closet.data.UploadOutcome
import kotlinx.coroutines.CompletableDeferred

/** 서버 대신 쓰는 가짜 저장소. 옷장은 메모리 목록이고, 필터·수정·삭제를 서버처럼 처리한다 */
class FakeClothesRepository(
    clothes: List<Cloth> = emptyList(),
) : ClothesRepository {
    val clothes = clothes.toMutableList()
    val outcomes = mutableMapOf<String, ArrayDeque<UploadOutcome>>()
    val uploaded = mutableListOf<PickedPhoto>()
    val listRequests = mutableListOf<ClothFilter>()
    var deleteAllCalls = 0
    var failNextList: String? = null

    /** 설정하면 다음 목록 요청이 이 값이 완료될 때까지 기다린다 (불러오는 중 화면 확인용) */
    var holdNextList: CompletableDeferred<Unit>? = null

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

    override suspend fun listClothes(filter: ClothFilter): ApiResult<ClothesPage> {
        listRequests += filter
        holdNextList?.let {
            holdNextList = null
            it.await()
        }
        failNextList?.let {
            failNextList = null
            return ApiResult.Error(it)
        }
        val items =
            clothes
                .filter { filter.category == null || it.category == filter.category }
                .filter { filter.color == null || it.color == filter.color }
                .sortedByDescending { it.id }
        return ApiResult.Ok(ClothesPage(items, items.size))
    }

    override suspend fun updateCloth(
        id: Long,
        update: ClothUpdate,
    ): ApiResult<Cloth> {
        val index = clothes.indexOfFirst { it.id == id }
        if (index < 0) return ApiResult.Error("옷을 찾을 수 없어요")
        val old = clothes[index]

        fun text(
            new: String?,
            current: String?,
        ) = if (new == null) current else new.trim().ifEmpty { null }
        val updated =
            old.copy(
                category = update.category ?: old.category,
                color = update.color ?: old.color,
                name = text(update.name, old.name),
                brand = text(update.brand, old.brand),
                material = text(update.material, old.material),
                size = text(update.size, old.size),
                confirmed = true,
                needsCategory = false,
            )
        clothes[index] = updated
        return ApiResult.Ok(updated)
    }

    override suspend fun deleteCloth(id: Long): ApiResult<Unit> =
        if (clothes.removeIf { it.id == id }) ApiResult.Ok(Unit) else ApiResult.Error("옷을 찾을 수 없어요")

    override suspend fun deleteAllMyData(): ApiResult<Unit> {
        deleteAllCalls++
        clothes.clear()
        return ApiResult.Ok(Unit)
    }

    override fun imageUrl(path: String?): String? = null

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

        fun cloth(
            id: Long,
            category: String,
            color: String,
            name: String? = null,
            confirmed: Boolean = true,
            needsPhoto: Boolean = false,
        ) = Cloth(
            id = id,
            imageUrl = if (needsPhoto) null else "/v1/clothes/$id/image",
            category = category,
            color = color,
            name = name,
            source = "photo",
            confirmed = confirmed,
            needsPhoto = needsPhoto,
        )
    }
}
