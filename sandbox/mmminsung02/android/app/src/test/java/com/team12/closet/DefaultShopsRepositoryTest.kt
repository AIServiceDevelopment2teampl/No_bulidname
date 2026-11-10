package com.team12.closet

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.team12.closet.data.AddShopResult
import com.team12.closet.data.AppDatabase
import com.team12.closet.data.DefaultShopsRepository
import com.team12.closet.data.InMemorySessionStore
import com.team12.closet.data.ServerConnection
import com.team12.closet.data.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 실제 Room(파일 DB)과 앱 assets의 기본 목록으로 확인한다 */
@RunWith(RobolectricTestRunner::class)
class DefaultShopsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = openDb()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
        server.close()
    }

    private fun openDb() = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME).allowMainThreadQueries().build()

    private fun repository(database: AppDatabase = db) =
        DefaultShopsRepository(
            api = ServerConnection(server.url("/").toString(), InMemorySessionStore(Session("t"))).shopsApi,
            dao = database.myShops(),
            builtIn = DefaultShopsRepository.builtInFrom(context),
        )

    @Test
    fun builtInListComesFromTheSameShopsJsonAsTheServer() {
        val shops = DefaultShopsRepository.builtInFrom(context)()

        assertTrue(shops.size >= 10)
        assertTrue(shops.any { it.id == "zozotown" && it.shipping == "forwarding" })
        assertTrue(shops.all { it.shipping in setOf("direct", "forwarding", "unknown") })
    }

    @Test
    fun usesServerListWhenAvailable() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(
                        """{"shops":[{"id":"only","name":"Only","url":"https://only.example/","region":"overseas",""" +
                            """"lang":"en","korean_page":false,"shipping":"unknown"}]}""",
                    ).build(),
            )

            val shops = repository().defaultShops()

            assertEquals(listOf("only"), shops.map { it.id })
            assertEquals("/v1/shops", server.takeRequest().url.encodedPath)
        }

    @Test
    fun fallsBackToBuiltInListWhenServerIsDown() =
        runTest {
            server.close()

            val shops = repository().defaultShops()

            assertEquals(DefaultShopsRepository.builtInFrom(context)(), shops)
        }

    @Test
    fun addedShopSurvivesRestartAndCanBeDeleted() =
        runTest {
            assertEquals(AddShopResult.Added, repository().addMyShop("COS", "https://www.cos.com/", "unknown"))

            // 앱을 다시 켠 것처럼 DB를 닫고 다시 연다
            db.close()
            db = openDb()
            val reopened = repository()
            assertEquals(listOf("COS"), reopened.myShops.first().map { it.name })

            reopened.removeMyShop("https://www.cos.com/")
            assertEquals(emptyList<Any>(), reopened.myShops.first())
        }

    @Test
    fun rejectsNonHttpsDuplicatesAndEmptyName() =
        runTest {
            val repo = repository()
            repo.addMyShop("COS", "https://www.cos.com/", "unknown")

            assertEquals(
                AddShopResult.Rejected("https:// 로 시작하는 주소만 추가할 수 있어요"),
                repo.addMyShop("X", "http://x.example/", "unknown"),
            )
            // 기본 목록에 있는 곳 (www, 끝의 / 차이는 같은 곳으로 본다)
            assertEquals(AddShopResult.Rejected("이미 목록에 있는 쇼핑몰이에요"), repo.addMyShop("A", "https://asos.com", "unknown"))
            // 내가 이미 추가한 곳
            assertEquals(AddShopResult.Rejected("이미 목록에 있는 쇼핑몰이에요"), repo.addMyShop("C", "https://cos.com", "unknown"))
            assertEquals(AddShopResult.Rejected("쇼핑몰 이름을 적어 주세요"), repo.addMyShop(" ", "https://new.example/", "unknown"))
            assertEquals(1, repo.myShops.first().size)
        }

    private companion object {
        const val DB_NAME = "test-shops.db"
    }
}
