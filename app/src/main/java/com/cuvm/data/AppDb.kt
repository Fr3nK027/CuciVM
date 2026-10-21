package com.cuvm.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface VmDao {
    @Query("select * from guest_image order by source, name")
    fun images(): Flow<List<GuestImage>>

    @Query("select * from guest_image where id = :id")
    suspend fun image(id: String): GuestImage?

    @Query("select * from guest_image where source = 'catalogue'")
    suspend fun catalogueImages(): List<GuestImage>

    @Query("delete from guest_image where id = :id")
    suspend fun deleteImage(id: String)

    @Query("select * from guest_image where sha256 = :sha and status = 'ready' limit 1")
    suspend fun readyImageBySha(sha: String): GuestImage?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImagesIfMissing(images: List<GuestImage>)

    @Update
    suspend fun updateImage(image: GuestImage)

    @Upsert
    suspend fun upsertImage(image: GuestImage)

    @Query("select * from vm order by sortOrder, createdAt")
    fun vms(): Flow<List<Vm>>

    @Query("select * from vm where id = :id")
    fun vmFlow(id: String): Flow<Vm?>

    @Query("select * from vm where id = :id")
    suspend fun vm(id: String): Vm?

    @Query("select count(*) from vm where imageId = :imageId")
    suspend fun vmCountForImage(imageId: String): Int

    @Query("select count(*) from vm")
    suspend fun vmCount(): Int

    @Query("select max(spaceUser) from vm")
    suspend fun maxSpaceUser(): Int?

    @Insert
    suspend fun insertVm(vm: Vm)

    @Update
    suspend fun updateVm(vm: Vm)

    @Query("delete from vm where id = :id")
    suspend fun deleteVm(id: String)

    @Insert
    suspend fun insertIdentity(v: VmIdentity)

    @Update
    suspend fun updateIdentity(v: VmIdentity)

    @Query("select * from vm_identity where vmId = :vmId")
    fun identity(vmId: String): Flow<VmIdentity?>

    @Insert
    suspend fun insertNetwork(v: VmNetwork)

    @Update
    suspend fun updateNetwork(v: VmNetwork)

    @Query("select * from vm_network where vmId = :vmId")
    fun network(vmId: String): Flow<VmNetwork?>

    @Insert
    suspend fun insertSensors(v: List<VmSensor>)

    @Update
    suspend fun updateSensor(v: VmSensor)

    @Query("select * from vm_sensor where vmId = :vmId")
    fun sensors(vmId: String): Flow<List<VmSensor>>

    @Upsert
    suspend fun upsertLocation(v: VmLocation)

    @Query("select * from vm_location where vmId = :vmId")
    fun location(vmId: String): Flow<VmLocation?>

    @Transaction
    suspend fun insertVmWithDefaults(vm: Vm, id: VmIdentity, net: VmNetwork, sensors: List<VmSensor>, loc: VmLocation) {
        insertVm(vm); insertIdentity(id); insertNetwork(net); insertSensors(sensors); upsertLocation(loc)
    }
}

@Database(
    entities = [GuestImage::class, Vm::class, VmIdentity::class, VmNetwork::class, VmSensor::class, VmLocation::class],
    version = 4,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): VmDao

    companion object {
        private val V1_V2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("logoUrl", "mirrors", "parts", "setup", "session").forEach {
                    db.execSQL("alter table guest_image add column $it text")
                }
            }
        }

        private val V2_V3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("alter table vm add column spaceUser integer")
            }
        }

        private val V3_V4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("alter table vm add column locked integer not null default 0")
            }
        }

        fun build(context: Context) = Room.databaseBuilder(context, AppDb::class.java, "cucivm.db")
            .addMigrations(V1_V2, V2_V3, V3_V4).build()
    }
}
