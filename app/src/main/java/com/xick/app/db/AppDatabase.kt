package com.xick.app.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Further entities (FollowedChannel, Bookmark, Download, ChatFilter, ...)
 * are added as each feature lands, mirroring Xtra's db package layout.
 */
@Database(entities = [AppMeta::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase()
