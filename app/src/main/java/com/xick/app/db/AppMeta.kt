package com.xick.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal first entity so Room/KSP has something to validate against;
 * genuinely useful too (e.g. last-sync timestamps) once something writes
 * to it. Real feature entities (FollowedChannel, Bookmark, Download,
 * ChatFilter, ...) are added as each feature lands.
 */
@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey val key: String,
    val value: String,
)
