package com.example.launcherlock

import android.app.Application
import com.example.launcherlock.scheduler.LockScheduler

class LauncherLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LockScheduler.schedule(this)
    }
}
