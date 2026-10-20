package com.cuvm.ui.display

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuvm.R
import com.cuvm.CuciVm
import com.cuvm.engine.VmState
import com.cuvm.ui.home.stateText
import com.cuvm.vnc.Keysyms
import com.cuvm.vnc.RfbClient
import kotlin.math.abs
import kotlin.math.min

private const val SENTINEL = "  "

@Composable
fun DisplayScreen(id: String, onToPhone: () -> Unit, onSettings: () -> Unit, onSwitch: (String) -> Unit) {
    val vm by CuciVm.vms.vm(id).collectAsStateWithLifecycle(null)
    val state by CuciVm.runtime.state(id).collectAsStateWithLifecycle(VmState.Stopped)
    val ctx = LocalContext.current
    BackHandler(onBack = onToPhone)

    // Full screen while the guest is shown.
    DisposableEffect(Unit) {
        val window = (ctx as Activity).window
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ctl.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val v = vm ?: return
    // Android guests have their own activity/process; this screen only shows their preparation.
    val engineType by produceState<String?>(null, v.imageId) { value = CuciVm.vms.image(v.imageId)?.engine }
    val isAndroid = engineType == "android_container"
    LaunchedEffect(state, isAndroid) {
        if (isAndroid && (state is VmState.Running || state is VmState.Booting)) { CuciVm.runtime.showAndroid(v); onToPhone() }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .then(if (v.cutoutMode == "exclude") Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier),
    ) {
        val s = state
        if (s is VmState.Running && engineType == "proot") {
            GuestView(port = s.port, fps = v.refreshRate, shareClipboard = v.shareClipboard, navBar = v.navBar, miniBall = v.miniBall,
                onShutdown = { CuciVm.runtime.stop(id) }, onToPhone = onToPhone, onSettings = onSettings, onSwitch = onSwitch, selfId = id)
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(v.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(stateText(s), color = if (s is VmState.Error) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.8f))
                when (s) {
                    VmState.Stopped, is VmState.Error -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { CuciVm.runtime.start(id) }) { Text(stringResource(if (s is VmState.Error) R.string.retry else R.string.start)) }
                        TextButton(onClick = onToPhone) { Text(stringResource(R.string.to_phone)) }
                    }
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun GuestView(
    port: Int, fps: Int, shareClipboard: Boolean, navBar: String, miniBall: Boolean, selfId: String,
    onShutdown: () -> Unit, onToPhone: () -> Unit, onSettings: () -> Unit, onSwitch: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = remember { ctx.getSystemService(ClipboardManager::class.java) }
    var tick by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var closed by remember { mutableStateOf<String?>(null) }
    var reconnect by remember { mutableIntStateOf(0) }
    var client by remember { mutableStateOf<RfbClient?>(null) }

    DisposableEffect(port, reconnect) {
        closed = null
        val c = RfbClient(
            "127.0.0.1", port, fps,
            onFrame = { frame = it; tick++ },
            onCutText = { text ->
                if (shareClipboard) ContextCompat.getMainExecutor(ctx).execute { clipboard.setPrimaryClip(ClipData.newPlainText("vm", text)) }
            },
            onClosed = { closed = it?.message ?: "closed" },
        )
        client = c
        c.connect()
        onDispose { c.close(); client = null }
    }

    var box by remember { mutableStateOf(IntSize.Zero) }
    val bmp = frame
    // Letterbox: fit the guest framebuffer inside the view, keep aspect.
    val scale = if (bmp == null || box.width == 0) 1f else min(box.width.toFloat() / bmp.width, box.height.toFloat() / bmp.height)
    val off = if (bmp == null) Offset.Zero else Offset((box.width - bmp.width * scale) / 2, (box.height - bmp.height * scale) / 2)
    fun fb(p: Offset) = ((p.x - off.x) / scale).toInt() to ((p.y - off.y) / scale).toInt()

    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    var typed by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(SENTINEL.length))) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }

    fun sendKey(sym: Int) {
        val c = client ?: return
        if (ctrl) c.key(Keysyms.CTRL, true)
        if (alt) c.key(Keysyms.ALT, true)
        c.tap(sym)
        if (alt) c.key(Keysyms.ALT, false)
        if (ctrl) c.key(Keysyms.CTRL, false)
        ctrl = false; alt = false
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }
                .pointerInput(scale, off) {
                    detectTapGestures(
                        onTap = { p -> val (x, y) = fb(p); client?.pointer(x, y, 0); client?.pointer(x, y, 1); client?.pointer(x, y, 0) },
                        onLongPress = { p -> val (x, y) = fb(p); client?.pointer(x, y, 4); client?.pointer(x, y, 0) },
                    )
                }
                .pointerInput(scale, off) {
                    var last = Offset.Zero
                    detectDragGestures(
                        onDragStart = { p -> last = p; val (x, y) = fb(p); client?.pointer(x, y, 1) },
                        onDrag = { change, _ -> last = change.position; val (x, y) = fb(last); client?.pointer(x, y, 1) },
                        onDragEnd = { val (x, y) = fb(last); client?.pointer(x, y, 0) },
                        onDragCancel = { val (x, y) = fb(last); client?.pointer(x, y, 0) },
                    )
                },
        ) {
            if (bmp != null) {
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION") tick // redraw on every frame
                    drawImage(
                        bmp.asImageBitmap(), dstOffset = IntOffset(off.x.toInt(), off.y.toInt()),
                        dstSize = IntSize((bmp.width * scale).toInt(), (bmp.height * scale).toInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            } else if (closed == null) CircularProgressIndicator(Modifier.align(Alignment.Center))

            closed?.let { msg ->
                Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.display_lost, msg))
                        FilledTonalButton(onClick = { reconnect++ }) { Text(stringResource(R.string.reconnect)) }
                    }
                }
            }

            // Invisible field that owns the soft keyboard. IMEs rewrite their composing word on every key,
            // so diff old vs new text: chars after the common prefix are erased (backspace) or typed.
            BasicTextField(
                value = typed,
                onValueChange = { nv ->
                    val old = typed.text
                    val p = old.commonPrefixWith(nv.text).length
                    repeat(old.length - p) { sendKey(Keysyms.BACKSPACE) }
                    nv.text.substring(p).codePoints().forEach { sendKey(Keysyms.forChar(it)) }
                    // Refill the sentinel so backspace on an "empty" field still reaches the guest; trim when long.
                    typed = if (nv.composition == null && (nv.text.length < SENTINEL.length || nv.text.length > 64))
                        TextFieldValue(SENTINEL, TextRange(SENTINEL.length)) else nv
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii),
                modifier = Modifier.size(1.dp).focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val sym = when (e.key) {
                        Key.Enter -> Keysyms.RETURN; Key.Escape -> Keysyms.ESCAPE; Key.Tab -> Keysyms.TAB
                        Key.DirectionLeft -> Keysyms.LEFT; Key.DirectionRight -> Keysyms.RIGHT
                        Key.DirectionUp -> Keysyms.UP; Key.DirectionDown -> Keysyms.DOWN
                        Key.Delete -> Keysyms.DELETE
                        else -> null
                    }
                    sym?.let { sendKey(it); true } ?: false
                },
            )

            if (miniBall) MiniBall(
                onShutdown = onShutdown, onToPhone = onToPhone, onSettings = onSettings, onSwitch = onSwitch, selfId = selfId,
                onKeyboard = { focus.requestFocus(); kb?.show() },
                onPaste = { clipboard.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { client?.cutText(it) } },
            )
        }

        if (navBar != "none") KeyBar(
            ctrl = ctrl, alt = alt, onCtrl = { ctrl = !ctrl }, onAlt = { alt = !alt }, onKey = ::sendKey,
            onKeyboard = { focus.requestFocus(); kb?.show() },
            onScroll = { up -> val (x, y) = fb(Offset(box.width / 2f, box.height / 2f)); val b = if (up) 8 else 16; client?.pointer(x, y, b); client?.pointer(x, y, 0) },
        )
    }
}

