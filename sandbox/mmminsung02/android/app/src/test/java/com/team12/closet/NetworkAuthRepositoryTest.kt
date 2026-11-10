package com.team12.closet

import com.team12.closet.data.ApiResult
import com.team12.closet.data.ClothFilter
import com.team12.closet.data.InMemorySessionStore
import com.team12.closet.data.ServerConnection
import com.team12.closet.data.Session
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 앱이 서버 로그인 API(sandbox/mmminsung02/src/server/api/auth.py)와 세션 헤더를 제대로 다루는지 */
class NetworkAuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemorySessionStore()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun connection() = ServerConnection(server.url("/").toString(), store)

    private fun respond(
        code: Int,
        body: String = "",
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    @Test
    fun loginSendsIdTokenAndSavesSession() =
        runTest {
            respond(200, """{"user_id":"u1","session_token":"s-123"}""")
            val conn = connection()

            val result = conn.auth.loginWithGoogle("google-token", "홍길동", "a@b.com")

            assertEquals(ApiResult.Ok(Unit), result)
            assertEquals(Session("s-123", "홍길동", "a@b.com"), store.load())
            assertEquals(Session("s-123", "홍길동", "a@b.com"), conn.auth.session.value)
            val request = server.takeRequest()
            assertEquals("/v1/auth/google", request.url.encodedPath)
            assertEquals("""{"id_token":"google-token"}""", request.body!!.utf8())
        }

    @Test
    fun rejectedGoogleTokenDoesNotSaveSession() =
        runTest {
            respond(401, """{"detail":"Google 로그인을 확인하지 못했어요"}""")

            val result = connection().auth.loginWithGoogle("bad", null, null)

            assertTrue(result is ApiResult.Error)
            assertNull(store.load())
        }

    @Test
    fun serverWithoutClientIdShowsSetupMessage() =
        runTest {
            respond(503, """{"detail":"서버에 Google 로그인 설정이 없어요"}""")

            val result = connection().auth.loginWithGoogle("t", null, null)

            assertEquals(ApiResult.Error("서버에 Google 로그인 설정이 아직 없어요"), result)
        }

    @Test
    fun requestsCarryBearerSessionToken() =
        runTest {
            store.save(Session("s-abc"))
            respond(200, """{"items":[],"total":0}""")

            connection().clothes.listClothes(ClothFilter())

            assertEquals("Bearer s-abc", server.takeRequest().headers["Authorization"])
        }

    @Test
    fun noSessionMeansNoAuthorizationHeader() =
        runTest {
            respond(200, """{"items":[],"total":0}""")

            connection().clothes.listClothes(ClothFilter())

            assertNull(server.takeRequest().headers["Authorization"])
        }

    @Test
    fun serverSaying401ClearsSessionSoAppShowsLogin() =
        runTest {
            store.save(Session("expired"))
            respond(401, """{"detail":"로그인이 만료됐어요. 다시 로그인해 주세요"}""")
            val conn = connection()

            conn.clothes.listClothes(ClothFilter())

            assertNull(store.load())
            assertNull(conn.auth.session.value)
        }

    @Test
    fun deleteAccountClearsSessionOnSuccess() =
        runTest {
            store.save(Session("s-1"))
            respond(204)
            val conn = connection()

            assertEquals(ApiResult.Ok(Unit), conn.auth.deleteAccount())

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/v1/me", request.url.encodedPath)
            assertNull(conn.auth.session.value)
        }

    @Test
    fun failedDeleteAccountKeepsSession() =
        runTest {
            store.save(Session("s-1"))
            respond(500)
            val conn = connection()

            assertTrue(conn.auth.deleteAccount() is ApiResult.Error)

            assertEquals(
                "s-1",
                conn.auth.session.value
                    ?.token,
            )
        }

    @Test
    fun logoutClearsSessionEvenWhenServerIsDown() =
        runTest {
            store.save(Session("s-1"))
            val conn = connection()
            server.close()

            conn.auth.logout()

            assertNull(conn.auth.session.value)
            assertNull(store.load())
        }
}
