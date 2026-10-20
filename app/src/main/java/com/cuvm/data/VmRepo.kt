package com.cuvm.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VmRepo(private val context: Context, private val dao: VmDao) {
    val vms = dao.vms()
    fun vm(id: String) = dao.vmFlow(id)
    fun identity(id: String) = dao.identity(id)
    fun network(id: String) = dao.network(id)
    fun sensors(id: String) = dao.sensors(id)
    fun location(id: String) = dao.location(id)

    suspend fun get(id: String) = dao.vm(id)
    suspend fun image(id: String) = dao.image(id)

    suspend fun defaultName(image: GuestImage): String {
        val base = image.name.substringBefore(" ·")
        return "${base}_${dao.vmCount() + 1}"
    }

    suspend fun create(image: GuestImage, width: Int, height: Int, dpi: Int, name: String): Vm {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dir = File(context.filesDir, "vms/$id").apply { mkdirs() }
        val vm = Vm(
            id = id, imageId = image.id, name = name.trim(), sortOrder = dao.vmCount(),
            width = width, height = height, dpi = dpi, dataPath = dir.absolutePath,
            createdAt = now, updatedAt = now,
        )
        dao.insertVmWithDefaults(
            vm, Identity.newIdentity(id, now), Identity.newNetwork(id, now),
            Identity.newSensors(id), VmLocation(id),
        )
        return vm
    }

    suspend fun update(vm: Vm) = dao.updateVm(vm.copy(updatedAt = System.currentTimeMillis()))
    suspend fun updateIdentity(v: VmIdentity) = dao.updateIdentity(v.copy(updatedAt = System.currentTimeMillis()))
    suspend fun updateNetwork(v: VmNetwork) = dao.updateNetwork(v.copy(updatedAt = System.currentTimeMillis()))
    suspend fun updateSensor(v: VmSensor) = dao.updateSensor(v)
    suspend fun updateLocation(v: VmLocation) = dao.upsertLocation(v)

    /** Wipes the guest filesystem; next start extracts and sets up again. */
    suspend fun reset(vm: Vm) = withContext(Dispatchers.IO) {
        File(vm.dataPath).deleteRecursively()
        File(vm.dataPath).mkdirs()
    }

    /** Re-runs first-boot setup on next start, keeps user files. */
    suspend fun repair(vm: Vm) = withContext(Dispatchers.IO) {
        File(vm.dataPath, "rootfs/.vmbox-setup").delete()
    }

    suspend fun delete(vm: Vm) = withContext(Dispatchers.IO) {
        dao.deleteVm(vm.id)
        File(vm.dataPath).deleteRecursively()
    }
}
