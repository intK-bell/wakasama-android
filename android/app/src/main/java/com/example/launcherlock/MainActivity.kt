package com.example.launcherlock

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.launcherlock.model.QuestionAnswer
import com.example.launcherlock.network.NetworkModule
import com.example.launcherlock.queue.AppDatabase
import com.example.launcherlock.repo.AnswerUnlockUseCase
import com.example.launcherlock.repo.SubmissionRepository
import com.example.launcherlock.scheduler.LockScheduler
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private data class QuestionRow(
        val question: String,
        val answerInput: EditText
    )

    private lateinit var statusText: TextView
    private lateinit var questionAnswerContainer: LinearLayout
    private val uiHandler = Handler(Looper.getMainLooper())
    private var settingsLongPressTriggered = false
    private val questionRows = mutableListOf<QuestionRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
        statusText = findViewById(R.id.statusText)
        questionAnswerContainer = findViewById(R.id.questionAnswerContainer)

        if (!prefs.contains("api_base_url")) {
            prefs.edit {
                putString("api_base_url", "https://guh3h3lma1.execute-api.ap-northeast-1.amazonaws.com/prod")
                putString("mail_to", "")
                putInt("question_count", 2)
                putString("question_1", getString(R.string.default_question_1))
                putString("question_2", getString(R.string.default_question_2))
                putString("lock_mode", "EVERY_DAY")
                putBoolean("is_locked", false)
            }
        }

        findViewById<Button>(R.id.runLockCheckButton).setOnClickListener {
            LockScheduler.runImmediateLockCheck(this)
            updateLockStateLabel()
            updateLockTaskMode()
        }

        setupOpenSettingsGuard()

        findViewById<Button>(R.id.submitButton).setOnClickListener {
            val answers = questionRows.map {
                QuestionAnswer(q = it.question, a = it.answerInput.text.toString())
            }.filter { it.q.isNotBlank() || it.a.isNotBlank() }

            lifecycleScope.launch {
                val baseUrl = prefs.getString("api_base_url", "") ?: ""
                val appToken = BuildConfig.APP_TOKEN.trim()

                if (baseUrl.isBlank() || appToken.isBlank()) {
                    statusText.text = getString(R.string.msg_missing_config)
                    showErrorPopup()
                    return@launch
                }
                val mailTo = prefs.getString("mail_to", null)?.trim().orEmpty()
                if (mailTo.isBlank()) {
                    statusText.text = getString(R.string.msg_missing_mail_to)
                    showErrorPopup()
                    return@launch
                }

                val api = NetworkModule.createApi(baseUrl) { appToken }
                val dao = AppDatabase.getInstance(applicationContext).pendingSubmissionDao()
                val repo = SubmissionRepository(api, dao)
                val useCase = AnswerUnlockUseCase(repo)

                val success = useCase.submitAnswersAndUnlock(
                    deviceId = "child-phone-01",
                    to = mailTo,
                    answers = answers
                )

                statusText.text = if (success) {
                    getString(R.string.msg_sent_and_unlocked)
                } else {
                    getString(R.string.msg_queued)
                }
                if (success) {
                    val message = resources.getStringArray(R.array.unlock_success_messages).random()
                    showSuccessPopup(message)
                } else {
                    showErrorPopup()
                }
            }
        }
        applyConfiguredQuestions()
        updateLockStateLabel()
    }

    override fun onResume() {
        super.onResume()
        applyConfiguredQuestions()
        updateLockTaskMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
    }

    private fun updateLockStateLabel() {
        val locked = isLockedNow()
        val state = if (locked) getString(R.string.locked) else getString(R.string.unlocked)
        statusText.text = getString(R.string.current_state, state)
    }

    private fun isLockedNow(): Boolean {
        return getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
            .getBoolean("is_locked", false)
    }

    private fun updateLockTaskMode() {
        if (isLockedNow()) {
            try {
                startLockTask()
            } catch (e: Exception) {
                Log.w(TAG, "startLockTask failed: ${e.message}")
            }
        } else {
            try {
                stopLockTask()
            } catch (_: Exception) {
                // Not in lock task mode; nothing to stop.
            }
        }
    }

    private fun applyConfiguredQuestions() {
        val prefs = getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
        val count = prefs.getInt("question_count", 2).coerceIn(1, 20)
        val questions = (1..count).map { index ->
            prefs.getString("question_$index", "")?.trim().orEmpty()
        }.filter { it.isNotBlank() }

        questionAnswerContainer.removeAllViews()
        questionRows.clear()

        questions.forEachIndexed { idx, question ->
            val questionText = TextView(this).apply {
                text = getString(R.string.question_label_format, idx + 1, question)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.lotus_ink))
            }
            val answerInput = EditText(this).apply {
                hint = getString(R.string.answer_hint_format, idx + 1)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.lotus_ink))
                setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.duck_egg_cyan_deep))
                background = null
                setBackgroundColor(Color.TRANSPARENT)
            }

            val qParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (idx > 0) qParams.topMargin = (8 * resources.displayMetrics.density).toInt()
            questionText.layoutParams = qParams

            val aParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            answerInput.layoutParams = aParams

            questionAnswerContainer.addView(questionText)
            questionAnswerContainer.addView(answerInput)
            questionRows += QuestionRow(question = question, answerInput = answerInput)
        }
    }

    private fun setupOpenSettingsGuard() {
        val button = findViewById<Button>(R.id.openSettingsButton)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val openRunnable = Runnable {
            settingsLongPressTriggered = true
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        var downX = 0f
        var downY = 0f

        button.setOnClickListener { }
        button.setOnTouchListener { view: View, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    settingsLongPressTriggered = false
                    downX = event.x
                    downY = event.y
                    uiHandler.postDelayed(openRunnable, 5_000L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moved = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                    if (moved) {
                        uiHandler.removeCallbacks(openRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    uiHandler.removeCallbacks(openRunnable)
                    if (!settingsLongPressTriggered) {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    uiHandler.removeCallbacks(openRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun openHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(intent)
        moveTaskToBack(true)
        finish()
    }

    private fun showErrorPopup() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.popup_error_message))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showSuccessPopup(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.popup_success_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
                    .edit { putBoolean("is_locked", false) }
                updateLockStateLabel()
                updateLockTaskMode()
                openHomeScreen()
            }
            .show()
    }
}
