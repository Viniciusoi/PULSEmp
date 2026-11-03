package com.pulse.player.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pulse.player.PulseApp

data class Perms(val storage: Boolean, val notifications: Boolean, val mic: Boolean, val battery: Boolean)

private val storagePermission: String
    get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun granted(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun readPerms(ctx: Context): Perms {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return Perms(
        storage = granted(ctx, storagePermission),
        notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
        mic = granted(ctx, Manifest.permission.RECORD_AUDIO),
        battery = pm.isIgnoringBatteryOptimizations(ctx.packageName),
    )
}

fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
    }
}

private fun openNotificationSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        openAppSettings(ctx)
    }
}

/** Abre direto o diálogo do sistema "Permitir que o app fique ativo em segundo plano". */
fun requestIgnoreBattery(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            openAppSettings(ctx)
        }
    }
}

class PermissionController(
    val perms: State<Perms>,
    val requestStorage: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestMic: () -> Unit,
    val requestBattery: () -> Unit,
    val requestAll: () -> Unit,
)

@Composable
fun rememberPermissionController(): PermissionController {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as PulseApp
    val state = remember { mutableStateOf(readPerms(ctx)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                state.value = readPerms(ctx)
                app.analyzer.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val deniedOnce = remember { mutableSetOf<String>() }
    var lastAsked by remember { mutableStateOf<String?>(null) }

    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) lastAsked?.let { deniedOnce.add(it) }
        state.value = readPerms(ctx)
        app.analyzer.refresh()
    }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.value = readPerms(ctx)
        app.analyzer.refresh()
        if (!state.value.battery) requestIgnoreBattery(ctx)
    }

    fun ask(p: String) {
        if (p in deniedOnce) openAppSettings(ctx) else {
            lastAsked = p
            single.launch(p)
        }
    }

    return PermissionController(
        perms = state,
        requestStorage = { ask(storagePermission) },
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && Manifest.permission.POST_NOTIFICATIONS !in deniedOnce) {
                lastAsked = Manifest.permission.POST_NOTIFICATIONS
                single.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else openNotificationSettings(ctx)
        },
        requestMic = { ask(Manifest.permission.RECORD_AUDIO) },
        requestBattery = { requestIgnoreBattery(ctx) },
        requestAll = {
            val p = state.value
            val list = mutableListOf<String>()
            if (!p.storage) list += storagePermission
            if (Build.VERSION.SDK_INT >= 33 && !p.notifications) list += Manifest.permission.POST_NOTIFICATIONS
            if (!p.mic) list += Manifest.permission.RECORD_AUDIO
            if (list.isNotEmpty()) multi.launch(list.toTypedArray())
            else if (!p.battery) requestIgnoreBattery(ctx)
        },
    )
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, "Ativo", tint = MaterialTheme.colorScheme.secondary)
            } else {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun PermissionCards(pc: PermissionController) {
    val p by pc.perms
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionCard(
            Icons.Rounded.Storage, "Armazenamento",
            "Necessário para encontrar as músicas do celular.",
            p.storage, "Permitir", pc.requestStorage,
        )
        PermissionCard(
            Icons.Rounded.Notifications, "Notificações",
            "Mostra o nome da música e os botões de voltar, play/pausa e pular.",
            p.notifications, "Permitir", pc.requestNotifications,
        )
        PermissionCard(
            Icons.Rounded.Mic, "Microfone",
            "Só para as ondas reagirem à batida. O app não grava nada: lê o áudio que ele mesmo toca.",
            p.mic, "Permitir", pc.requestMic,
        )
        PermissionCard(
            Icons.Rounded.BatteryChargingFull, "Ficar ativo em segundo plano",
            "Evita que o Android pare a música com a tela apagada.",
            p.battery, "Ativar", pc.requestBattery,
        )
    }
}

@Composable
fun PermissionsScreen(pc: PermissionController, onContinue: () -> Unit) {
    val p by pc.perms
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(8.dp))
        Text("Vamos configurar o Pulse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Precisamos de algumas permissões para tocar suas músicas com notificação, ondas na batida e sem o Android parar a reprodução.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        PermissionCards(pc)
        Spacer(Modifier.height(20.dp))
        Button(onClick = pc.requestAll, modifier = Modifier.fillMaxWidth()) { Text("Permitir tudo") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = p.storage,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (p.storage) "Continuar" else "Permita o armazenamento para continuar") }
        if (p.storage && (!p.notifications || !p.mic || !p.battery)) {
            TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Pular o resto por enquanto") }
        }
    }
}
