package com.valkarie.valksbridge.ble

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.core.data.BleGattConnectOptions
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator

class ValksBleManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state  = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state

    private val _status = MutableStateFlow(VaultStatus.UNKNOWN)
    val status: StateFlow<VaultStatus> = _status

    private var gatt:     ClientBleGatt?                = null
    private var pwChr:    ClientBleGattCharacteristic?  = null
    private var keyChr:   ClientBleGattCharacteristic?  = null
    private var mouseChr: ClientBleGattCharacteristic?  = null

    fun connect() {
        scope.launch {
            try {
                _state.value = BleState.Scanning
                val aggregator = BleScanResultAggregator()
                val device = BleScanner(context).scan()
                    .map { aggregator.aggregateDevices(it) }
                    .mapNotNull { results ->
                        results.firstOrNull { it.name == "VaultBridge" }
                    }
                    .first()

                _state.value = BleState.Connecting
                gatt = ClientBleGatt.connect(
                    context = context,
                    device  = device,
                    scope   = scope,
                    options = BleGattConnectOptions()
                )
                gatt!!.discoverServices()

                val svc = gatt!!.services.filterNotNull().first().findService(SERVICE_UUID)
                    ?: error("VaultBridge service not found")

                pwChr    = svc.findCharacteristic(PASSWORD_IN_UUID)
                keyChr   = svc.findCharacteristic(KEY_IN_UUID)
                mouseChr = svc.findCharacteristic(MOUSE_IN_UUID)

                svc.findCharacteristic(STATUS_OUT_UUID)?.let { chr ->
                    scope.launch {
                        chr.getNotifications().collect { data: DataByteArray ->
                            val b = data.value.firstOrNull() ?: return@collect
                            _status.value = VaultStatus.from(b)
                        }
                    }
                }

                _state.value = BleState.Connected

            } catch (e: Exception) {
                _state.value = BleState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt = null; pwChr = null; keyChr = null; mouseChr = null
        _state.value  = BleState.Idle
        _status.value = VaultStatus.UNKNOWN
    }

    // Samsung ต้องการ WRITE_NO_RESPONSE — ลอง NO_RESPONSE ก่อน ถ้าไม่ได้ fallback เป็น DEFAULT
    private suspend fun smartWrite(chr: ClientBleGattCharacteristic, data: ByteArray) {
        val dataByteArray = DataByteArray(data)
        try {
            chr.write(dataByteArray, BleWriteType.NO_RESPONSE)
        } catch (e: Exception) {
            chr.write(dataByteArray, BleWriteType.DEFAULT)
        }
    }

    suspend fun sendPassword(password: String): Result<Unit> = runCatching {
        val chr = pwChr ?: error("Not connected")
        val bytes = password.toByteArray(Charsets.US_ASCII)
        require(bytes.size <= 64) { "Password too long (max 64 chars)" }
        smartWrite(chr, bytes)
    }

    suspend fun sendKey(modifier: Int, keycode: Int): Result<Unit> = runCatching {
        keyChr?.let { smartWrite(it, byteArrayOf(modifier.toByte(), keycode.toByte())) }
    }

    suspend fun releaseKey(): Result<Unit> = runCatching {
        keyChr?.let { smartWrite(it, byteArrayOf(0x00, 0x00)) }
    }

    suspend fun sendMouse(
        buttons: Int = 0, dx: Int = 0, dy: Int = 0, scroll: Int = 0
    ): Result<Unit> = runCatching {
        mouseChr?.let {
            smartWrite(it, byteArrayOf(
                buttons.toByte(), dx.toSigned8(), dy.toSigned8(), scroll.toSigned8()
            ))
        }
    }

    fun clear() { scope.cancel(); disconnect() }
}
