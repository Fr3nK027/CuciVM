package com.cuvm.ui.display

import android.annotation.SuppressLint
import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cuvm.MainActivity
import com.cuvm.R
import com.cuvm.engine.AndroidEngine
import com.cuvm.ui.theme.CuciVmTheme
import io.twoyi.Renderer
import java.io.File
import kotlin.concurrent.thread

/**
 * Hosts the Android guest. Runs in its own ":android" process: the Twoyi renderer starts the guest init
 * once per process, so shutting down the VM means ending this process.
 */
class AndroidVmActivity : ComponentActivity() {
    private val engine by lazy { AndroidEngine(applicationContext) }
    private var booted by mutableStateOf(false)

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rootfs = File(intent.getStringExtra(EXTRA_ROOTFS) ?: return finish())
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val miniBall = intent.getBooleanExtra(EXTRA_MINI_BALL, true)
        loadLibs()
        booted = Guest.booted
        if (!Guest.listening) listenForBoot()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            CuciVmTheme("dark", "slate") {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(h: SurfaceHolder) {
                                    val m = DisplayMetrics().also { @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(it) }
                                    if (!Guest.started) { engine.ensureBootFiles(rootfs); Guest.started = true }
                                    Renderer.init(h.surface, engine.loader.path, m.xdpi, m.ydpi, 60)
                                }
                                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) = Renderer.resetWindow(h.surface, 0, 0, width, height)
                                override fun surfaceDestroyed(h: SurfaceHolder) = Renderer.removeWindow(h.surface)
                            })
                            setOnTouchListener { _, e -> Renderer.handleTouch(e); true }
                        }
                    }, modifier = Modifier.fillMaxSize())

                    if (!booted) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.android_booting, name), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }

                    if (miniBall) MiniBall(
                        selfId = "", onShutdown = ::shutdown, onToPhone = { moveTaskToBack(true) },
                        onSettings = { startActivity(Intent(this@AndroidVmActivity, MainActivity::class.java)) },
                        onSwitch = {}, onKeyboard = {}, onPaste = {},
                        androidKeys = AndroidKeys(
                            back = { Renderer.sendKeycode(KeyEvent.KEYCODE_BACK) },
                            home = { Renderer.sendKeycode(KeyEvent.KEYCODE_HOME) },
                            recents = { Renderer.sendKeycode(KeyEvent.KEYCODE_APP_SWITCH) },
                        ),
                    )
                }
            }
        }
    }

    @Deprecated("Guest owns back")
    @Suppress("DEPRECATION")
    override fun onBackPressed() = Renderer.sendKeycode(KeyEvent.KEYCODE_BACK)

    private fun shutdown() {
        engine.killGuestProcesses()
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun loadLibs() {
        if (Guest.loaded) return
        // Load order matters: libtwoyi needs libOpenglRender by soname, already loaded resolves it.
        System.load(engine.openGl.path)
        System.load(engine.twoyi.path)
        Guest.loaded = true
    }

    /** The guest's helper app reports BOOT_COMPLETED on this abstract socket (Twoyi protocol). */
    private fun listenForBoot() {
        Guest.listening = true
        thread(name = "guest-sock", isDaemon = true) {
            try {
                val s = LocalSocket(LocalSocket.SOCKET_SEQPACKET)
                s.bind(LocalSocketAddress("TWOYI_SOCK", LocalSocketAddress.Namespace.ABSTRACT))
                val server = LocalServerSocket(s.fileDescriptor)
                while (true) {
                    val c = server.accept()
                    thread(isDaemon = true) {
                        val buf = ByteArray(1024)
                        try {
                            while (true) {
                                val n = c.inputStream.read(buf)
                                if (n <= 0) break
                                if (String(buf, 0, n, Charsets.US_ASCII).startsWith("BOOT_COMPLETED")) markBooted()
                            }
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }
        }
        // Fallback when the helper never reports: show the guest anyway.
        window.decorView.postDelayed(::markBooted, 30_000)
    }

    private fun markBooted() {
        Guest.booted = true
        runOnUiThread { booted = true }
    }

    companion object {
        const val EXTRA_ROOTFS = "rootfs"
        const val EXTRA_NAME = "name"
        const val EXTRA_MINI_BALL = "mini_ball"
        const val PROCESS_SUFFIX = ":android"
    }
}

/** Per-process guest state (this process lives exactly as long as the guest). */
private object Guest {
    var loaded = false
    var started = false
    var listening = false
    var booted = false
}

class AndroidKeys(val back: () -> Unit, val home: () -> Unit, val recents: () -> Unit)
