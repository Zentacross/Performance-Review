package zx.zx.sw

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress_entries", primaryKeys = ["day", "month", "year"])
data class ProgressEntry(
    val day: Int,
    val month: Int,
    val year: Int,
    val score: Int?,
    val summary: String?
)