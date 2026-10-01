package com.example.photoevents.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Transaction
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAllWithImages(): Flow<List<EventWithImages>>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    fun observeOneWithImages(id: String): Flow<EventWithImages?>

    @Query("SELECT * FROM events")
    suspend fun getAllIncludingDeleted(): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): Event?

    @Upsert
    suspend fun upsert(event: Event)

    @Upsert
    suspend fun upsertAll(events: List<Event>)

    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    /** Xoá nhiều sự kiện cùng lúc (chọn nhiều để xoá hàng loạt). */
    @Query("UPDATE events SET deleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteAll(ids: Collection<String>, now: Long = System.currentTimeMillis())

    @Query("UPDATE events SET coverImageId = :imageId, updatedAt = :now WHERE id = :id")
    suspend fun setCover(id: String, imageId: String?, now: Long = System.currentTimeMillis())

    /** Đổi ngày diễn ra sự kiện — [date] nên đã được đưa về 00:00 (xem [normalizeToMidnight]). */
    @Query("UPDATE events SET eventDate = :date, updatedAt = :now WHERE id = :id")
    suspend fun setEventDate(id: String, date: Long, now: Long = System.currentTimeMillis())
}
