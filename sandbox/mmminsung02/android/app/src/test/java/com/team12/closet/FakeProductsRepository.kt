package com.team12.closet

import com.team12.closet.FakeClothesRepository.Companion.cloth
import com.team12.closet.data.ApiResult
import com.team12.closet.data.PickedPhoto
import com.team12.closet.data.ProductField
import com.team12.closet.data.ProductInfo
import com.team12.closet.data.ProductsRepository
import com.team12.closet.data.PurchaseReport
import com.team12.closet.data.ReportItem
import com.team12.closet.data.ScreenshotResult
import com.team12.closet.data.SimilarCloth
import com.team12.closet.data.SimilarResult
import kotlinx.coroutines.CompletableDeferred

class FakeProductsRepository(
    var extractResult: ApiResult<ProductInfo> = ApiResult.Ok(SAMPLE_PRODUCT),
    var similarResult: ApiResult<SimilarResult> = ApiResult.Ok(SAMPLE_SIMILAR),
    var reportResult: ApiResult<PurchaseReport> = ApiResult.Ok(SAMPLE_REPORT),
    var screenshotResult: ApiResult<ScreenshotResult> = ApiResult.Ok(SAMPLE_SCREENSHOT),
) : ProductsRepository {
    val extracted = mutableListOf<String>()
    val similarFor = mutableListOf<Long>()
    val reportFor = mutableListOf<Long>()
    val screenshots = mutableListOf<PickedPhoto>()

    /** 설정하면 다음 extract가 이 값이 완료될 때까지 기다린다 */
    var holdExtract: CompletableDeferred<Unit>? = null

    override suspend fun extract(pageJson: String): ApiResult<ProductInfo> {
        extracted += pageJson
        holdExtract?.let {
            holdExtract = null
            it.await()
        }
        return extractResult
    }

    override suspend fun findSimilar(productId: Long): ApiResult<SimilarResult> {
        similarFor += productId
        return similarResult
    }

    override suspend fun report(productId: Long): ApiResult<PurchaseReport> {
        reportFor += productId
        return reportResult
    }

    override suspend fun fromScreenshot(photo: PickedPhoto): ApiResult<ScreenshotResult> {
        screenshots += photo
        return screenshotResult
    }

    companion object {
        val SAMPLE_PRODUCT =
            ProductInfo(
                id = 41,
                url = "https://zozo.jp/shop/beams/goods/7781/",
                site = "zozo.jp",
                lang = "ja",
                category = "top",
                hasImage = true,
                fields =
                    mapOf(
                        "name" to ProductField("ウールブレンド クルーネック カーディガン", "울 블렌드 크루넥 카디건"),
                        "price" to ProductField("8800", "8800 JPY"),
                        "color" to ProductField("チャコール", "차콜"),
                        "material" to ProductField("ウール50% アクリル50%", "울 50%, 아크릴 50%"),
                        "fit" to null,
                        "size" to ProductField("Length: 25 in", "총장: 25 in", "총장: 63.5 cm (25 in)"),
                        "care" to ProductField("手洗い可", "손세탁 가능"),
                    ),
            )

        val SAMPLE_SIMILAR =
            SimilarResult(
                "ok",
                listOf(
                    SimilarCloth(cloth(7, "top", "gray", "회색 카디건"), 0.93, "very_similar"),
                    SimilarCloth(cloth(8, "top", "white", "흰 니트"), 0.71, "different"),
                ),
            )

        val SAMPLE_REPORT =
            PurchaseReport(
                id = 5,
                productId = 41,
                status = "ok",
                headline = "가장 비슷한 내 옷과 비교했어요",
                cloth = cloth(7, "top", "gray", "회색 카디건"),
                level = "very_similar",
                summary = "회색 카디건과 종류·색이 비슷하고, 소재는 내 옷 정보가 없어요.",
                similar =
                    listOf(
                        ReportItem("category", "종류", "상의", "상의", text = "둘 다 상의예요."),
                        ReportItem("color", "색", "차콜", "그레이", "page", "둘 다 그레이 계열이에요."),
                    ),
                unknown = listOf(ReportItem("material", "소재", "울 50%, 아크릴 50%", null, text = "내 옷의 소재 정보가 없어요.")),
            )

        val SAMPLE_SCREENSHOT =
            ScreenshotResult(
                product = SAMPLE_PRODUCT.copy(id = 42, url = null, imagePath = "/v1/products/42/image"),
                similar = SAMPLE_SIMILAR,
                report = SAMPLE_REPORT.copy(productId = 42),
            )
    }
}
