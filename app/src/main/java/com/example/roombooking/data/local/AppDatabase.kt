package com.example.roombooking.data.local

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
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/*
 * Local cache (Room/SQLite). The server stays the source of truth for bookings;
 * this cache lets the room list, "my bookings" and notifications open instantly
 * and stay readable offline (NFR03). Creating a booking always needs the network,
 * because only the server can guarantee no double booking (BR02/BR05).
 */

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: Long,
    val code: String,
    val name: String,
    val building: String,
    val floor: Int,
    val capacity: Int,
    val typeId: Int,
    val typeName: String,
    val active: Boolean,
    val description: String?,
    val equipmentJson: String, // List<Equipment> stored as JSON
)

@Entity(tableName = "my_bookings")
data class BookingEntity(
    @PrimaryKey val id: Long,
    val ownerUserId: Long,      // keeps caches of different accounts on one phone apart
    val json: String,           // full Booking stored as JSON (BookingDto)
    val startTime: String,
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: Long,
    val userId: Long,
    val bookingId: Long?,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String,
)

@Dao
abstract class RoomDao {
    @Query("SELECT * FROM rooms WHERE active = 1 ORDER BY building, code")
    abstract fun observeActive(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id")
    abstract suspend fun find(id: Long): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<RoomEntity>)

    @Query("DELETE FROM rooms")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<RoomEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
abstract class BookingDao {
    @Query("SELECT * FROM my_bookings WHERE ownerUserId = :userId ORDER BY startTime DESC")
    abstract fun observeMine(userId: Long): Flow<List<BookingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<BookingEntity>)

    @Query("DELETE FROM my_bookings WHERE ownerUserId = :userId")
    abstract suspend fun clearFor(userId: Long)

    @Transaction
    open suspend fun replaceFor(userId: Long, items: List<BookingEntity>) {
        clearFor(userId)
        insertAll(items)
    }
}

@Dao
abstract class NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY id DESC")
    abstract fun observe(userId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    abstract fun observeUnread(userId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    abstract suspend fun markAllRead(userId: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    abstract suspend fun markRead(id: Long)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    abstract suspend fun clearFor(userId: Long)
}

@Database(
    entities = [RoomEntity::class, BookingEntity::class, NotificationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun bookingDao(): BookingDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "room_booking.db")
                .fallbackToDestructiveMigration() // it's only a cache; safe to rebuild
                .build()
    }
}
