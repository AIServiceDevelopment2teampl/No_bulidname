package com.team12.closet

import com.team12.closet.data.ApiResult
import com.team12.closet.data.InMemorySessionStore
import com.team12.closet.data.ServerConnection
import com.team12.closet.data.Session
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 앱이 서버 유사 검색 응답(sandbox/mmminsung02/src/server/services/similar.py)을 제대로 읽는지 */
class NetworkProductsRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repository() = ServerConnection(server.url("/").toString(), InMemorySessionStore(Session("t"))).products

    private fun respond(
        code: Int,
        body: String,
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    @Test
    fun parsesResultsAndCallsSimilarEndpoint() =
        runTest {
            respond(
                200,
                """{"status":"ok","items":[{"cloth":{"id":4,"image_url":"/v1/clothes/4/image","category":"outer",""" +
                    """"color":"beige","name":"카멜 코트","source":"photo","confirmed":true},""" +
                    """"image_similarity":0.95,"level":"very_similar"}]}""",
            )

            val result = (repository().findSimilar(12) as ApiResult.Ok).value

            assertEquals("ok", result.status)
            assertEquals(4L, result.items[0].cloth.id)
            assertEquals("very_similar", result.items[0].level)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/products/12/similar", request.url.encodedPath)
        }

    @Test
    fun missingProductPhotoAndUnknownProductMapToMessages() =
        runTest {
            respond(400, """{"detail":"상품 사진을 찾지 못했어요"}""")
            respond(404, """{"detail":"상품을 찾을 수 없어요"}""")

            assertEquals(ApiResult.Error("상품 사진을 찾지 못했어요"), repository().findSimilar(1))
            assertEquals(ApiResult.Error("상품을 찾을 수 없어요"), repository().findSimilar(2))
        }

    // 기능 7: 상품 정보 구조화

    @Test
    fun extractSendsPageJsonUnchangedAndParsesFields() =
        runTest {
            respond(
                201,
                """{"id":41,"url":"https://zozo.jp/x","site":"zozo.jp","lang":"ja","category":"top",""" +
                    """"image_url":"https://img/x.jpg","has_image":true,"fields":{"name":{"original":"ニット","ko":"니트"},""" +
                    """"material":null,"size":{"original":"25 in","ko":"25 in","converted":"63.5 cm (25 in)"}}}""",
            )
            val page = """{"url":"https://zozo.jp/x","json_ld":[[{"@type":"Product"}]],"og":{},"text":"ニット"}"""

            val product = (repository().extract(page) as ApiResult.Ok).value

            assertEquals("니트", product.field("name")?.ko)
            assertEquals(null, product.field("material"))
            assertEquals("63.5 cm (25 in)", product.field("size")?.converted)
            val request = server.takeRequest()
            assertEquals("/v1/products/extract", request.url.encodedPath)
            // 앱은 페이지 데이터를 해석하지 않고 그대로 넘긴다
            assertEquals(page, request.body!!.utf8())
            assertEquals("Bearer t", request.headers["Authorization"])
        }

    @Test
    fun extractFailureBecomesMessage() =
        runTest {
            respond(422, """{"detail":[]}""")

            assertEquals(ApiResult.Error("상품 정보를 읽지 못했어요"), repository().extract("{}"))
        }
}
