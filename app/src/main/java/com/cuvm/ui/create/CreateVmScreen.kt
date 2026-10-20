package com.cuvm.ui.create

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuvm.R
import com.cuvm.CuciVm
import com.cuvm.data.GuestImage
import com.cuvm.ui.common.DetailScaffold
import com.cuvm.ui.common.OsLogo
import com.cuvm.ui.common.TextDialog
import kotlinx.coroutines.launch

data class Resolution(val w: Int, val h: Int, val dpi: Int)

@Composable
fun resolutionPresets(): List<Pair<Resolution, String?>> {
    val m = LocalContext.current.resources.displayMetrics
    val portrait = Resolution(minOf(m.widthPixels, m.heightPixels), maxOf(m.widthPixels, m.heightPixels), m.densityDpi)
    return listOf(
        Resolution(480, 800, 240) to null, Resolution(540, 960, 240) to null,
        Resolution(720, 1280, 320) to null, Resolution(1080, 1920, 480) to null,
        portrait to stringResource(R.string.res_device_portrait),
        Resolution(portrait.h, portrait.w, portrait.dpi) to stringResource(R.string.res_device_landscape),
        // Laptop-style desktops: landscape, desktop density.
        Resolution(1280, 720, 120) to stringResource(R.string.res_desktop, "720p"),
        Resolution(1600, 900, 140) to stringResource(R.string.res_desktop, "900p"),
        Resolution(1920, 1080, 160) to stringResource(R.string.res_desktop, "1080p"),
    )
}

@Composable
fun ResolutionPicker(selected: Resolution?, onPick: (Resolution) -> Unit, modifier: Modifier = Modifier) {
    val presets = resolutionPresets()
    var custom by remember { mutableStateOf(false) }
    val all = presets + listOfNotNull(selected?.takeIf { s -> presets.none { it.first == s } }?.let { it to stringResource(R.string.res_custom) })
    LazyColumn(modifier.selectableGroup()) {
        items(all) { (r, label) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(r == selected, role = Role.RadioButton) { onPick(r) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = r == selected, onClick = null)
                Spacer(Modifier.width(16.dp))
                Column {
                    if (label != null) Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text("${r.w} × ${r.h} · DPI ${r.dpi}", style = if (label == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { TextButton(onClick = { custom = true }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.res_add_custom)) } }
    }
    if (custom) TextDialog(
        stringResource(R.string.res_custom_title), "1080x2340x450",
        validate = { parseRes(it) != null }, onOk = { parseRes(it)?.let(onPick) }, onDismiss = { custom = false },
    )
}

/** "W x H x DPI", any non-digit separators. */
fun parseRes(s: String): Resolution? {
    val n = Regex("\\d+").findAll(s).map { it.value.toIntOrNull() }.toList()
    if (n.size != 3 || n.any { it == null }) return null
    val (w, h, d) = n.map { it!! }
    return if (w in 240..4096 && h in 240..4096 && d in 72..800) Resolution(w, h, d) else null
}

@Composable
fun CreateVmScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val images by CuciVm.images.images.collectAsStateWithLifecycle(emptyList())
    val progress by CuciVm.images.progress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    var imageId by rememberSaveable { mutableStateOf<String?>(null) }
    var res by remember { mutableStateOf<Resolution?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val image = images.firstOrNull { it.id == imageId }
    val back: () -> Unit = { if (step > 0) step-- else onBack() }
    BackHandler(onBack = back)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val label = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Custom"
            imageId = CuciVm.images.importCustom(uri, label.substringBefore(".tar")).id
        }
    }

    val titles = listOf(R.string.create_step_system, R.string.create_step_resolution, R.string.create_step_name)
    DetailScaffold(stringResource(titles[step]), back) { mod ->
        Column(mod.fillMaxSize()) {
            when (step) {
                0 -> LazyColumn(Modifier.weight(1f).selectableGroup(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(images, key = { it.id }) { img -> ImageItem(img, progress[img.id], img.id == imageId) { imageId = img.id } }
                    item {
                        OutlinedButton(onClick = { pick.launch(arrayOf("application/gzip", "application/x-gzip", "application/x-tar", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.add_custom_image))
                        }
                        Text(stringResource(R.string.add_custom_image_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                1 -> ResolutionPicker(res, { res = it }, Modifier.weight(1f))
                else -> Column(Modifier.weight(1f).padding(16.dp)) {
                    LaunchedEffect(image) { if (name.isEmpty() && image != null) name = CuciVm.vms.defaultName(image) }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(60) }, label = { Text(stringResource(R.string.vm_name)) },
                        singleLine = true, isError = name.isBlank(), modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(stringResource(R.string.vm_name_hint)) },
                    )
                }
            }
            val canNext = when (step) { 0 -> image?.status == "ready"; 1 -> res != null; else -> name.isNotBlank() }
            Button(
                enabled = canNext, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
                onClick = {
                    if (step < 2) step++
                    else scope.launch {
                        val r = res!!
                        CuciVm.vms.create(image!!, r.w, r.h, r.dpi, name)
                        onDone()
                    }
                },
            ) { Text(stringResource(if (step < 2) R.string.next else R.string.done)) }
        }
    }
}

@Composable
private fun ImageItem(img: GuestImage, progress: Float?, selected: Boolean, onSelect: () -> Unit) {
    val ready = img.status == "ready"
    OutlinedCard(
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = ready, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OsLogo(img.logoUrl, img.os, 40.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(img.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.image_arch, img.arch, img.engine), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.image_version, img.versionLabel, "%.1f MB".format(img.sizeBytes / 1e6)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            Spacer(Modifier.width(8.dp))
            when {
                ready -> Text(stringResource(R.string.downloaded), color = MaterialTheme.colorScheme.primary)
                progress != null || img.status == "downloading" -> TextButton(onClick = { CuciVm.images.cancel(img.id) }) { Text(stringResource(R.string.cancel)) }
                else -> TextButton(onClick = { CuciVm.images.download(img.id) }) {
                    Text(stringResource(if (img.status == "failed") R.string.retry else R.string.download))
                }
            }
        }
    }
}
