package com.ranum.one

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

// Setiap catatan punya waktu ubah (updated) dan penanda hapus (deleted) agar bisa disinkronkan antar perangkat.

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
    val bop: Long = 0,
    val updated: Long = 0,
    val deleted: Boolean = false
)

@Entity(tableName = "asset")
data class Asset(
    @PrimaryKey val uid: String,
    val name: String,
    val type: String,
    val weight: Double,
    val price: Long,
    val date: Long,
    val baru: Boolean,
    val gold: Boolean,
    val updated: Long = 0,
    val deleted: Boolean = false
)

@Entity(tableName = "prior")
data class Prior(
    @PrimaryKey val uid: String,
    val note: String,
    val date: String,
    val amount: Long,
    val updated: Long = 0,
    val deleted: Boolean = false
)

@Entity(tableName = "setting")
data class Setting(
    @PrimaryKey val k: String,
    val v: String,
    val updated: Long = 0
)

@Dao
interface AppDao {
    @Query("SELECT * FROM daily WHERE deleted = 0 ORDER BY date") fun dailies(): Flow<List<Daily>>
    @Query("SELECT * FROM daily") suspend fun allDaily(): List<Daily>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDaily(d: Daily)

    @Query("SELECT * FROM asset WHERE deleted = 0 ORDER BY date") fun assets(): Flow<List<Asset>>
    @Query("SELECT * FROM asset") suspend fun allAsset(): List<Asset>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAsset(a: Asset)

    @Query("SELECT * FROM prior WHERE deleted = 0 ORDER BY updated") fun priors(): Flow<List<Prior>>
    @Query("SELECT * FROM prior") suspend fun allPrior(): List<Prior>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPrior(p: Prior)

    @Query("SELECT * FROM setting") fun settings(): Flow<List<Setting>>
    @Query("SELECT * FROM setting") suspend fun allSetting(): List<Setting>
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
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "ranum2.db")
                .build().also { inst = it }
        }
    }
}
