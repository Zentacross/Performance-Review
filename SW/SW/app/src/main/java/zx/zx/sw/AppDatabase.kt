package zx.zx.sw

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProgressEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}