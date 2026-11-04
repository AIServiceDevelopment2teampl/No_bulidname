package com.team12.closet

import com.team12.closet.FakeClothesRepository.Companion.photo
import com.team12.closet.data.NetworkClothesRepository
import com.team12.closet.data.UploadOutcome
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
        repository = NetworkClothesRepository.create(server.url("/").toString())
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
}
