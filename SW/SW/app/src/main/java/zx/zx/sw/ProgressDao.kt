package zx.zx.sw

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProgressDao {
    @Insert
    suspend fun insert(entry: ProgressEntry)

    @Update
    suspend fun update(entry: ProgressEntry)

    @Delete
    suspend fun delete(entry: ProgressEntry)

    @Query("SELECT * FROM progress_entries WHERE day = :dayNumber AND month = :monthNumber AND year = :yearNumber LIMIT 1")
    suspend fun getEntryByDayMonthYear(dayNumber: Int, monthNumber: Int, yearNumber: Int): ProgressEntry?

    @Query("SELECT * FROM progress_entries WHERE month = :monthNumber AND year = :yearNumber ORDER BY day ASC")
    suspend fun getEntriesForMonthYear(monthNumber: Int, yearNumber: Int): List<ProgressEntry>

    @Query("SELECT * FROM progress_entries")
    suspend fun getAllEntries(): List<ProgressEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ProgressEntry>)
}