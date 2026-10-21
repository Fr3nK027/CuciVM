package com.cuvm.data

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
import com.cuvm.CuciVm
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
import java.util.concurrent.TimeUnit

class ImageRepo(private val context: Context, private val dao: VmDao) {
    val images = dao.images()

    /** imageId -> 0..1 while downloading. Same-process state, written by [DownloadWorker]. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress = _progress.asStateFlow()

    /** os -> logo URL, from the catalogue; used for custom images. */
    private val _osLogos = MutableStateFlow<Map<String, String>>(emptyMap())
    val osLogos = _osLogos.asStateFlow()

    private val dir get() = File(context.filesDir, "images").apply { mkdirs() }
    private val cached get() = File(context.filesDir, "catalog.json")
    internal val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    /** Last good catalogue (downloaded copy, else the one in the APK), then the online one. */
    suspend fun syncCatalogue() = withContext(Dispatchers.IO) {
        val local = if (cached.exists()) cached.readText() else context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        merge(local)
        try {
            val body = http.newCall(Request.Builder().url(Catalogue.REMOTE_URL).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                r.body!!.string()
            }
            merge(body)          // throws on a malformed file, so a bad upload never replaces the cache
            cached.writeText(body)
        } catch (_: Exception) {
            // Offline or catalogue not published yet: keep the local list.
        }
    }

    private suspend fun merge(json: String) {
        val parsed = Catalogue.parse(json, System.currentTimeMillis())
        _osLogos.value = parsed.osLogos
        // Systems dropped from the catalogue disappear unless a VM still uses them.
        val ids = parsed.images.map { it.id }.toSet()
        // Downloaded or downloading ones stay (their file is on disk and may be shared).
        dao.catalogueImages()
            .filter { it.id !in ids && it.status !in setOf("ready", "downloading") && dao.vmCountForImage(it.id) == 0 }
            .forEach { dao.deleteImage(it.id) }
        for (img in parsed.images) {
            val old = dao.image(img.id)
            when {
                old == null -> dao.upsertImage(img)
                // Same file: refresh metadata, keep download state.
                old.sha256 == img.sha256 -> dao.updateImage(img.copy(localPath = old.localPath, status = old.status, createdAt = old.createdAt))
                // New version of the file: download again (existing VMs keep their extracted system).
                // Stale partial files belong to the old version.
                else -> { partFiles(img.id).forEach { it.delete() }; dao.updateImage(img.copy(createdAt = old.createdAt)) }
            }
        }
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

    internal fun partFile(id: String, n: Int = 0) = File(dir, "$id.part$n")
    private fun partFiles(id: String) = dir.listFiles { f -> f.name.startsWith("$id.part") }?.toList() ?: emptyList()
    /** One file per version (sha in the name): a new version never overwrites a file a twin image points at. */
    internal fun finalFile(id: String, sha: String? = null) = File(dir, if (sha == null) "$id.img" else "$id-${sha.take(16)}.img")

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
        val repo = CuciVm.images
        val dao = CuciVm.db.dao()
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val img = dao.image(id) ?: return@withContext Result.failure()
        // Two catalogue entries can share one file (same rootfs, different desktop): reuse it.
        img.sha256?.let { dao.readyImageBySha(it) }?.let { twin ->
            dao.updateImage(img.copy(status = "ready", localPath = twin.localPath, updatedAt = System.currentTimeMillis()))
            return@withContext Result.success()
        }
        dao.updateImage(img.copy(status = "downloading", updatedAt = System.currentTimeMillis()))
        try {
            val parts = Catalogue.parts(img)
            val final = repo.finalFile(id, img.sha256)
            if (parts.isEmpty()) {
                val total = img.sizeBytes
                fetchAny(repo, Catalogue.urls(img), repo.partFile(id), total) { done -> if (total > 0) repo.setProgress(id, done.toFloat() / total) }
                    ?: return@withContext Result.failure()
                if (!repo.partFile(id).renameTo(final)) throw IOException("rename failed")
            } else {
                // Split file (> 2 GB on GitHub): fetch each part, check it, then join in order.
                val total = parts.sumOf { it.size }.coerceAtLeast(1)
                var before = 0L
                parts.forEachIndexed { n, p ->
                    val f = repo.partFile(id, n)
                    if (!(p.size > 0 && f.length() == p.size && (p.sha256 == null || sha256(f).equals(p.sha256, true)))) {
                        fetchAny(repo, listOf(p.url), f, p.size) { done -> repo.setProgress(id, (before + done).toFloat() / total) }
                            ?: return@withContext Result.failure()
                        if (p.sha256 != null && !sha256(f).equals(p.sha256, true)) { f.delete(); throw IOException("part $n checksum mismatch") }
                    }
                    before += f.length()
                }
                FileOutputStream(final).use { out -> parts.indices.forEach { n -> repo.partFile(id, n).inputStream().use { it.copyTo(out) } } }
                parts.indices.forEach { repo.partFile(id, it).delete() }
            }
            img.sha256?.let { want ->
                if (!sha256(final).equals(want, ignoreCase = true)) { final.delete(); throw IOException("checksum mismatch") }
            }
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

    /** Tries each URL in order, resuming into [into]. Returns null when stopped. */
    private fun fetchAny(repo: ImageRepo, urls: List<String>, into: File, expected: Long, progress: (Long) -> Unit): Unit? {
        var last: IOException? = null
        for (url in urls) {
            try {
                return fetch(repo, url, into, expected, progress)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("no download URL")
    }

    private fun fetch(repo: ImageRepo, url: String, into: File, expected: Long, progress: (Long) -> Unit): Unit? {
        val have = if (into.exists()) into.length() else 0L
        val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        repo.http.newCall(req).execute().use { resp ->
            // 416: nothing left to send. Only trust it when the size matches, else start over.
            if (resp.code == 416) {
                if (expected <= 0 || into.length() == expected) return Unit
                into.delete(); throw IOException("stale partial file")
            }
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val append = resp.code == 206
            var done = if (append) have else 0L
            FileOutputStream(into, append).use { out ->
                val buf = ByteArray(64 * 1024)
                val input = resp.body!!.byteStream()
                while (true) {
                    if (isStopped) return null
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    progress(done)
                }
            }
        }
        return Unit
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
