package com.dronzer.kaan

/**
 * What the update row in Settings shows. Only debug builds can update themselves.
 * A release build comes from Google Play and never leaves [Idle].
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val build: Int) : UpdateState
    data class Downloading(val build: Int, val progress: Float) : UpdateState

    /** Android wants the "install unknown apps" permission for Kaan before the installer opens. */
    data class NeedsPermission(val build: Int) : UpdateState

    /** The APK is on the phone and the system installer has been opened. */
    data class Ready(val build: Int) : UpdateState
    data class Failed(val reason: String) : UpdateState
}
