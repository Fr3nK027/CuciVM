package app.vmbox.data

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.vmbox.VmBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class ImageRepo(private val context: Context, private val dao: VmDao) {
    val images = dao.images()

    /** imageId -> 0..1 while downloading. Same-process state, written by [DownloadWorker]. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress = _progress.asStateFlow()

    private val dir get() = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun syncCatalogue() {
        val now = System.currentTimeMillis()
        dao.insertImagesIfMissing(Catalogue.images.map { it.copy(createdAt = now, updatedAt = now) })
        // A download killed with the app stays "downloading" in the DB; WorkManager resumes it.
    }

    fun download(id: String) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("dl-$id", ExistingWorkPolicy.KEEP, req)
    }

    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("dl-$id")
        setProgress(id, null)
    }

    internal fun setProgress(id: String, p: Float?) =
        _progress.update { if (p == null) it - id else it + (id to p) }

    internal fun partFile(id: String) = File(dir, "$id.part")
    internal fun finalFile(id: String) = File(dir, "$id.tar.gz")

    /** Copies a user-picked rootfs tarball into app storage. */
    suspend fun importCustom(uri: Uri, displayName: String): GuestImage = withContext(Dispatchers.IO) {
        val id = "custom-" + UUID.randomUUID()
        val out = finalFile(id)
        val size = context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { input.copyTo(it) }
        } ?: throw IOException("cannot open $uri")
        val now = System.currentTimeMillis()
        GuestImage(
            id = id, os = "linux", name = displayName, versionLabel = "custom", arch = "arm64",
            engine = "proot", source = "custom", downloadUrl = null, sha256 = null, sizeBytes = size,
            localPath = out.absolutePath, status = "ready", createdAt = now, updatedAt = now,
        ).also { dao.upsertImage(it) }
    }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = VmBox.images
        val dao = VmBox.db.dao()
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val img = dao.image(id) ?: return@withContext Result.failure()
        val url = img.downloadUrl ?: return@withContext Result.failure()
        dao.updateImage(img.copy(status = "downloading", updatedAt = System.currentTimeMillis()))
        val part = repo.partFile(id)
        try {
            val have = if (part.exists()) part.length() else 0L
            val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val append = resp.code == 206
                val total = if (append) have + resp.body!!.contentLength() else resp.body!!.contentLength()
                var done = if (append) have else 0L
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    val input = resp.body!!.byteStream()
                    while (true) {
                        if (isStopped) return@withContext Result.failure()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) repo.setProgress(id, done.toFloat() / total)
                    }
                }
            }
            img.sha256?.let { want ->
                val got = sha256(part)
                if (!got.equals(want, ignoreCase = true)) {
                    part.delete()
                    throw IOException("checksum mismatch")
                }
            }
            val final = repo.finalFile(id)
            if (!part.renameTo(final)) throw IOException("rename failed")
            dao.updateImage(img.copy(status = "ready", localPath = final.absolutePath, updatedAt = System.currentTimeMillis()))
            Result.success()
        } catch (e: IOException) {
            dao.updateImage(img.copy(status = "failed", updatedAt = System.currentTimeMillis()))
            Result.failure()
        } finally {
            repo.setProgress(id, null)
            // Cancelled mid-download: back to "remote" so the UI offers Download again (the .part file resumes).
            withContext(NonCancellable) {
                dao.image(id)?.takeIf { it.status == "downloading" }?.let { dao.updateImage(it.copy(status = "remote")) }
            }
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
