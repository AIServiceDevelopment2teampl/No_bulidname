package com.team12.closet.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** 사용자가 직접 추가한 쇼핑몰. 이 폰(Room)에만 저장한다 (shopping/shop-browser.md) */
@Entity(tableName = "my_shops")
data class MyShop(
    @PrimaryKey val url: String,
    val name: String,
    /** direct / forwarding / unknown. 기본값 unknown(그 외) */
    val shipping: String = "unknown",
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface MyShopDao {
    @Query("SELECT * FROM my_shops ORDER BY addedAt")
    fun observeAll(): Flow<List<MyShop>>

    @Query("SELECT * FROM my_shops")
    suspend fun all(): List<MyShop>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shop: MyShop)

    @Query("DELETE FROM my_shops WHERE url = :url")
    suspend fun delete(url: String)
}

@Database(entities = [MyShop::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun myShops(): MyShopDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "app.db").build()
    }
}
