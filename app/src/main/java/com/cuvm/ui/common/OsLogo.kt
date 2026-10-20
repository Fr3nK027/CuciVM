package com.cuvm.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.cuvm.CuciVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** OS logo from the catalogue URL, cached on disk; a plain icon until (or unless) it loads. Decorative. */
@Composable
fun OsLogo(url: String?, os: String, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, url) {
        if (url == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val key = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val f = File(ctx.cacheDir, "logos/$key.png").apply { parentFile?.mkdirs() }
            try {
                if (!f.exists()) {
                    CuciVm.images.http.newCall(Request.Builder().url(url).header("User-Agent", "CuciVM").build()).execute().use { r ->
                        if (!r.isSuccessful) return@withContext null
                        val tmp = File(f.path + ".tmp")
                        r.body!!.byteStream().use { i -> tmp.outputStream().use { i.copyTo(it) } }
                        tmp.renameTo(f)
                    }
                }
                BitmapFactory.decodeFile(f.path)
            } catch (_: Exception) { null }
        }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), contentDescription = null, modifier = modifier.size(size))
    else Icon(
        when (os) { "android" -> Icons.Outlined.Android; "windows_apps", "windows_full" -> Icons.Outlined.DesktopWindows; else -> Icons.Outlined.Terminal },
        contentDescription = null, modifier = modifier.size(size), tint = MaterialTheme.colorScheme.primary,
    )
}
