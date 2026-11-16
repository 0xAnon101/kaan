package com.dronzer.kaan

import android.content.Context

/**
 * Release builds update through Google Play. This stand-in keeps the same shape as the
 * debug updater so the shared code compiles, and it does nothing.
 */
object Updater {
    const val ENABLED = false

    val state: UpdateState get() = UpdateState.Idle

    fun check(ctx: Context) = Unit

    fun autoCheck(ctx: Context, notify: Boolean = false) = Unit

    fun act(ctx: Context) = Unit
}
