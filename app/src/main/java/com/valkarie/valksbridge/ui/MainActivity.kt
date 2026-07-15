package com.valkarie.valksbridge.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valkarie.valksbridge.ble.BleState
import com.valkarie.valksbridge.ble.VaultStatus

// ---- Design tokens ----
private val BgColor     = Color(0xFF0D0D0F)
private val Surface1    = Color(0xFF16171A)
private val Surface2    = Color(0xFF1E1F23)
private val Border      = Color(0xFF2A2B30)
private val Green       = Color(0xFF4ADE80)
private val Blue        = Color(0xFF60A5FA)
private val Pink        = Color(0xFFF472B6)
private val Muted       = Color(0xFF6B7280)
private val Danger      = Color(0xFFF87171)
private val TextPrimary = Color(0xFFE8E9ED)
private val TextDim     = Color(0xFF9CA3AF)
private val GreenDim    = Color(0xFF1A4A2E)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* user handles via connect button */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permLauncher.launch(arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        ))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background   = BgColor,
                surface      = Surface1,
                onBackground = TextPrimary,
                onSurface    = TextPrimary,
                primary      = Green,
            )) {
                Surface(Modifier.fillMaxSize(), color = BgColor) {
                    ValksBridgeScreen(vm)
                }
            }
        }
    }
}

// ---- Root screen ----
@Composable
fun ValksBridgeScreen(vm: MainViewModel) {
    val bleState    by vm.bleState.collectAsState()
    val vaultStatus by vm.vaultStatus.collectAsState()
    val tab         by vm.tab.collectAsState()
    val password    by vm.password.collectAsState()
    val message     by vm.message.collectAsState()
    val sensitivity by vm.sensitivity.collectAsState()
    val connected   = bleState is BleState.Connected

    Column(
        Modifier.fillMaxSize().background(BgColor).padding(20.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(GreenDim),
                contentAlignment = Alignment.Center) {
                Text("⚡", fontSize = 20.sp)
            }
            Column {
                Text("ValksBridge", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("BLE → USB HID", fontSize = 11.sp, color = Muted, fontFamily = FontFamily.Monospace)
            }
        }

        // Status
        StatusCard(bleState)

        // Tabs
        if (connected) {
            val tabs = listOf("pw" to "⌨ Text", "kb" to "⌨ Keyboard", "ms" to "🖱 Mouse")
            val idx  = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)
            TabRow(selectedTabIndex = idx, containerColor = Surface1, contentColor = Green,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))) {
                tabs.forEach { (t, label) ->
                    Tab(selected = tab == t, onClick = { vm.setTab(t) },
                        text = {
                            Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = when { tab != t -> TextDim; t == "kb" -> Blue; t == "ms" -> Pink; else -> Green })
                        })
                }
            }
        }

        // Tab content
        when {
            !connected || tab == "pw" -> PasswordTab(password, connected, vaultStatus == VaultStatus.TYPING,
                vm::onPasswordChange, vm::sendPassword)
            tab == "kb" -> KeyboardTab(vm)
            tab == "ms" -> MouseTab(sensitivity, vm::setSensitivity,
                onMove   = { dx, dy -> vm.sendMouse(dx = dx.toInt(), dy = dy.toInt()) },
                onClick  = { btn     -> vm.sendMouse(buttons = btn) },
                onScroll = { d       -> vm.sendMouse(scroll  = d) }
            )
        }

        // Feedback
        when (vaultStatus) {
            VaultStatus.TYPING    -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
            VaultStatus.DONE      -> Chip("✓  Typed successfully",    Green,  Color(0xFF0F2A1A))
            VaultStatus.ERR_ASCII -> Chip("✗  Non-ASCII not supported", Danger, Color(0xFF2A0F0F))
            VaultStatus.ERR_BOND  -> Chip("✗  BLE bond required",      Danger, Color(0xFF2A0F0F))
            else -> {}
        }
        if (message.isNotBlank()) {
            Text(message, fontSize = 12.sp, color = TextDim, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.weight(1f))

        // Connect / Disconnect
        if (!connected) {
            Button(onClick = vm::connect, modifier = Modifier.fillMaxWidth(),
                enabled = bleState !is BleState.Scanning && bleState !is BleState.Connecting,
                shape   = RoundedCornerShape(10.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = GreenDim, contentColor = Green)
            ) {
                Text(when (bleState) {
                    BleState.Scanning   -> "Scanning for VaultBridge..."
                    BleState.Connecting -> "Connecting..."
                    else -> "Connect to VaultBridge"
                }, fontFamily = FontFamily.Monospace)
            }
        } else {
            OutlinedButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shape  = RoundedCornerShape(10.dp)
            ) { Text("Disconnect", color = TextDim) }
        }
    }
}

// ---- Status card ----
@Composable
fun StatusCard(bleState: BleState) {
    val (dot, label) = when (bleState) {
        BleState.Idle        -> Muted                 to "Not connected"
        BleState.Scanning    -> Color(0xFFFACC15)     to "Scanning..."
        BleState.Connecting  -> Blue                  to "Connecting..."
        BleState.Connected   -> Green                 to "Connected"
        is BleState.Error    -> Danger                to "Error: ${bleState.msg}"
    }
    Surface(color = Surface1, shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(10.dp))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(label, fontSize = 13.sp, color = if (bleState is BleState.Connected) Green else TextDim,
                fontFamily = FontFamily.Monospace)
        }
    }
}

