package com.cuvm.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cuvm.data.VmRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface VmState {
    data object Stopped : VmState
    data class Preparing(val step: String) : VmState
    data object Booting : VmState
    data class Running(val port: Int) : VmState
    data object Stopping : VmState
    data class Error(val message: String) : VmState
}

val VmState.active get() = this !is VmState.Stopped && this !is VmState.Error

/** Owns every guest. One state machine per VM: start is ignored unless stopped or failed. */
class VmRuntime(
    private val context: Context, private val repo: VmRepo,
    private val engine: ProotEngine, private val space: AppSpace,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, VmState>>(emptyMap())
    val states = _states.asStateFlow()
    private val procs = ConcurrentHashMap<String, Process>()
    private val displays = ConcurrentHashMap<String, Int>()

    fun state(id: String) = states.map { it[id] ?: VmState.Stopped }
    fun current(id: String) = states.value[id] ?: VmState.Stopped

    private fun set(id: String, s: VmState) = _states.update { it + (id to s) }

    @Synchronized
    private fun allocDisplay(id: String): Int {
        val used = displays.values.toSet()
        return (1..99).first { it !in used }.also { displays[id] = it }
    }

    @Synchronized
    fun start(id: String) {
        if (current(id).active) return
        set(id, VmState.Preparing("start"))
        scope.launch {
            try {
                val vm = repo.get(id) ?: return@launch set(id, VmState.Stopped)
                val image = repo.image(vm.imageId) ?: error("image missing")
                if (image.engine == AppSpace.ENGINE) {
                    // Apps run in the engine's own processes; the "VM" is its virtual user.
                    space.ensureUser(repo.spaceUser(vm))
                    return@launch set(id, VmState.Running(0))
                }
                ContextCompat.startForegroundService(context, Intent(context, VmService::class.java))
                engine.prepare(vm, image) { set(id, VmState.Preparing(it)) }
                set(id, VmState.Booting)
                val display = allocDisplay(id)
                val port = 5900 + display
                val proc = engine.start(vm, image, display)
                procs[id] = proc
                if (!waitForPort(port, proc)) {
                    proc.destroy()
                    error("display did not start (see boot.log)")
                }
                set(id, VmState.Running(port))
                val code = proc.waitFor()
                // Exit while still "running" means the guest or the system ended it.
                if (current(id) is VmState.Running) {
                    set(id, if (code == 0) VmState.Stopped else VmState.Error("exit $code"))
                } else set(id, VmState.Stopped)
            } catch (e: Exception) {
                set(id, VmState.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                procs.remove(id)
                displays.remove(id)
            }
        }
    }

    fun stop(id: String) {
        val p = procs[id]
        if (p == null) {
            // App space: stop every app of that virtual user.
            if (current(id) is VmState.Running) scope.launch {
                set(id, VmState.Stopping)
                repo.get(id)?.spaceUser?.let(space::stopAll)
                set(id, VmState.Stopped)
            }
            return
        }
        set(id, VmState.Stopping)
        scope.launch {
            p.destroy()
            if (!p.waitFor(10, TimeUnit.SECONDS)) p.destroyForcibly()
        }
    }

    fun stopAll() = states.value.filterValues { it.active }.keys.forEach(::stop)

    private suspend fun waitForPort(port: Int, proc: Process): Boolean {
        repeat(120) {
            if (!proc.isAlive) return false
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 250) }
                return true
            } catch (_: Exception) {
                delay(250)
            }
        }
        return false
    }
}
