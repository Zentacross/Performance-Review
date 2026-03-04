package zx.zx.sw

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var appDatabase: AppDatabase
    private lateinit var progressDao: ProgressDao
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var monthSpinner: Spinner
    private lateinit var yearSpinner: Spinner
    private var selectedMonth: Int = 0 // Calendar.MONTH starts from 0 (January)
    private var selectedYear: Int = 0

    private lateinit var totalScoreTextView: TextView
    private lateinit var percentageTextView: TextView
    private lateinit var week1Title: TextView
    private lateinit var week2Title: TextView
    private lateinit var week3Title: TextView
    private lateinit var week4Title: TextView
    private lateinit var week5Title: TextView

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.also { uri ->
                backupData(uri)
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.also { uri ->
                restoreData(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val mainLayout = findViewById<LinearLayout>(R.id.main)
        val footerLayout = findViewById<LinearLayout>(R.id.footer_layout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            mainLayout.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            footerLayout.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Room Database
        appDatabase = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "progress-db"
        ).build()
        progressDao = appDatabase.progressDao()

        monthSpinner = findViewById(R.id.monthSpinner)
        yearSpinner = findViewById(R.id.yearSpinner)
        totalScoreTextView = findViewById(R.id.totalScoreTextView)
        percentageTextView = findViewById(R.id.percentageTextView)
        week1Title = findViewById(R.id.week1Title)
        week2Title = findViewById(R.id.week2Title)
        week3Title = findViewById(R.id.week3Title)
        week4Title = findViewById(R.id.week4Title)
        week5Title = findViewById(R.id.week5Title)

        setupSpinners()

        // Set up Save button click listener
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveProgressData()
        }

        val hamburgerMenu = findViewById<ImageView>(R.id.hamburger_menu)
        hamburgerMenu.setOnClickListener { view ->
            showPopupMenu(view)
        }

        // Initial load for the current month/year
        loadProgressData()
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.main_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_backup -> {
                    openBackupLocation()
                    true
                }
                R.id.action_restore -> {
                    openRestoreLocation()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun openBackupLocation() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "progress_backup.json")
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        }
        backupLauncher.launch(intent)
    }

    private fun openRestoreLocation() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        }
        restoreLauncher.launch(intent)
    }

    private fun backupData(uri: Uri) {
        activityScope.launch {
            val allEntries = progressDao.getAllEntries()
            val json = Gson().toJson(allEntries)

            try {
                contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use {
                        it.write(json.toByteArray())
                    }
                }
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Backup successful!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Backup failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun restoreData(uri: Uri) {
        activityScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                val type = object : TypeToken<List<ProgressEntry>>() {}.type
                val entries: List<ProgressEntry> = Gson().fromJson(json, type)

                progressDao.insertAll(entries)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Restore successful!", Toast.LENGTH_SHORT).show()
                    loadProgressData()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Restore failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun setupSpinners() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear - 5..currentYear + 5).map { it.toString() }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        yearSpinner.adapter = yearAdapter

        val months = resources.getStringArray(R.array.months_array)
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        monthSpinner.adapter = monthAdapter

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        monthSpinner.setSelection(currentMonth)
        yearSpinner.setSelection(years.indexOf(currentYear.toString()))

        selectedMonth = currentMonth
        selectedYear = currentYear

        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedMonth = position
                loadProgressData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        yearSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedYear = parent.getItemAtPosition(position).toString().toInt()
                loadProgressData()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun getDaysInMonth(month: Int, year: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun updateDaysInMonthUI() {
        val daysInMonth = getDaysInMonth(selectedMonth, selectedYear)
        val tableLayoutIds = listOf(
            R.id.tableLayout_week1,
            R.id.tableLayout_week2,
            R.id.tableLayout_week3,
            R.id.tableLayout_week4,
            R.id.tableLayout_week5
        )

        var currentDay = 1
        for (tableLayoutId in tableLayoutIds) {
            val tableLayout = findViewById<TableLayout>(tableLayoutId)
            // Remove all rows except the header
            tableLayout.removeViews(1, tableLayout.childCount - 1)

            for (i in 0 until 7) { // Max 7 days per week
                if (currentDay <= daysInMonth) {
                    val row = layoutInflater.inflate(R.layout.table_row_item, tableLayout, false) as TableRow
                    val dayTextView = row.findViewById<TextView>(R.id.dayTextView)
                    val summaryEditText = row.findViewById<EditText>(R.id.summaryEditText)
                    dayTextView.text = currentDay.toString()

                    val dayForDialog = currentDay
                    summaryEditText.setOnClickListener {
                        showSummaryDialog(dayForDialog, summaryEditText)
                    }

                    tableLayout.addView(row)
                    currentDay++
                } else {
                    break
                }
            }
        }
    }

    private fun showSummaryDialog(day: Int, summaryEditText: EditText) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Summary for Day $day")

        val input = EditText(this)
        input.setText(summaryEditText.text.toString())
        input.setPadding(32, 32, 32, 32)
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            summaryEditText.setText(input.text.toString())
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun getTableLayoutForDay(day: Int): TableLayout? {
        val week = (day - 1) / 7 + 1
        return when (week) {
            1 -> findViewById(R.id.tableLayout_week1)
            2 -> findViewById(R.id.tableLayout_week2)
            3 -> findViewById(R.id.tableLayout_week3)
            4 -> findViewById(R.id.tableLayout_week4)
            5 -> findViewById(R.id.tableLayout_week5)
            else -> null
        }
    }

    private fun getEditTextsForDay(day: Int): Pair<EditText, EditText>? {
        val tableLayout = getTableLayoutForDay(day) ?: return null
        val rowInWeek = (day - 1) % 7 + 1 // +1 to skip header row when indexing
        if (rowInWeek < tableLayout.childCount) {
            val row = tableLayout.getChildAt(rowInWeek) as TableRow
            val scoreEditText = row.findViewById<EditText>(R.id.scoreEditText)
            val summaryEditText = row.findViewById<EditText>(R.id.summaryEditText)
            return Pair(scoreEditText, summaryEditText)
        }
        return null
    }

    private fun saveProgressData() {
        activityScope.launch {
            val daysInMonth = getDaysInMonth(selectedMonth, selectedYear)
            for (day in 1..daysInMonth) {
                val (scoreEditText, summaryEditText) = getEditTextsForDay(day) ?: continue

                val score = scoreEditText.text.toString().toIntOrNull()
                val summary = summaryEditText.text.toString().takeIf { it.isNotBlank() }

                val existingEntry = progressDao.getEntryByDayMonthYear(day, selectedMonth, selectedYear)
                if (existingEntry == null) {
                    // Insert new entry
                    progressDao.insert(ProgressEntry(day = day, month = selectedMonth, year = selectedYear, score = score, summary = summary))
                } else {
                    // Update existing entry
                    progressDao.update(existingEntry.copy(score = score, summary = summary))
                }
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Data saved!", Toast.LENGTH_SHORT).show()
                loadProgressData() // Reload data to update scores
            }
        }
    }

    private fun loadProgressData() {
        activityScope.launch {
            runOnUiThread {
                updateDaysInMonthUI() // Update UI first to reflect correct number of days
                // Clear existing data in EditTexts before loading new data
                val daysInMonth = getDaysInMonth(selectedMonth, selectedYear)
                for (day in 1..daysInMonth) {
                    getEditTextsForDay(day)?.let { (scoreEditText, summaryEditText) ->
                        scoreEditText.setText("")
                        summaryEditText.setText("")
                    }
                }
            }

            val allEntries = progressDao.getEntriesForMonthYear(selectedMonth, selectedYear).associateBy { it.day }
            var totalScore = 0
            val weeklyScores = IntArray(5)

            runOnUiThread {
                val daysInMonth = getDaysInMonth(selectedMonth, selectedYear)
                for (day in 1..daysInMonth) {
                    val score = allEntries[day]?.score ?: 0
                    totalScore += score
                    val week = (day - 1) / 7
                    if(week < 5) {
                        weeklyScores[week] += score
                    }

                    allEntries[day]?.let { entry ->
                        getEditTextsForDay(day)?.let { (scoreEditText, summaryEditText) ->
                            scoreEditText.setText(entry.score?.toString() ?: "")
                            summaryEditText.setText(entry.summary ?: "")
                        }
                    }
                }

                val maxScore = daysInMonth * 20
                val percentage = if (maxScore > 0) (totalScore.toDouble() / maxScore * 100) else 0.0

                totalScoreTextView.text = "Total Score: $totalScore"
                percentageTextView.text = "Percentage: ${String.format("%.2f", percentage)}%"

                week1Title.text = "Week 1 - '${weeklyScores[0]}'"
                week2Title.text = "Week 2 - '${weeklyScores[1]}'"
                week3Title.text = "Week 3 - '${weeklyScores[2]}'"
                week4Title.text = "Week 4 - '${weeklyScores[3]}'"
                week5Title.text = "Week 5 - '${weeklyScores[4]}'"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.coroutineContext.cancelChildren()
    }
}
