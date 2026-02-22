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
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.example.launcherlock.lock.LockAlertNotifier
import com.example.launcherlock.lock.LockStateEvaluator
import com.example.launcherlock.model.QuestionAnswer
import com.example.launcherlock.network.NetworkModule
import com.example.launcherlock.queue.AppDatabase
import com.example.launcherlock.repo.AnswerUnlockUseCase
import com.example.launcherlock.repo.SubmissionRepository
import com.example.launcherlock.security.DeviceSigningManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val ENABLE_PINNED_MODE = false
        private const val PREFS_NAME = LockStateEvaluator.PREFS_NAME
        private const val HOME_SETTINGS_IN_PROGRESS_KEY = "home_settings_in_progress"
        private const val HOME_SETTINGS_REQUESTED_AT_KEY = "home_settings_requested_at"
        private const val HOME_SETTINGS_EXPECT_DEFAULT_HOME_KEY = "home_settings_expect_default_home"
        private const val HOME_SETTINGS_RETURN_TIMEOUT_MS = 15 * 60 * 1000L
        private const val SKIP_FORWARD_TO_NORMAL_HOME_ONCE_KEY = "skip_forward_to_normal_home_once"
        private const val RETURN_TO_MAIN_AFTER_SAVE_ONCE_KEY = "return_to_main_after_save_once"
        private const val IS_LOCKED_KEY = LockStateEvaluator.IS_LOCKED_KEY
        private const val PENDING_UNLOCK_DECISION_ONCE_KEY = "pending_unlock_decision_once"
        private const val NORMAL_HOME_PACKAGE_KEY = "normal_home_package"
        private const val NORMAL_HOME_CLASS_KEY = "normal_home_class"
        private const val FORWARDING_TO_NORMAL_HOME_EXTRA = "forwarding_to_normal_home"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
    }

    private data class QuestionRow(
        val question: String,
        val answerInput: EditText
    )

    private enum class LaunchSource {
        HOME,
        LAUNCHER,
        OTHER
    }

    private lateinit var questionAnswerContainer: LinearLayout
    private lateinit var submitButton: View
    private lateinit var submitButtonLabel: TextView
    private var isSubmitting = false
    private var suppressNextAutoForwardToNormalHome = false
    private var foldBlockedDialog: AlertDialog? = null
    private val questionRows = mutableListOf<QuestionRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        questionAnswerContainer = findViewById(R.id.questionAnswerContainer)
        submitButton = findViewById(R.id.submitButton)
        submitButtonLabel = findViewById(R.id.submitButtonLabel)

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
        maybeRequestNotificationPermission()
        monitorFoldState()

        submitButton.setOnClickListener {
            if (isSubmitting) return@setOnClickListener

            val answers = questionRows.map {
                QuestionAnswer(q = it.question, a = it.answerInput.text.toString())
            }.filter { it.q.isNotBlank() || it.a.isNotBlank() }
            if (answers.isEmpty() || answers.any { it.a.isBlank() }) {
                showErrorPopup(getString(R.string.msg_answer_required))
                return@setOnClickListener
            }

            lifecycleScope.launch {
                isSubmitting = true
                setSubmitButtonSubmitting(submitting = true)
                try {
                    val baseUrl = prefs.getString("api_base_url", "") ?: ""
                    val signingManager = DeviceSigningManager(applicationContext)

                    if (baseUrl.isBlank()) {
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

                    val submitResult = useCase.submitAnswersAndUnlock(
                        deviceId = deviceId,
                        to = mailTo,
                        answers = answers
                    )

                    when (submitResult) {
                        SubmissionRepository.SubmitResult.SUCCESS -> {
                            val message = resources.getStringArray(R.array.unlock_success_messages).random()
                            showSuccessPopup(message)
                        }
                        SubmissionRepository.SubmitResult.QUEUED_RATE_LIMITED -> {
                            showErrorPopup(getString(R.string.msg_rate_limited_queued))
                        }
                        SubmissionRepository.SubmitResult.QUEUED -> {
                            showErrorPopup(getString(R.string.msg_queued))
                        }
                    }
                } finally {
                    isSubmitting = false
                    setSubmitButtonSubmitting(submitting = false)
                }
            }
        }
        val handledByPendingUnlockDecision = applyPendingUnlockDecisionIfNeeded(intent)
        val returnToMainAfterSave = if (handledByPendingUnlockDecision) {
            false
        } else {
            consumeReturnToMainAfterSaveOnce(prefs)
        }
        suppressNextAutoForwardToNormalHome = if (handledByPendingUnlockDecision) {
            false
        } else {
            consumeSkipForwardToNormalHomeOnce(prefs) || returnToMainAfterSave
        }
        applyConfiguredQuestions()
        if (!handledByPendingUnlockDecision && !suppressNextAutoForwardToNormalHome) {
            maybeForwardToNormalHome(intent)
        }
        updateLockTaskMode()
    }

    private fun monitorFoldState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { layoutInfo ->
                        val blocked = layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .any { feature ->
                                feature.state == FoldingFeature.State.FLAT ||
                                    feature.state == FoldingFeature.State.HALF_OPENED ||
                                    feature.isSeparating
                            }
                        applyFoldRestriction(blocked)
                    }
            }
        }
    }

    private fun applyFoldRestriction(blocked: Boolean) {
        submitButton.isEnabled = !blocked && !isSubmitting
        questionAnswerContainer.isEnabled = !blocked
        findViewById<View>(R.id.openSettingsButton).isEnabled = !blocked
        questionAnswerContainer.alpha = if (blocked) 0.35f else 1f
        if (!blocked) {
            foldBlockedDialog?.dismiss()
            foldBlockedDialog = null
            return
        }
        if (foldBlockedDialog?.isShowing == true) return
        foldBlockedDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.fold_restriction_title))
            .setMessage(getString(R.string.fold_restriction_message))
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val handledByPendingUnlockDecision = applyPendingUnlockDecisionIfNeeded(intent)
        val returnToMainAfterSave = if (handledByPendingUnlockDecision) {
            false
        } else {
            consumeReturnToMainAfterSaveOnce(prefs)
        }
        val skipForwardBySave = if (handledByPendingUnlockDecision) {
            false
        } else {
            consumeSkipForwardToNormalHomeOnce(prefs)
        }
        val launchSource = detectLaunchSource(intent)

        if (!isAppDefaultHome()) {
            Log.i(TAG, "onResume: not default home, showing setup guide")
            maybeShowDefaultHomeSetupGuide()
            return
        }

        val shouldOpenSettingsAfterHomeSetup = consumeHomeSettingsReturnOnce(prefs)
        val shouldAutoOpenSettings =
            shouldOpenSettingsAfterHomeSetup && launchSource != LaunchSource.LAUNCHER
        Log.i(
            TAG,
            "onResume: default home, launchSource=$launchSource, shouldOpenSettingsAfterHomeSetup=$shouldOpenSettingsAfterHomeSetup, shouldAutoOpenSettings=$shouldAutoOpenSettings, returnToMainAfterSave=$returnToMainAfterSave, skipForwardBySave=$skipForwardBySave"
        )
        suppressNextAutoForwardToNormalHome =
            shouldAutoOpenSettings || returnToMainAfterSave || skipForwardBySave
        if (shouldAutoOpenSettings) {
            Log.i(TAG, "onResume: opening SettingsActivity after home setup return")
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        applyConfiguredQuestions()
        if (!handledByPendingUnlockDecision && !suppressNextAutoForwardToNormalHome) {
            maybeForwardToNormalHome(intent)
        }
        updateLockTaskMode()
    }

    override fun onDestroy() {
        foldBlockedDialog?.dismiss()
        foldBlockedDialog = null
        super.onDestroy()
    }

    private fun consumeSkipForwardToNormalHomeOnce(
        prefs: android.content.SharedPreferences
    ): Boolean {
        val shouldSkip = prefs.getBoolean(SKIP_FORWARD_TO_NORMAL_HOME_ONCE_KEY, false)
        if (shouldSkip) {
            prefs.edit {
                putBoolean(SKIP_FORWARD_TO_NORMAL_HOME_ONCE_KEY, false)
            }
        }
        return shouldSkip
    }

    private fun consumeReturnToMainAfterSaveOnce(
        prefs: android.content.SharedPreferences
    ): Boolean {
        val shouldReturnToMain = prefs.getBoolean(RETURN_TO_MAIN_AFTER_SAVE_ONCE_KEY, false)
        if (shouldReturnToMain) {
            prefs.edit {
                putBoolean(RETURN_TO_MAIN_AFTER_SAVE_ONCE_KEY, false)
            }
        }
        return shouldReturnToMain
    }

    private fun consumeHomeSettingsReturnOnce(
        prefs: android.content.SharedPreferences
    ): Boolean {
        val inProgress = prefs.getBoolean(HOME_SETTINGS_IN_PROGRESS_KEY, false)
        if (!inProgress) return false

        val requestedAt = prefs.getLong(HOME_SETTINGS_REQUESTED_AT_KEY, 0L)
        val expectDefaultHome = prefs.getBoolean(HOME_SETTINGS_EXPECT_DEFAULT_HOME_KEY, false)
        val isFreshRequest = requestedAt > 0L &&
            (System.currentTimeMillis() - requestedAt) <= HOME_SETTINGS_RETURN_TIMEOUT_MS
        val shouldOpenSettings = isFreshRequest && expectDefaultHome && isAppDefaultHome()
        Log.i(
            TAG,
            "consumeHomeSettingsReturnOnce: inProgress=true, requestedAt=$requestedAt, isFreshRequest=$isFreshRequest, expectDefaultHome=$expectDefaultHome, shouldOpenSettings=$shouldOpenSettings"
        )

        prefs.edit {
            putBoolean(HOME_SETTINGS_IN_PROGRESS_KEY, false)
            remove(HOME_SETTINGS_REQUESTED_AT_KEY)
            remove(HOME_SETTINGS_EXPECT_DEFAULT_HOME_KEY)
        }
        return shouldOpenSettings
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyPendingUnlockDecisionIfNeeded(intent)) {
            updateLockTaskMode()
            return
        }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (consumeReturnToMainAfterSaveOnce(prefs)) {
            suppressNextAutoForwardToNormalHome = true
        }
        val shouldSuppressForward = consumeAutoForwardSuppressionIfNeeded()
        if (!shouldSuppressForward) {
            maybeForwardToNormalHome(intent)
        }
        updateLockTaskMode()
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

    private fun maybeForwardToNormalHome(launchIntent: Intent?) {
        if (isLockedNow()) return
        if (!isAppDefaultHome()) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(HOME_SETTINGS_IN_PROGRESS_KEY, false)) return
        val launchSource = detectLaunchSource(launchIntent)
        if (launchSource == LaunchSource.LAUNCHER) return
        if (launchIntent?.getBooleanExtra(FORWARDING_TO_NORMAL_HOME_EXTRA, false) == true) return

        if (launchFirstAvailableNormalHomeTarget()) return
        Log.w(TAG, "No available normal home target could be launched.")
    }

    private fun launchFirstAvailableNormalHomeTarget(): Boolean {
        val saved = loadSavedNormalHomeComponent()
        val discovered = discoverNormalHomeComponent()
        val targets = listOfNotNull(saved, discovered)
            .distinctBy { "${it.packageName}/${it.className}" }
            .filter { it.packageName != packageName }

        for (target in targets) {
            if (!isAvailableHomeComponent(target)) continue
            if (launchNormalHomeComponent(target)) return true
        }
        return false
    }

    private fun applyPendingUnlockDecisionIfNeeded(launchIntent: Intent?): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (launchIntent?.getBooleanExtra(LockAlertNotifier.EXTRA_FROM_TIMER_LOCK_NOTIFICATION, false) == true) {
            clearPendingUnlockDecisionIfNeeded(prefs, "notification_tap")
            Log.i(TAG, "applyPendingUnlockDecisionIfNeeded: skipped by notification tap")
            return false
        }
        val pending = prefs.getBoolean(PENDING_UNLOCK_DECISION_ONCE_KEY, false)
        val isLocked = isLockedNow()
        val isDefaultHome = isAppDefaultHome()
        val homeSettingsInProgress = prefs.getBoolean(HOME_SETTINGS_IN_PROGRESS_KEY, false)
        Log.i(
            TAG,
            "applyPendingUnlockDecisionIfNeeded: pending=$pending, isLocked=$isLocked, isDefaultHome=$isDefaultHome, homeSettingsInProgress=$homeSettingsInProgress"
        )
        if (!pending) return false
        if (!isDefaultHome) return false
        if (homeSettingsInProgress) return false

        clearPendingUnlockDecisionIfNeeded(prefs, "ignored_notification_path")
        return if (isLocked) {
            Log.i(TAG, "applyPendingUnlockDecisionIfNeeded: keep wakasama home (locked)")
            true
        } else {
            val forwarded = launchFirstAvailableNormalHomeTarget()
            if (!forwarded) {
                Log.w(TAG, "applyPendingUnlockDecisionIfNeeded: no normal home target available")
            } else {
                Log.i(TAG, "applyPendingUnlockDecisionIfNeeded: forwarded to normal home (unlocked)")
            }
            forwarded
        }
    }

    private fun clearPendingUnlockDecisionIfNeeded(
        prefs: android.content.SharedPreferences,
        reason: String
    ) {
        if (!prefs.getBoolean(PENDING_UNLOCK_DECISION_ONCE_KEY, false)) return
        prefs.edit { putBoolean(PENDING_UNLOCK_DECISION_ONCE_KEY, false) }
        Log.i(TAG, "clearPendingUnlockDecisionIfNeeded: reason=$reason")
    }

    private fun isAvailableHomeComponent(component: ComponentName): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                packageManager.getActivityInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(0)
                )
            }.isSuccess
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                packageManager.getActivityInfo(component, 0)
            }.isSuccess
        }
    }

    private fun launchNormalHomeComponent(component: ComponentName): Boolean {
        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            this.component = component
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            putExtra(FORWARDING_TO_NORMAL_HOME_EXTRA, true)
        }
        return runCatching {
            startActivity(launch)
            finish()
            true
        }.getOrElse { error ->
            Log.w(TAG, "Failed to launch normal home: ${component.flattenToShortString()}", error)
            false
        }
    }

    private fun detectLaunchSource(candidateIntent: Intent?): LaunchSource {
        val action = candidateIntent?.action ?: return LaunchSource.OTHER
        val categories = candidateIntent.categories ?: emptySet()
        if (action != Intent.ACTION_MAIN) return LaunchSource.OTHER
        return when {
            categories.contains(Intent.CATEGORY_HOME) -> LaunchSource.HOME
            categories.contains(Intent.CATEGORY_LAUNCHER) -> LaunchSource.LAUNCHER
            else -> LaunchSource.OTHER
        }
    }

    private fun consumeAutoForwardSuppressionIfNeeded(): Boolean {
        if (!suppressNextAutoForwardToNormalHome) return false
        suppressNextAutoForwardToNormalHome = false
        return true
    }

    private fun loadSavedNormalHomeComponent(): ComponentName? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkg = prefs.getString(NORMAL_HOME_PACKAGE_KEY, null)?.trim().orEmpty()
        val rawCls = prefs.getString(NORMAL_HOME_CLASS_KEY, null)?.trim().orEmpty()
        if (pkg.isBlank() || rawCls.isBlank()) return null
        val cls = if (rawCls.startsWith(".")) "$pkg$rawCls" else rawCls
        if (!isSelectableNormalHomeComponent(pkg, cls)) return null
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
                if (!isSelectableNormalHomeComponent(pkg, cls)) return@mapNotNull null
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
        button.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setSubmitButtonSubmitting(submitting: Boolean) {
        submitButton.isEnabled = !submitting
        submitButtonLabel.text = if (submitting) {
            getString(R.string.submit_and_unlock_submitting)
        } else {
            getString(R.string.submit_and_unlock)
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
                openHomeScreen()
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
        ensureNormalHomeComponentSavedBeforeHomeSettings()
        val now = System.currentTimeMillis()
        val expectDefaultHome = !isAppDefaultHome()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(HOME_SETTINGS_IN_PROGRESS_KEY, true)
            putLong(HOME_SETTINGS_REQUESTED_AT_KEY, now)
            putBoolean(HOME_SETTINGS_EXPECT_DEFAULT_HOME_KEY, expectDefaultHome)
        }
        Log.i(TAG, "openHomeSettings: marked in progress at=$now, expectDefaultHome=$expectDefaultHome")
        val homeSettingsIntent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(homeSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    private fun ensureNormalHomeComponentSavedBeforeHomeSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (loadSavedNormalHomeComponent() != null) return

        val candidate = resolveCurrentDefaultHomeComponent() ?: discoverNormalHomeComponent()
        if (candidate == null) {
            Log.w(TAG, "ensureNormalHomeComponentSavedBeforeHomeSettings: no candidate found")
            return
        }
        prefs.edit {
            putString(NORMAL_HOME_PACKAGE_KEY, candidate.packageName)
            putString(NORMAL_HOME_CLASS_KEY, candidate.className)
        }
        Log.i(
            TAG,
            "ensureNormalHomeComponentSavedBeforeHomeSettings: saved=${candidate.flattenToShortString()}"
        )
    }

    private fun resolveCurrentDefaultHomeComponent(): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        val info = resolved.activityInfo ?: return null
        val pkg = info.packageName
        val cls = if (info.name.startsWith(".")) "$pkg${info.name}" else info.name
        if (!isSelectableNormalHomeComponent(pkg, cls)) return null
        return ComponentName(pkg, cls)
    }

    private fun isSelectableNormalHomeComponent(pkg: String, cls: String): Boolean {
        if (pkg == packageName) return false
        if (pkg == "com.android.settings") return false
        if (pkg == "android") return false
        if (cls.contains("ResolverActivity")) return false
        if (cls.contains("FallbackHome")) return false
        return true
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
