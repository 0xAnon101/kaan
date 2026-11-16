package com.dronzer.kaan

import android.media.audiofx.Visualizer
import kotlin.math.log10
import kotlin.math.pow

/**
 * Precise mode. Reads how strong the audio signal is that the phone sends to the earphones.
 * It taps the phone's own output mix. It does not open the microphone and keeps no audio.
 * Android guards this tap with the "record audio" permission.
 */
class LevelMeter {
    private var viz: Visualizer? = null
    private val reading = Visualizer.MeasurementPeakRms()
    private var powerSum = 0.0
    private var count = 0

    val running: Boolean get() = viz != null

    fun start(): Boolean {
        if (viz != null) return true
        return runCatching {
            val v = Visualizer(0)
            v.setEnabled(false)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
            v.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED)
            v.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS)
            v.setEnabled(true)
            viz = v
        }.isSuccess
    }

    /** Takes one reading. Call a few times a second. */
    fun poll() {
        val v = viz ?: return
        runCatching {
            if (v.getMeasurementPeakRms(reading) == Visualizer.SUCCESS) {
                // mRms is in millibels below full scale.
                powerSum += 10.0.pow(reading.mRms / 1000.0)
                count++
            }
        }
    }

    /** Average signal level since the last call, in dB below full scale. Null when there were no readings. */
    fun take(): Double? {
        if (count == 0) return null
        val mean = powerSum / count
        powerSum = 0.0
        count = 0
        return if (mean <= 0.0) -96.0 else 10.0 * log10(mean)
    }

    fun stop() {
        val v = viz ?: return
        viz = null
        runCatching {
            v.setEnabled(false)
            v.release()
        }
    }
}
