package com.example.launcherlock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.launcherlock.lock.LockStateEvaluator
import com.example.launcherlock.model.QuestionAnswer
import com.example.launcherlock.network.NetworkModule
import com.example.launcherlock.queue.AppDatabase
import com.example.launcherlock.repo.AnswerUnlockUseCase
import com.example.launcherlock.repo.SubmissionRepository
import com.example.launcherlock.security.DeviceSigningManager
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val ENABLE_PINNED_MODE = false
        private const val PREFS_NAME = LockStateEvaluator.PREFS_NAME
        private const val IS_LOCKED_KEY = LockStateEvaluator.IS_LOCKED_KEY
        private const val NORMAL_HOME_PACKAGE_KEY = "normal_home_package"
        private const val NORMAL_HOME_CLASS_KEY = "normal_home_class"
        private const val FORWARDING_TO_NORMAL_HOME_EXTRA = "forwarding_to_normal_home"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
    }

    private data class QuestionRow(
        val question: String,
        val answerInput: EditText
    )

    private lateinit var questionAnswerContainer: LinearLayout
    private val uiHandler = Handler(Looper.getMainLooper())
    private var settingsLongPressTriggered = false
    private val questionRows = mutableListOf<QuestionRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        questionAnswerContainer = findViewById(R.id.questionAnswerContainer)

        if (!prefs.contains("api_base_url")) {
            prefs.edit {
                putString("api_base_url", BuildConfig.DEFAULT_API_BASE_URL.trim())
                putString("mail_to", "")
                putInt("question_count", 2)
                putString("question_1", getString(R.string.default_question_1))
                putString("question_2", getString(R.string.default_question_2))
                putString("lock_mode", "EVERY_DAY")
                putString("lock_weekdays", "1,2,3,4,5")
                putInt("lock_hour", 14)
                putInt("lock_minute", 0)
                putBoolean("is_locked", false)
            }
        }

        setupOpenSettingsGuard()
        maybeShowDefaultHomeSetupGuide()
        maybeRequestNotificationPermission()

        findViewById<View>(R.id.submitButton).setOnClickListener {
            val answers = questionRows.map {
                QuestionAnswer(q = it.question, a = it.answerInput.text.toString())
            }.filter { it.q.isNotBlank() || it.a.isNotBlank() }

            lifecycleScope.launch {
                val baseUrl = prefs.getString("api_base_url", "") ?: ""
                val signingManager = DeviceSigningManager(applicationContext)
                val appToken = signingManager.appToken()

                if (baseUrl.isBlank() || appToken.isBlank()) {
                    showErrorPopup(getString(R.string.msg_missing_config))
                    return@launch
                }
                val mailTo = prefs.getString("mail_to", null)?.trim().orEmpty()
                if (mailTo.isBlank()) {
                    showErrorPopup(getString(R.string.msg_missing_mail_to))
                    return@launch
                }
                if (!EmailValidator.isValid(mailTo)) {
                    showErrorPopup(getString(R.string.msg_invalid_mail_to))
                    return@launch
                }

                val api = NetworkModule.createApi(applicationContext, baseUrl)
                val dao = AppDatabase.getInstance(applicationContext).pendingSubmissionDao()
                val repo = SubmissionRepository(api, dao, signingManager)
                val useCase = AnswerUnlockUseCase(repo)
                val deviceId = signingManager.deviceId()

                val success = useCase.submitAnswersAndUnlock(
                    deviceId = deviceId,
                    to = mailTo,
                    answers = answers
                )

                if (success) {
                    val message = resources.getStringArray(R.array.unlock_success_messages).random()
                    showSuccessPopup(message)
                } else {
                    showErrorPopup(getString(R.string.msg_queued))
                }
            }
        }
        applyConfiguredQuestions()
        maybeForwardToNormalHome()
        updateLockTaskMode()
    }

    override fun onResume() {
        super.onResume()
        applyConfiguredQuestions()
        maybeForwardToNormalHome()
        updateLockTaskMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeForwardToNormalHome()
        updateLockTaskMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
    }

    private fun isLockedNow(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(IS_LOCKED_KEY, false)
    }

    private fun updateLockTaskMode() {
        if (!ENABLE_PINNED_MODE) return
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

    private fun maybeForwardToNormalHome() {
        if (isLockedNow()) return
        if (!isAppDefaultHome()) return
        if (isExplicitLauncherLaunch()) return
        if (intent.getBooleanExtra(FORWARDING_TO_NORMAL_HOME_EXTRA, false)) return

        val target = loadSavedNormalHomeComponent() ?: discoverNormalHomeComponent() ?: return
        if (target.packageName == packageName) return

        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            component = target
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            putExtra(FORWARDING_TO_NORMAL_HOME_EXTRA, true)
        }
        runCatching {
            startActivity(launch)
            finish()
        }
    }

    private fun isExplicitLauncherLaunch(): Boolean {
        val action = intent?.action ?: return false
        val categories = intent?.categories ?: emptySet()
        return action == Intent.ACTION_MAIN && categories.contains(Intent.CATEGORY_LAUNCHER)
    }

    private fun loadSavedNormalHomeComponent(): ComponentName? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkg = prefs.getString(NORMAL_HOME_PACKAGE_KEY, null)?.trim().orEmpty()
        val rawCls = prefs.getString(NORMAL_HOME_CLASS_KEY, null)?.trim().orEmpty()
        if (pkg.isBlank() || rawCls.isBlank()) return null
        val cls = if (rawCls.startsWith(".")) "$pkg$rawCls" else rawCls
        if (pkg == packageName) return null
        if (pkg == "com.android.settings") return null
        if (cls.contains("FallbackHome")) return null
        return ComponentName(pkg, cls)
    }

    private fun discoverNormalHomeComponent(): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val candidates = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val info = resolveInfo.activityInfo ?: return@mapNotNull null
                val pkg = info.packageName
                val cls = if (info.name.startsWith(".")) "$pkg${info.name}" else info.name
                if (pkg == packageName) return@mapNotNull null
                if (pkg == "com.android.settings") return@mapNotNull null
                if (cls.contains("FallbackHome")) return@mapNotNull null
                ComponentName(pkg, cls)
            }
            .distinctBy { "${it.packageName}/${it.className}" }
        val preferredOrder = listOf(
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.asus.launcher",
            "com.teslacoilsw.launcher",
            "ch.deletescape.lawnchair.plah",
            "app.lawnchair"
        )
        val preferred = preferredOrder.firstNotNullOfOrNull { pkg ->
            candidates.firstOrNull { it.packageName == pkg }
        }
        return preferred ?: candidates.firstOrNull()
    }

    @Suppress("unused")
    private fun discoverAndSaveNormalHomeComponent(): ComponentName? = loadSavedNormalHomeComponent()

    private fun applyConfiguredQuestions() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val button = findViewById<View>(R.id.openSettingsButton)
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
                    uiHandler.postDelayed(openRunnable, 2_000L)
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

    private fun showErrorPopup(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun maybeShowDefaultHomeSetupGuide() {
        if (isAppDefaultHome()) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.home_setup_dialog_title))
            .setMessage(getString(R.string.home_setup_dialog_message))
            .setPositiveButton(getString(R.string.home_setup_open_settings)) { _, _ ->
                openHomeSettings()
            }
            .setNegativeButton(getString(R.string.home_setup_ignore)) { _, _ ->
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    private fun isAppDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultPackage = resolved?.activityInfo?.packageName ?: return false
        return defaultPackage == packageName
    }

    private fun openHomeSettings() {
        val homeSettingsIntent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
        try {
            startActivity(homeSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    private fun showSuccessPopup(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.popup_success_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit {
                        putBoolean(IS_LOCKED_KEY, false)
                    }
                updateLockTaskMode()
                openHomeScreen()
            }
            .show()
    }
}
