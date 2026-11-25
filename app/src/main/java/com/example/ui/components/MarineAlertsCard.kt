package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.notifications.MarineAlertManager
import com.example.data.preferences.SonarSettings

@Composable
fun MarineAlertsCard(
    settings: SonarSettings,
    onSetStormAlerts: (Boolean) -> Unit,
    onSetTideAlerts: (Boolean) -> Unit,
    onSendTestNotification: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var feedbackText by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            feedbackText = "Izin notifikasi aktif! Pengingat otomatis berjalan setiap 30 menit."
            pendingAction?.invoke()
        } else {
            feedbackText = "Izin notifikasi ditolak. Aktifkan izin notifikasi di Setelan HP agar pengingat bisa muncul."
        }
        pendingAction = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (MarineAlertManager.hasNotificationPermission(context)) {
            action()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val anyActive = settings.stormAlertsEnabled || settings.tideAlertsEnabled

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (anyActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (anyActive) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                        contentDescription = "Ikon notifikasi mancing",
                        tint = if (anyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOTIFIKASI JAM GACOR & BADAI",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (anyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = if (anyActive) "AKTIF" else "NONAKTIF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (anyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Toggle 1: Storm & Heavy Rain Warning
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Peringatan Badai & Hujan Lebat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Notifikasi otomatis bila terdeteksi badai petir, hujan lebat, atau angin ≥ 20 knot dalam 3 jam ke depan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.stormAlertsEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            runWithNotificationPermission {
                                onSetStormAlerts(true)
                                feedbackText = "Peringatan badai otomatis aktif."
                            }
                        } else {
                            onSetStormAlerts(false)
                            feedbackText = null
                        }
                    },
                    modifier = Modifier.testTag("storm_alerts_switch")
                )
            }

            // Toggle 2: Rising Tide / Jam Gacor Alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pengingat Jam Gacor (Pasang Naik)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Notifikasi saat arus pasang naik mulai aktif (+8 cm/jam+) — waktu ideal ikan masuk ke pinggir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.tideAlertsEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            runWithNotificationPermission {
                                onSetTideAlerts(true)
                                feedbackText = "Pengingat jam gacor pasang naik aktif."
                            }
                        } else {
                            onSetTideAlerts(false)
                            feedbackText = null
                        }
                    },
                    modifier = Modifier.testTag("tide_alerts_switch")
                )
            }

            OutlinedButton(
                onClick = {
                    runWithNotificationPermission {
                        val sent = onSendTestNotification()
                        feedbackText = if (sent) {
                            "Notifikasi uji dikirim! Cek bilah notifikasi di atas layar HP Anda."
                        } else {
                            "Izin notifikasi belum aktif di HP Anda."
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_notification_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TES NOTIFIKASI SEKARANG",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (feedbackText != null) {
                Text(
                    text = feedbackText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
