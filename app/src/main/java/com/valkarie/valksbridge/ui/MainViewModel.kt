package com.valkarie.valksbridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valkarie.valksbridge.ble.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = ValksBleManager(app.applicationContext)

    val bleState:    StateFlow<BleState>     = ble.state
    val vaultStatus: StateFlow<VaultStatus>  = ble.status

    private val _tab         = MutableStateFlow("pw")
    private val _password    = MutableStateFlow("")
    private val _message     = MutableStateFlow("")
    private val _sensitivity = MutableStateFlow(2f)

    val tab:         StateFlow<String> = _tab
    val password:    StateFlow<String> = _password
    val message:     StateFlow<String> = _message
    val sensitivity: StateFlow<Float>  = _sensitivity

    fun setTab(t: String)           { _tab.value = t }
    fun onPasswordChange(v: String) { _password.value = v }
    fun setSensitivity(v: Float)    { _sensitivity.value = v }

    fun connect()    = ble.connect()
    fun disconnect() { ble.disconnect(); _message.value = "Disconnected" }

    fun sendPassword() {
        val pw = _password.value.trim()
        if (pw.isBlank()) { _message.value = "Password is empty"; return }
        viewModelScope.launch {
            ble.sendPassword(pw).fold(
                onSuccess = { _message.value = "✓ Sent"; _password.value = "" },
                onFailure = { _message.value = "✗ ${it.message}" }
            )
        }
    }

    // HID keycode map: label -> Pair(modifier, hidKeycode)
    private val KEY_TO_HID = mapOf(
        "A" to (0x00 to 0x04), "B" to (0x00 to 0x05), "C" to (0x00 to 0x06),
        "D" to (0x00 to 0x07), "E" to (0x00 to 0x08), "F" to (0x00 to 0x09),
        "G" to (0x00 to 0x0A), "H" to (0x00 to 0x0B), "I" to (0x00 to 0x0C),
        "J" to (0x00 to 0x0D), "K" to (0x00 to 0x0E), "L" to (0x00 to 0x0F),
        "M" to (0x00 to 0x10), "N" to (0x00 to 0x11), "O" to (0x00 to 0x12),
        "P" to (0x00 to 0x13), "Q" to (0x00 to 0x14), "R" to (0x00 to 0x15),
        "S" to (0x00 to 0x16), "T" to (0x00 to 0x17), "U" to (0x00 to 0x18),
        "V" to (0x00 to 0x19), "W" to (0x00 to 0x1A), "X" to (0x00 to 0x1B),
        "Y" to (0x00 to 0x1C), "Z" to (0x00 to 0x1D),
        "1" to (0x00 to 0x1E), "2" to (0x00 to 0x1F), "3" to (0x00 to 0x20),
        "4" to (0x00 to 0x21), "5" to (0x00 to 0x22), "6" to (0x00 to 0x23),
        "7" to (0x00 to 0x24), "8" to (0x00 to 0x25), "9" to (0x00 to 0x26),
        "0" to (0x00 to 0x27),
        "↵" to (0x00 to 0x28), "⌫" to (0x00 to 0x2A), "Tab" to (0x00 to 0x2B),
        "Space" to (0x00 to 0x2C), "Esc" to (0x00 to 0x29), "Caps" to (0x00 to 0x39),
        "-" to (0x00 to 0x2D), "=" to (0x00 to 0x2E),
        "[" to (0x00 to 0x2F), "]" to (0x00 to 0x30), "\\" to (0x00 to 0x31),
        ";" to (0x00 to 0x33), "'" to (0x00 to 0x34), "`" to (0x00 to 0x35),
        "," to (0x00 to 0x36), "." to (0x00 to 0x37), "/" to (0x00 to 0x38),
        "F1"  to (0x00 to 0x3A), "F2"  to (0x00 to 0x3B), "F3"  to (0x00 to 0x3C),
        "F4"  to (0x00 to 0x3D), "F5"  to (0x00 to 0x3E), "F6"  to (0x00 to 0x3F),
        "F7"  to (0x00 to 0x40), "F8"  to (0x00 to 0x41), "F9"  to (0x00 to 0x42),
        "F10" to (0x00 to 0x43), "F11" to (0x00 to 0x44), "F12" to (0x00 to 0x45),
        "Del" to (0x00 to 0x4C), "Home" to (0x00 to 0x4A), "End" to (0x00 to 0x4D),
        "PgUp" to (0x00 to 0x4B), "PgDn" to (0x00 to 0x4E),
        "→" to (0x00 to 0x4F), "←" to (0x00 to 0x50),
        "↓" to (0x00 to 0x51), "↑" to (0x00 to 0x52),
    )

    fun sendKeyLabel(label: String, shift: Boolean = false, ctrl: Boolean = false, alt: Boolean = false) {
        val (baseMod, hid) = KEY_TO_HID[label] ?: KEY_TO_HID[label.uppercase()] ?: return
        var mod = baseMod
        if (shift) mod = mod or 0x02
        if (ctrl)  mod = mod or 0x01
        if (alt)   mod = mod or 0x04
        viewModelScope.launch { ble.sendKey(mod, hid) }
    }

    fun releaseKey() { viewModelScope.launch { ble.releaseKey() } }

    fun sendMouse(buttons: Int = 0, dx: Int = 0, dy: Int = 0, scroll: Int = 0) {
        val s = _sensitivity.value
        viewModelScope.launch {
            ble.sendMouse(buttons, (dx * s).toInt(), (dy * s).toInt(), scroll)
        }
    }

    override fun onCleared() { super.onCleared(); ble.clear() }
}
