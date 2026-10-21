package com.cuvm.ui.display

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuvm.R

/** VMs unlocked since the app came to the screen (cleared in MainActivity.onStop). */
object VmLock {
    val unlocked = androidx.compose.runtime.mutableStateListOf<String>()

    /** A lock needs the phone's own screen lock (fingerprint, face, PIN, pattern or password). */
    fun available(ctx: Context) = ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun ask(ctx: Context, title: String, onOk: () -> Unit) {
        val b = BiometricPrompt.Builder(ctx).setTitle(title)
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        }
        b.build().authenticate(CancellationSignal(), ctx.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
    }
}

/** Shown instead of a locked VM until the phone's lock is passed. */
@Composable
fun LockGate(id: String, name: String, onToPhone: () -> Unit) {
    val ctx = LocalContext.current
    val title = stringResource(R.string.unlock_vm, name)
    val ask = { VmLock.ask(ctx, title) { VmLock.unlocked.add(id) } }
    LaunchedEffect(id) { ask() }
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(name, color = Color.White)
        FilledTonalButton(onClick = ask) { Text(stringResource(R.string.unlock)) }
        TextButton(onClick = onToPhone) { Text(stringResource(R.string.to_phone)) }
    }
}
