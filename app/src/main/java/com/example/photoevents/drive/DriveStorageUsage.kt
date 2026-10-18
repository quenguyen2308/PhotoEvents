package com.example.photoevents.drive

/**
 * Thống kê dung lượng mà PhotoEvents sử dụng trên Google Drive (appDataFolder).
 */
data class DriveStorageUsage(
    val totalBytes: Long,
    val totalFiles: Int,
    val metadataBytes: Long,
    val imagesBytes: Long,
    val imagesCount: Int,
    val accountName: String
)
