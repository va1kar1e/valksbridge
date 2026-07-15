package com.valkarie.valksbridge.ble

import java.util.UUID

val SERVICE_UUID     = UUID.fromString("15e4ec59-68e1-4f9a-95a7-d865f2382d34")
val PASSWORD_IN_UUID = UUID.fromString("1b0e5fb0-9445-4f88-ae31-aa6ccb34df33")
val KEY_IN_UUID      = UUID.fromString("2c3f7a1b-0e45-4d89-b2c6-5a8f9e1d3047")
val MOUSE_IN_UUID    = UUID.fromString("3d4e8b2c-1f56-5e9a-c3d7-6b9f0f2e4158")
val STATUS_OUT_UUID  = UUID.fromString("789eea0b-843b-45a5-b8b6-2eb6547e76fa")

enum class VaultStatus(val code: Byte) {
    READY(0x01), TYPING(0x02), DONE(0x03),
    ERR_ASCII(0x04), ERR_BOND(0x06), UNKNOWN(0xFF.toByte());
    companion object {
        fun from(b: Byte) = entries.find { it.code == b } ?: UNKNOWN
    }
}

sealed class BleState {
    data object Idle       : BleState()
    data object Scanning   : BleState()
    data object Connecting : BleState()
    data object Connected  : BleState()
    data class  Error(val msg: String) : BleState()
}

fun Int.clamp8(): Int  = coerceIn(-127, 127)
fun Int.toSigned8(): Byte = clamp8().toByte()
