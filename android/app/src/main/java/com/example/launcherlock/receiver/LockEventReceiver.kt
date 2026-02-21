package com.example.launcherlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.launcherlock.scheduler.LockScheduler

class LockEventReceiver : BroadcastReceiver() {
    companion object {
        private const val ACTION_TIME_SET = "android.intent.action.TIME_SET"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        when (action) {
            LockScheduler.ACTION_TIMER_LOCK -> {
                LockScheduler.onTimerAlarmFired(appContext)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_TIME_SET,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                LockScheduler.schedule(appContext)
            }
        }
    }
}
