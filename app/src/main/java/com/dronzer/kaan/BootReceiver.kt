package com.dronzer.kaan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Resumes tracking after the phone restarts, if the user had it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = Store(context)
        if (store.onboarded && store.tracking) TrackerService.start(context)
    }
}
