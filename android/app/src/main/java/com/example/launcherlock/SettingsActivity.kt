package com.example.launcherlock

import android.content.Context
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.appcompat.app.AlertDialog
import com.example.launcherlock.model.LockDayMode
import com.example.launcherlock.scheduler.LockScheduler
import java.time.DayOfWeek
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val MAX_QUESTIONS = 20
        private const val PREFS_NAME = "launcher_lock"
        private const val NORMAL_HOME_PACKAGE_KEY = "normal_home_package"
        private const val NORMAL_HOME_CLASS_KEY = "normal_home_class"
        private const val DEFAULT_WEEKDAY_CSV = "1,2,3,4,5"
        private const val DEFAULT_LOCK_HOUR = 14
        private const val DEFAULT_LOCK_MINUTE = 0
        private const val ADVANCED_SETTINGS_OPEN_KEY = "advanced_settings_open"
    }

    private data class HomeCandidate(
        val packageName: String,
        val className: String,
        val label: String
    )

    private fun isSelectableNormalHome(packageName: String, className: String): Boolean {
        if (packageName == this.packageName) return false
        if (packageName == "com.android.settings") return false
        if (className.contains("FallbackHome")) return false
        return true
    }

    private val questionInputs = mutableListOf<EditText>()
    private val weekdayChecks = linkedMapOf<Int, CheckBox>()
    private lateinit var statusText: TextView
    private lateinit var toggleLockButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val mailToInput = findViewById<EditText>(R.id.mailToInput)
        val questionCountSpinner = findViewById<Spinner>(R.id.questionCountSpinner)
        val questionsContainer = findViewById<LinearLayout>(R.id.questionsContainer)
        val weekdaySelectLabel = findViewById<TextView>(R.id.weekdaySelectLabel)
        val weekdayContainer = findViewById<LinearLayout>(R.id.weekdayContainer)
        val lockModeSpinner = findViewById<Spinner>(R.id.lockModeSpinner)
        val lockTimeValue = findViewById<TextView>(R.id.lockTimeValue)
        val editLockTimeButton = findViewById<Button>(R.id.editLockTimeButton)
        val advancedSettingsContainer = findViewById<LinearLayout>(R.id.advancedSettingsContainer)
        val toggleAdvancedSettingsButton = findViewById<Button>(R.id.toggleAdvancedSettingsButton)
        val normalHomeValue = findViewById<TextView>(R.id.normalHomeValue)
        val selectNormalHomeButton = findViewById<Button>(R.id.selectNormalHomeButton)
        val resultText = findViewById<TextView>(R.id.settingsResultText)
        statusText = findViewById(R.id.statusText)
        toggleLockButton = findViewById(R.id.runLockCheckButton)

        mailToInput.setText(prefs.getString("mail_to", ""))
        var selectedLockHour = prefs.getInt("lock_hour", DEFAULT_LOCK_HOUR).coerceIn(0, 23)
        var selectedLockMinute = prefs.getInt("lock_minute", DEFAULT_LOCK_MINUTE).coerceIn(0, 59)
        lockTimeValue.text = formatLockTime(selectedLockHour, selectedLockMinute)

        editLockTimeButton.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedLockHour = hourOfDay
                    selectedLockMinute = minute
                    lockTimeValue.text = formatLockTime(selectedLockHour, selectedLockMinute)
                },
                selectedLockHour,
                selectedLockMinute,
                true
            ).show()
        }

        var isAdvancedOpen = prefs.getBoolean(ADVANCED_SETTINGS_OPEN_KEY, false)
        updateAdvancedSettingsVisibility(
            isVisible = isAdvancedOpen,
            container = advancedSettingsContainer,
            toggleButton = toggleAdvancedSettingsButton
        )
        toggleAdvancedSettingsButton.setOnClickListener {
            isAdvancedOpen = !isAdvancedOpen
            prefs.edit { putBoolean(ADVANCED_SETTINGS_OPEN_KEY, isAdvancedOpen) }
            updateAdvancedSettingsVisibility(
                isVisible = isAdvancedOpen,
                container = advancedSettingsContainer,
                toggleButton = toggleAdvancedSettingsButton
            )
        }

        val normalHomeCandidates = loadHomeCandidates()
        if (prefs.getString(NORMAL_HOME_PACKAGE_KEY, null).isNullOrBlank()) {
            findCurrentDefaultHomeCandidate()?.let { candidate ->
                if (candidate.packageName != packageName) {
                    prefs.edit {
                        putString(NORMAL_HOME_PACKAGE_KEY, candidate.packageName)
                        putString(NORMAL_HOME_CLASS_KEY, candidate.className)
                    }
                }
            }
        }
        updateNormalHomeValue(normalHomeValue, prefs, normalHomeCandidates)
        selectNormalHomeButton.setOnClickListener {
            if (normalHomeCandidates.isEmpty()) {
                resultText.text = getString(R.string.msg_normal_home_not_found)
                return@setOnClickListener
            }
            val labels = normalHomeCandidates.map { it.label }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.select_normal_home))
                .setItems(labels) { _, which ->
                    val selected = normalHomeCandidates[which]
                    prefs.edit {
                        putString(NORMAL_HOME_PACKAGE_KEY, selected.packageName)
                        putString(NORMAL_HOME_CLASS_KEY, selected.className)
                    }
                    updateNormalHomeValue(normalHomeValue, prefs, normalHomeCandidates)
                    resultText.text = getString(R.string.msg_normal_home_saved)
                }
                .show()
        }
        val lockModeOptions = listOf(
            LockDayMode.WEEKDAY,
            LockDayMode.HOLIDAY,
            LockDayMode.EVERY_DAY
        )
        val lockModeLabels = listOf(
            getString(R.string.lock_mode_weekday),
            getString(R.string.lock_mode_holiday),
            getString(R.string.lock_mode_every_day)
        )
        lockModeSpinner.adapter = createPaletteSpinnerAdapter(lockModeLabels)
        val savedLockMode = prefs.getString("lock_mode", LockDayMode.EVERY_DAY.name)
            ?.let { raw -> runCatching { LockDayMode.valueOf(raw) }.getOrNull() }
            ?: LockDayMode.EVERY_DAY
        lockModeSpinner.setSelection(lockModeOptions.indexOf(savedLockMode).coerceAtLeast(0))
        renderWeekdayChecks(
            container = weekdayContainer,
            selectedValues = parseWeekdaysCsv(
                prefs.getString("lock_weekdays", DEFAULT_WEEKDAY_CSV) ?: DEFAULT_WEEKDAY_CSV
            )
        )
        updateWeekdayVisibility(savedLockMode, weekdaySelectLabel, weekdayContainer)

        lockModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = lockModeOptions.getOrElse(position) { LockDayMode.EVERY_DAY }
                updateWeekdayVisibility(selected, weekdaySelectLabel, weekdayContainer)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val savedCount = prefs.getInt("question_count", 2).coerceIn(1, MAX_QUESTIONS)
        val questionCountOptions = (1..MAX_QUESTIONS).toList()
        val questionCountLabels = questionCountOptions.map { it.toString() }
        questionCountSpinner.adapter = createPaletteSpinnerAdapter(questionCountLabels)
        questionCountSpinner.setSelection(savedCount - 1, false)
        renderQuestionInputs(
            container = questionsContainer,
            count = savedCount,
            values = (1..savedCount).map { prefs.getString("question_$it", "").orEmpty() }
        )

        questionCountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val desiredCount = questionCountOptions.getOrElse(position) { 2 }
                if (questionInputs.size == desiredCount) return
                renderQuestionInputs(
                    container = questionsContainer,
                    count = desiredCount,
                    values = currentQuestionValues()
                )
                resultText.text = getString(R.string.msg_question_count_updated)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            val mailTo = mailToInput.text.toString().trim()
            val desiredCount = questionCountOptions
                .getOrElse(questionCountSpinner.selectedItemPosition) { savedCount }

            if (mailTo.isBlank()) {
                resultText.text = getString(R.string.msg_missing_mail_to)
                return@setOnClickListener
            }
            if (!EmailValidator.isValid(mailTo)) {
                resultText.text = getString(R.string.msg_invalid_mail_to)
                return@setOnClickListener
            }

            if (questionInputs.size != desiredCount) {
                renderQuestionInputs(
                    container = questionsContainer,
                    count = desiredCount,
                    values = currentQuestionValues()
                )
            }

            val questions = currentQuestionValues()
                .take(desiredCount)
                .map { it.trim() }
            val selectedLockMode = lockModeOptions
                .getOrElse(lockModeSpinner.selectedItemPosition) { LockDayMode.EVERY_DAY }
            val selectedWeekdays = weekdayChecks
                .filterValues { it.isChecked }
                .keys
                .sorted()
            if (questions.size != desiredCount || questions.any { it.isBlank() }) {
                resultText.text = getString(R.string.msg_settings_required)
                return@setOnClickListener
            }
            if (selectedLockMode == LockDayMode.WEEKDAY && selectedWeekdays.isEmpty()) {
                resultText.text = getString(R.string.msg_weekday_required)
                return@setOnClickListener
            }

            prefs.edit {
                putString("mail_to", mailTo)
                putString("lock_mode", selectedLockMode.name)
                putString("lock_weekdays", selectedWeekdays.joinToString(","))
                putInt("lock_hour", selectedLockHour)
                putInt("lock_minute", selectedLockMinute)
                putInt("question_count", desiredCount)
                questions.forEachIndexed { idx, q ->
                    putString("question_${idx + 1}", q)
                }
                for (idx in (desiredCount + 1)..MAX_QUESTIONS) {
                    remove("question_$idx")
                }
            }

            resultText.text = getString(R.string.msg_settings_saved)
            LockScheduler.schedule(applicationContext)
            setResult(Activity.RESULT_OK, Intent())
            finish()
        }

        toggleLockButton.setOnClickListener {
            toggleLockState()
            updateLockStateLabel()
        }

        updateLockStateLabel()
    }

    private fun updateLockStateLabel() {
        val locked = isLockedNow()
        val state = if (locked) getString(R.string.locked) else getString(R.string.unlocked)
        statusText.text = getString(R.string.current_state, state)
        toggleLockButton.text = if (locked) {
            getString(R.string.unlock_now)
        } else {
            getString(R.string.lock_now)
        }
    }

    private fun isLockedNow(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("is_locked", false)
    }

    private fun toggleLockState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getBoolean("is_locked", false)
        prefs.edit {
            putBoolean("is_locked", !current)
        }
    }

    private fun currentQuestionValues(): List<String> {
        return questionInputs.map { it.text.toString() }
    }

    private fun renderQuestionInputs(
        container: LinearLayout,
        count: Int,
        values: List<String>
    ) {
        questionInputs.clear()
        container.removeAllViews()

        repeat(count) { idx ->
            val input = EditText(this).apply {
                hint = getString(R.string.hint_question_n, idx + 1)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setText(values.getOrNull(idx).orEmpty())
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.lotus_ink))
                setHintTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.duck_egg_cyan_deep))
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@SettingsActivity, R.color.duck_egg_cyan_deep)
                )
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (idx > 0) params.topMargin = (8 * resources.displayMetrics.density).toInt()
            input.layoutParams = params
            container.addView(input)
            questionInputs += input
        }
    }

    private fun parseWeekdaysCsv(raw: String): Set<Int> {
        val parsed = raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
        return if (parsed.isEmpty()) {
            setOf(
                DayOfWeek.MONDAY.value,
                DayOfWeek.TUESDAY.value,
                DayOfWeek.WEDNESDAY.value,
                DayOfWeek.THURSDAY.value,
                DayOfWeek.FRIDAY.value
            )
        } else {
            parsed
        }
    }

    private fun formatLockTime(hour: Int, minute: Int): String {
        return String.format(Locale.JAPAN, "%02d:%02d", hour, minute)
    }

    private fun renderWeekdayChecks(
        container: LinearLayout,
        selectedValues: Set<Int>
    ) {
        weekdayChecks.clear()
        container.removeAllViews()
        val labels = linkedMapOf(
            DayOfWeek.MONDAY.value to getString(R.string.weekday_mon),
            DayOfWeek.TUESDAY.value to getString(R.string.weekday_tue),
            DayOfWeek.WEDNESDAY.value to getString(R.string.weekday_wed),
            DayOfWeek.THURSDAY.value to getString(R.string.weekday_thu),
            DayOfWeek.FRIDAY.value to getString(R.string.weekday_fri),
            DayOfWeek.SATURDAY.value to getString(R.string.weekday_sat),
            DayOfWeek.SUNDAY.value to getString(R.string.weekday_sun)
        )

        labels.forEach { (value, label) ->
            val check = CheckBox(this).apply {
                text = label
                isChecked = value in selectedValues
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.lotus_ink))
                buttonTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@SettingsActivity, R.color.duck_egg_cyan_deep)
                )
            }
            container.addView(check)
            weekdayChecks[value] = check
        }
    }

    private fun createPaletteSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.let { text ->
                    text.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.lotus_ink))
                    text.setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.moon_white))
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.let { text ->
                    text.setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.lotus_ink))
                    text.setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.moon_white))
                }
                return view
            }
        }.also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun updateWeekdayVisibility(
        mode: LockDayMode,
        label: TextView,
        container: LinearLayout
    ) {
        val visible = mode == LockDayMode.WEEKDAY
        label.visibility = if (visible) View.VISIBLE else View.GONE
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateAdvancedSettingsVisibility(
        isVisible: Boolean,
        container: LinearLayout,
        toggleButton: Button
    ) {
        container.visibility = if (isVisible) View.VISIBLE else View.GONE
        toggleButton.text = if (isVisible) {
            getString(R.string.hide_advanced_settings)
        } else {
            getString(R.string.show_advanced_settings)
        }
    }

    private fun loadHomeCandidates(): List<HomeCandidate> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val info = resolveInfo.activityInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager)?.toString()
                    ?: "${info.packageName}/${info.name}"
                val normalizedClass = if (info.name.startsWith(".")) {
                    "${info.packageName}${info.name}"
                } else {
                    info.name
                }
                if (!isSelectableNormalHome(info.packageName, normalizedClass)) return@mapNotNull null
                HomeCandidate(
                    packageName = info.packageName,
                    className = normalizedClass,
                    label = label
                )
            }
            .distinctBy { "${it.packageName}/${it.className}" }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun findCurrentDefaultHomeCandidate(): HomeCandidate? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        val info = resolved.activityInfo ?: return null
        val label = resolved.loadLabel(packageManager)?.toString()
            ?: "${info.packageName}/${info.name}"
        val normalizedClass = if (info.name.startsWith(".")) {
            "${info.packageName}${info.name}"
        } else {
            info.name
        }
        if (!isSelectableNormalHome(info.packageName, normalizedClass)) return null
        return HomeCandidate(
            packageName = info.packageName,
            className = normalizedClass,
            label = label
        )
    }

    private fun updateNormalHomeValue(
        textView: TextView,
        prefs: android.content.SharedPreferences,
        candidates: List<HomeCandidate>
    ) {
        val pkg = prefs.getString(NORMAL_HOME_PACKAGE_KEY, null)
        val cls = prefs.getString(NORMAL_HOME_CLASS_KEY, null)
        val selected = candidates.firstOrNull { it.packageName == pkg && it.className == cls }
        textView.text = selected?.label ?: getString(R.string.normal_home_not_selected)
    }
}