// ---- Password tab ----
@Composable
fun PasswordTab(password: String, enabled: Boolean, typing: Boolean,
                onChange: (String) -> Unit, onSend: () -> Unit) {
    var showPw by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Surface1, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(10.dp))) {
            Column(Modifier.padding(14.dp)) {
                Text("TEXT TO INJECT", fontSize = 10.sp, color = Muted,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = password, onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    placeholder = { Text(if (enabled) "Enter Text..." else "Connect first",
                        color = Muted, fontFamily = FontFamily.Monospace) },
                    trailingIcon = {
                        TextButton(onClick = { showPw = !showPw }) {
                            Text(if (showPw) "hide" else "show", fontSize = 11.sp, color = Muted)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Green, unfocusedBorderColor = Border,
                        focusedTextColor     = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Green)
                )
            }
        }
        if (enabled) {
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotBlank() && !typing,
                shape   = RoundedCornerShape(10.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
            ) { Text(if (typing) "Typing..." else "Type via USB HID", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---- Keyboard tab ----
@Composable
fun KeyboardTab(vm: MainViewModel) {
    var shift by remember { mutableStateOf(false) }
    var ctrl  by remember { mutableStateOf(false) }
    var alt   by remember { mutableStateOf(false) }

    val rows = listOf(
        listOf("Esc","F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"),
        listOf("`","1","2","3","4","5","6","7","8","9","0","-","=","⌫"),
        listOf("Tab","Q","W","E","R","T","Y","U","I","O","P","[","]","\\"),
        listOf("Caps","A","S","D","F","G","H","J","K","L",";","'","↵"),
        listOf("⇧","Z","X","C","V","B","N","M",",",".","/","⇧"),
        listOf("Ctrl","Alt","Space","↑","↓","←","→","Del"),
    )

    Surface(color = Surface1, shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(10.dp))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("VIRTUAL KEYBOARD", fontSize = 10.sp, color = Muted,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))

            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { key ->
                        val isActive = (key == "⇧" && shift) || (key == "Ctrl" && ctrl) || (key == "Alt" && alt)
                        val weight   = when (key) { "Space" -> 3f; "⌫","↵","Tab","Caps","⇧" -> 1.5f; else -> 1f }
                        Button(
                            onClick = {
                                when (key) {
                                    "⇧"    -> shift = !shift
                                    "Ctrl" -> ctrl  = !ctrl
                                    "Alt"  -> alt   = !alt
                                    else   -> {
                                        vm.sendKeyLabel(key, shift, ctrl, alt)
                                        if (shift && key !in listOf("⇧","Ctrl","Alt")) shift = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(weight).height(34.dp),
                            shape    = RoundedCornerShape(5.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) Color(0xFF1A2A4A) else Surface2,
                                contentColor   = if (isActive) Blue else TextDim)
                        ) { Text(key, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
            Text("กดปุ่มเพื่อส่ง keycode ออก USB HID ทันที · Ctrl/Shift/Alt ค้างไว้ได้",
                fontSize = 10.sp, color = Muted, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---- Mouse tab ----
@Composable
fun MouseTab(sensitivity: Float, onSensChange: (Float) -> Unit,
             onMove: (Float, Float) -> Unit, onClick: (Int) -> Unit, onScroll: (Int) -> Unit) {
    Surface(color = Surface1, shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(10.dp))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TRACKPAD", fontSize = 10.sp, color = Muted, fontFamily = FontFamily.Monospace)

            Box(Modifier.fillMaxWidth().height(180.dp)
                .clip(RoundedCornerShape(8.dp)).background(BgColor)
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .pointerInput(Unit) { detectDragGestures { _, drag -> onMove(drag.x, drag.y) } },
                contentAlignment = Alignment.Center) {
                Text("Drag to move cursor", fontSize = 12.sp, color = Muted, fontFamily = FontFamily.Monospace)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Left" to 0x01, "Middle" to 0x04, "Right" to 0x02).forEach { (label, btn) ->
                    OutlinedButton(onClick = { onClick(btn) }, modifier = Modifier.weight(1f),
                        shape  = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(8.dp)
                    ) { Text(label, fontSize = 12.sp, color = TextDim, fontFamily = FontFamily.Monospace) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("▲ Scroll Up" to 3, "▼ Scroll Down" to -3).forEach { (label, d) ->
                    OutlinedButton(onClick = { onScroll(d) }, modifier = Modifier.weight(1f),
                        shape  = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(8.dp)
                    ) { Text(label, fontSize = 12.sp, color = TextDim, fontFamily = FontFamily.Monospace) }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sensitivity", fontSize = 11.sp, color = Muted, fontFamily = FontFamily.Monospace)
                Slider(value = sensitivity, onValueChange = onSensChange, valueRange = 1f..8f, steps = 6,
                    modifier = Modifier.weight(1f),
                    colors   = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink))
                Text("${sensitivity.toInt()}x", fontSize = 11.sp, color = Pink,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
            }
        }
    }
}

// ---- Feedback chip ----
@Composable
fun Chip(text: String, textColor: Color, bgColor: Color) {
    Surface(color = bgColor, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 12.sp, color = textColor, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}
