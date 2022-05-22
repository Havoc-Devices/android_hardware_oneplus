/*
 * Copyright (C) 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioSystem
import android.os.IBinder
import android.os.UEventObserver
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

class KeyHandler : Service() {
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator

    private var wasMuted = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
            val state = intent.getBooleanExtra(AudioManager.EXTRA_STREAM_VOLUME_MUTED, false)
            if (stream == AudioSystem.STREAM_MUSIC && state == false) {
                wasMuted = false
            }
        }
    }

    private val alertSliderEventObserver = object : UEventObserver() {
        private val lock = Any()

        override fun onUEvent(event: UEvent) {
            synchronized(lock) {
                event.get("SWITCH_STATE")?.let {
                    handleMode(it.toInt())
                    return
                }
                event.get("STATE")?.let {
                    val none = it.contains("USB=0")
                    val vibration = it.contains("HOST=0")
                    val silent = it.contains("null)=0")

                    if (none && !vibration && !silent) {
                        handleMode(POSITION_BOTTOM)
                    } else if (!none && vibration && !silent) {
                        handleMode(POSITION_MIDDLE)
                    } else if (!none && !vibration && silent) {
                        handleMode(POSITION_TOP)
                    }

                    return
                }
            }
        }
    }

    override fun onCreate() {
        audioManager = getSystemService(AudioManager::class.java)
        vibrator = getSystemService(Vibrator::class.java)

        registerReceiver(
            broadcastReceiver,
            IntentFilter(AudioManager.STREAM_MUTE_CHANGED_ACTION)
        )
        alertSliderEventObserver.startObserving("tri-state-key")
        alertSliderEventObserver.startObserving("tri_state_key")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun vibrateIfNeeded(mode: Int) {
        when (mode) {
            AudioManager.RINGER_MODE_VIBRATE -> vibrator.vibrate(MODE_VIBRATION_EFFECT)
            AudioManager.RINGER_MODE_NORMAL -> vibrator.vibrate(MODE_NORMAL_EFFECT)
        }
    }

    private fun handleMode(mode: Int) {
        val muteMedia = Settings.System.getInt(getContentResolver(),
                Settings.System.ALERT_SLIDER_MUTE_MEDIA, 0) == 1

        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> {
                audioManager.setRingerModeInternal(mode)
                if (muteMedia) {
                    audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                    wasMuted = true
                }
            }
            AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_NORMAL -> {
                audioManager.setRingerModeInternal(mode)
                if (muteMedia && wasMuted) {
                    audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
                }
            }
        }
        vibrateIfNeeded(mode)
    }

    companion object {
        private const val TAG = "KeyHandler"

        // Slider key positions
        private const val POSITION_TOP = 0
        private const val POSITION_MIDDLE = 1
        private const val POSITION_BOTTOM = 2

        // Vibration effects
        private val MODE_NORMAL_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK)
        private val MODE_VIBRATION_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }
}
