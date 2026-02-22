package com.example.launcherlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.launcherlock.scheduler.LockScheduler

class TimerLockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LockScheduler.onTimerAlarmFired(context.applicationContext)
    }
}
