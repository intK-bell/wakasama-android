package com.example.launcherlock

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.launcherlock.model.LockDayMode

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val MAX_QUESTIONS = 20
    }

    private val questionInputs = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)

        val mailToInput = findViewById<EditText>(R.id.mailToInput)
        val questionCountInput = findViewById<EditText>(R.id.questionCountInput)
        val applyQuestionCountButton = findViewById<Button>(R.id.applyQuestionCountButton)
        val questionsContainer = findViewById<LinearLayout>(R.id.questionsContainer)
        val lockModeSpinner = findViewById<Spinner>(R.id.lockModeSpinner)
        val resultText = findViewById<TextView>(R.id.settingsResultText)

        mailToInput.setText(prefs.getString("mail_to", ""))
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
        lockModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            lockModeLabels
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val savedLockMode = prefs.getString("lock_mode", LockDayMode.EVERY_DAY.name)
            ?.let { raw -> runCatching { LockDayMode.valueOf(raw) }.getOrNull() }
            ?: LockDayMode.EVERY_DAY
        lockModeSpinner.setSelection(lockModeOptions.indexOf(savedLockMode).coerceAtLeast(0))

        val savedCount = prefs.getInt("question_count", 2).coerceIn(1, MAX_QUESTIONS)
        questionCountInput.setText(savedCount.toString())
        renderQuestionInputs(
            container = questionsContainer,
            count = savedCount,
            values = (1..savedCount).map { prefs.getString("question_$it", "").orEmpty() }
        )

        applyQuestionCountButton.setOnClickListener {
            val desiredCount = parseQuestionCount(questionCountInput.text.toString())
            if (desiredCount == null) {
                resultText.text = getString(R.string.msg_invalid_question_count)
                return@setOnClickListener
            }
            renderQuestionInputs(
                container = questionsContainer,
                count = desiredCount,
                values = currentQuestionValues()
            )
            resultText.text = getString(R.string.msg_question_count_updated)
        }

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            val mailTo = mailToInput.text.toString().trim()
            val desiredCount = parseQuestionCount(questionCountInput.text.toString())
            if (desiredCount == null) {
                resultText.text = getString(R.string.msg_invalid_question_count)
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

            if (
                mailTo.isBlank() ||
                questions.size != desiredCount ||
                questions.any { it.isBlank() }
            ) {
                resultText.text = getString(R.string.msg_settings_required)
                return@setOnClickListener
            }

            val editor = prefs.edit()
                .putString("mail_to", mailTo)
                .putString(
                    "lock_mode",
                    lockModeOptions
                        .getOrElse(lockModeSpinner.selectedItemPosition) { LockDayMode.EVERY_DAY }
                        .name
                )
                .putInt("question_count", desiredCount)

            questions.forEachIndexed { idx, q ->
                editor.putString("question_${idx + 1}", q)
            }
            for (idx in (desiredCount + 1)..MAX_QUESTIONS) {
                editor.remove("question_$idx")
            }
            editor.apply()

            resultText.text = getString(R.string.msg_settings_saved)
            setResult(Activity.RESULT_OK, Intent())
            finish()
        }
    }

    private fun parseQuestionCount(raw: String): Int? {
        val count = raw.trim().toIntOrNull() ?: return null
        return if (count in 1..MAX_QUESTIONS) count else null
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
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (idx > 0) params.topMargin = dpToPx(8)
            input.layoutParams = params
            container.addView(input)
            questionInputs += input
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
