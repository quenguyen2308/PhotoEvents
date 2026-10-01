package com.example.photoevents.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventImageDao {

    @Query("SELECT * FROM event_images WHERE eventId = :eventId AND deleted = 0 ORDER BY position ASC")
    fun observeForEvent(eventId: String): Flow<List<EventImage>>

    @Query("SELECT * FROM event_images")
    suspend fun getAllIncludingDeleted(): List<EventImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: EventImage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(images: List<EventImage>)

    @Query("SELECT * FROM event_images WHERE id = :id")
    suspend fun getById(id: String): EventImage?

    /** Xoá 1 ảnh cụ thể — chỉ đánh dấu deleted, việc xoá file thật trên Drive xảy ra khi sync. */
    @Query("UPDATE event_images SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM event_images WHERE eventId = :eventId AND deleted = 0")
    suspend fun countForEvent(eventId: String): Int

    /** Xoá toàn bộ ảnh thuộc các sự kiện bị xoá hàng loạt — để sync dọn file thật trên Drive. */
    @Query("UPDATE event_images SET deleted = 1, updatedAt = :now WHERE eventId IN (:eventIds) AND deleted = 0")
    suspend fun softDeleteForEvents(eventIds: Collection<String>, now: Long = System.currentTimeMillis())
}
