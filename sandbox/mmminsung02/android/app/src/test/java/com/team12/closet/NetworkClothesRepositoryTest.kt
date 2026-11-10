package com.team12.closet

import com.team12.closet.FakeClothesRepository.Companion.photo
import com.team12.closet.data.ApiResult
import com.team12.closet.data.ClothFilter
import com.team12.closet.data.ClothUpdate
import com.team12.closet.data.InMemorySessionStore
import com.team12.closet.data.NetworkClothesRepository
import com.team12.closet.data.ServerConnection
import com.team12.closet.data.Session
import com.team12.closet.data.UploadOutcome
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 앱이 서버 응답(sandbox/mmminsung02/src/server/api/clothes.py)을 제대로 읽는지 */
class NetworkClothesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: NetworkClothesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = ServerConnection(server.url("/").toString(), InMemorySessionStore(Session("test-token"))).clothes
    }

    @After
    fun tearDown() {
        server.close()
    }

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
    fun createdResponseBecomesRegistered() =
        runTest {
            respond(
                201,
                """{"id":7,"category":"top","color":"black","source":"photo",""" +
                    """"confirmed":false,"needs_category":false,"duplicate":false}""",
            )

            val outcome = repository.uploadPhoto(photo("shirt.jpg"))

            outcome as UploadOutcome.Registered
            assertEquals(7L, outcome.cloth.id)
            assertEquals("top", outcome.cloth.category)
            assertEquals(false, outcome.duplicate)
            val request = server.takeRequest()
            assertEquals("/v1/clothes", request.url.encodedPath)
            assertTrue(request.body!!.utf8().contains("name=\"image\"; filename=\"shirt.jpg\""))
        }

    @Test
    fun duplicateResponseIsMarkedDuplicate() =
        runTest {
            respond(
                200,
                """{"id":7,"category":"top","color":"black","source":"photo","confirmed":false,""" +
                    """"needs_category":false,"duplicate":true,"message":"이미 등록된 사진이에요"}""",
            )

            val outcome = repository.uploadPhoto(photo("shirt.jpg")) as UploadOutcome.Registered

            assertTrue(outcome.duplicate)
        }

    @Test
    fun badRequestBecomesRejectedWithServerMessage() =
        runTest {
            respond(400, """{"detail":"이미지를 읽을 수 없어요"}""")

            val outcome = repository.uploadPhoto(photo("note.txt"))

            assertEquals(UploadOutcome.Rejected("이미지를 읽을 수 없어요"), outcome)
        }

    @Test
    fun tooLargeBecomesRejected() =
        runTest {
            respond(413, """{"detail":"사진 용량이 너무 커요"}""")

            val outcome = repository.uploadPhoto(photo("big.jpg"))

            assertEquals(UploadOutcome.Rejected("사진 용량이 너무 커요"), outcome)
        }

    @Test
    fun serverErrorBecomesFailed() =
        runTest {
            respond(500, "oops")

            val outcome = repository.uploadPhoto(photo("a.jpg"))

            assertTrue(outcome is UploadOutcome.Failed)
        }

    @Test
    fun connectionFailureBecomesFailed() =
        runTest {
            server.close()

            val outcome = repository.uploadPhoto(photo("a.jpg"))

            assertEquals(UploadOutcome.Failed("서버에 연결하지 못했어요"), outcome)
        }

    // 기능 2: 옷장 조회·수정·삭제

    @Test
    fun listSendsFilterAndParsesItems() =
        runTest {
            respond(
                200,
                """{"items":[{"id":3,"image_url":"/v1/clothes/3/image","category":"top","color":"black",""" +
                    """"name":null,"brand":"UNIQLO","material":null,"size":"M","source":"photo","confirmed":false,""" +
                    """"needs_category":false,"needs_photo":false,"created_at":"2026-11-04 10:00:00"}],"total":1}""",
            )

            val result = repository.listClothes(ClothFilter(category = "top", color = "black"))

            val page = (result as ApiResult.Ok).value
            assertEquals(1, page.total)
            assertEquals("UNIQLO", page.items[0].brand)
            assertNull(page.items[0].name)
            val request = server.takeRequest()
            assertEquals("/v1/clothes", request.url.encodedPath)
            assertEquals("top", request.url.queryParameter("category"))
            assertEquals("black", request.url.queryParameter("color"))
        }

    @Test
    fun listWithoutFilterSendsNoQuery() =
        runTest {
            respond(200, """{"items":[],"total":0}""")

            repository.listClothes(ClothFilter())

            assertNull(server.takeRequest().url.query)
        }

    @Test
    fun updateSendsOnlyGivenFieldsAndParsesCloth() =
        runTest {
            respond(
                200,
                """{"id":3,"category":"outer","color":"navy","name":"코트","source":"photo","confirmed":true}""",
            )

            val result = repository.updateCloth(3, ClothUpdate(category = "outer", name = ""))

            assertEquals("outer", (result as ApiResult.Ok).value.category)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/v1/clothes/3", request.url.encodedPath)
            assertEquals("""{"category":"outer","name":""}""", request.body!!.utf8())
        }

    @Test
    fun updateWithInvalidValueReturnsServerMessage() =
        runTest {
            respond(400, """{"detail":"알 수 없는 종류예요: shoes"}""")

            val result = repository.updateCloth(3, ClothUpdate(category = "shoes"))

            assertEquals(ApiResult.Error("알 수 없는 종류예요: shoes"), result)
        }

    @Test
    fun deleteOfMissingClothReturnsNotFound() =
        runTest {
            respond(404, """{"detail":"옷을 찾을 수 없어요"}""")

            assertEquals(ApiResult.Error("옷을 찾을 수 없어요"), repository.deleteCloth(9))
        }

    @Test
    fun deleteAndDeleteAllUseDeleteMethod() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())
            server.enqueue(MockResponse.Builder().code(204).build())

            assertEquals(ApiResult.Ok(Unit), repository.deleteCloth(3))
            assertEquals(ApiResult.Ok(Unit), repository.deleteAllMyData())

            server.takeRequest().let {
                assertEquals("DELETE", it.method)
                assertEquals("/v1/clothes/3", it.url.encodedPath)
            }
            server.takeRequest().let {
                assertEquals("DELETE", it.method)
                assertEquals("/v1/me/data", it.url.encodedPath)
            }
        }

    @Test
    fun imageUrlIsJoinedWithServerAddress() {
        val url = repository.imageUrl("/v1/clothes/3/image")

        assertEquals(server.url("/v1/clothes/3/image").toString(), url)
        assertNull(repository.imageUrl(null))
    }
}
