package com.ranum.one

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily")
data class Daily(
    @PrimaryKey val date: Long,
    val o1: Int = 0,
    val o2: Int = 0,
    val fuel: Int = 0,
    val income: Long = 0,
    val incomeOther: Long = 0,
    val noteIn: String = "",
    val mandatory: Long = 0,
    val otherExp: Long = 0,
    val noteExp: String = "",
    val bop: Long = 0
)

@Entity(tableName = "asset")
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val weight: Double,
    val price: Long,
    val date: Long,
    val baru: Boolean,
    val gold: Boolean
)

@Entity(tableName = "prior")
data class Prior(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val note: String,
    val date: String,
    val amount: Long
)

@Entity(tableName = "setting")
data class Setting(@PrimaryKey val k: String, val v: String)

@Dao
interface AppDao {
    @Query("SELECT * FROM daily ORDER BY date") fun dailies(): Flow<List<Daily>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDaily(d: Daily)
    @Query("DELETE FROM daily WHERE date = :d") suspend fun delDaily(d: Long)

    @Query("SELECT * FROM asset ORDER BY date") fun assets(): Flow<List<Asset>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAsset(a: Asset)
    @Delete suspend fun delAsset(a: Asset)

    @Query("SELECT * FROM prior ORDER BY id") fun priors(): Flow<List<Prior>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPrior(p: Prior)
    @Delete suspend fun delPrior(p: Prior)

    @Query("SELECT * FROM setting") fun settings(): Flow<List<Setting>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSetting(s: Setting)
}

@Database(
    entities = [Daily::class, Asset::class, Prior::class, Setting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "ranum.db")
                .build().also { inst = it }
        }
    }
}