@Composable
private fun KeyBar(ctrl: Boolean, alt: Boolean, onCtrl: () -> Unit, onAlt: () -> Unit, onKey: (Int) -> Unit, onKeyboard: () -> Unit, onScroll: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onKey(Keysyms.ESCAPE) }) { Text("Esc") }
            TextButton(onClick = { onKey(Keysyms.TAB) }) { Text("Tab") }
            FilterChip(selected = ctrl, onClick = onCtrl, label = { Text("Ctrl") })
            FilterChip(selected = alt, onClick = onAlt, label = { Text("Alt") })
            TextButton(onClick = { onKey(Keysyms.LEFT) }) { Text("←") }
            TextButton(onClick = { onKey(Keysyms.UP) }) { Text("↑") }
            TextButton(onClick = { onKey(Keysyms.DOWN) }) { Text("↓") }
            TextButton(onClick = { onKey(Keysyms.RIGHT) }) { Text("→") }
            // Swipe up/down on this key to scroll the guest (wheel buttons 4/5).
            Box(
                Modifier.size(48.dp).pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures { _, dy -> acc += dy; if (abs(acc) > 24f) { onScroll(acc > 0); acc = 0f } }
                },
                contentAlignment = Alignment.Center,
            ) { Text("⇕", color = MaterialTheme.colorScheme.primary) }
            TextButton(onClick = onKeyboard) { Text("⌨") }
        }
    }
}
