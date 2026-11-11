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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 앱이 서버 AI 점원 응답(sandbox/mmminsung02/src/server/api/clerk.py)을 제대로 읽는지 */
class NetworkClerkRepositoryTest {
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

    private fun repository() = ServerConnection(server.url("/").toString(), InMemorySessionStore(Session("t"))).clerk

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
    fun askSendsProductAndTextAndParsesReply() =
        runTest {
            respond(201, """{"message":{"id":8,"role":"assistant","content":"손세탁할 수 있어요.","created_at":"2026-11-11"}}""")

            val reply = (repository().ask(41, "세탁은?") as ApiResult.Ok).value

            assertEquals("손세탁할 수 있어요.", reply.content)
            val request = server.takeRequest()
            assertEquals("/v1/clerk/messages", request.url.encodedPath)
            val body = request.body!!.utf8()
            assertTrue(body, body.contains("\"product_id\":41") && body.contains("세탁은?"))
        }

    @Test
    fun generalQuestionSendsNullProduct() =
        runTest {
            respond(201, """{"message":{"id":8,"role":"assistant","content":"네"}}""")

            repository().ask(null, "안녕")

            // null은 JSON에서 빠진다 (서버 기본값 None = 일반 대화)
            val body = server.takeRequest().body!!.utf8()
            assertTrue(body, body.contains("안녕") && !body.contains("\"product_id\":4"))
        }

    @Test
    fun historyUsesProductQuery() =
        runTest {
            respond(
                200,
                """{"items":[{"id":1,"role":"user","content":"이거 두꺼워?"},""" +
                    """{"id":2,"role":"assistant","content":"페이지에 그 정보가 없어요"}]}""",
            )

            val items = (repository().history(41) as ApiResult.Ok).value

            assertEquals(listOf("user", "assistant"), items.map { it.role })
            assertEquals("41", server.takeRequest().url.queryParameter("product_id"))
        }

    @Test
    fun matchingParsesClothesAndMessage() =
        runTest {
            respond(
                200,
                """{"status":"ok","message":null,"items":[{"cloth":{"id":3,"image_url":"/v1/clothes/3/image","category":"bottom",""" +
                    """"color":"navy","name":"청바지","source":"photo","confirmed":true},"reason":"잘 어울려요"}]}""",
            )

            val result = (repository().matching(41) as ApiResult.Ok).value

            assertEquals(3L, result.items[0].cloth.id)
            assertEquals("/v1/products/41/matching", server.takeRequest().url.encodedPath)
        }

    @Test
    fun errorsShowServerMessage() =
        runTest {
            respond(502, """{"detail":"답하지 못했어요. 잠시 후 다시 시도해 주세요"}""")
            respond(429, """{"detail":"요청이 너무 많아요. 잠시 후 다시 시도해 주세요"}""")

            assertEquals(ApiResult.Error("답하지 못했어요. 잠시 후 다시 시도해 주세요"), repository().ask(null, "안녕"))
            assertEquals(ApiResult.Error("요청이 너무 많아요. 잠시 후 다시 시도해 주세요"), repository().ask(null, "안녕"))
        }
}
