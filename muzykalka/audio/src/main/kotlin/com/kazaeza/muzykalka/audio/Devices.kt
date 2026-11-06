package com.kazaeza.muzykalka.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ticket M-07: are headphones connected? (wired, USB, Bluetooth). Opens the duplex gate. */
class HeadphoneMonitor(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val state = MutableStateFlow(check())
    val connected: StateFlow<Boolean> = state.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            state.value = check()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            state.value = check()
        }
    }

    init {
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun check(): Boolean =
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in HEADPHONES } == true

    private companion object {
        val HEADPHONES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}

/** An input the user can pick in Settings. */
data class InputDevice(val id: Int, val label: String, val warning: String? = null)

object InputDevices {
    fun list(context: Context): List<InputDevice> {
        val am = context.applicationContext.getSystemService(AudioManager::class.java) ?: return emptyList()
        val seen = mutableSetOf<Int>()
        return am.getDevices(AudioManager.GET_DEVICES_INPUTS).mapNotNull { d ->
            val (label, warning) = when (d.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Микрофон телефона" to null
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY ->
                    "USB: ${d.productName}" to null
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводная гарнитура" to null
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET ->
                    "Bluetooth: ${d.productName}" to "у Bluetooth большая задержка"
                else -> return@mapNotNull null
            }
            if (!seen.add(d.type)) null else InputDevice(d.id, label, warning)
        }
    }
}
