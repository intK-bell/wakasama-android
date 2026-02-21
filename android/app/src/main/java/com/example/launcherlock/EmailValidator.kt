package com.example.launcherlock

import android.util.Patterns

object EmailValidator {
    fun isValid(email: String): Boolean {
        val value = email.trim()
        if (value.isEmpty()) return false
        return Patterns.EMAIL_ADDRESS.matcher(value).matches()
    }
}
