package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppInfo
import com.example.model.EditMode
import com.example.ui.theme.ApkAmber
import com.example.ui.theme.ApkBlue
import com.example.ui.theme.ApkCyan
import com.example.ui.theme.ApkEmeraldPrimary
import com.example.ui.theme.ApkPurple

@Composable
fun EditModeDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onSelectMode: (EditMode) -> Unit,
    onExtractApk: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (app.iconBitmap != null) {
                    Image(
                        bitmap = app.iconBitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = "${app.packageName} (v${app.versionName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditModeItem(
                    title = "Full Edit (RESOURCE RE-BUILD)",
                    subtitle = "Decompile & edit manifest, string resources, layouts, DEX smali & rebuild APK",
                    icon = Icons.Default.Construction,
                    tint = ApkEmeraldPrimary,
                    tag = "Full Mod",
                    testTag = "mode_full_edit"
                ) {
                    onSelectMode(EditMode.FULL_EDIT)
                }

                EditModeItem(
                    title = "Simple Edit (FILE REPLACEMENT)",
                    subtitle = "Directly replace images, audios, fonts, and assets inside the APK",
                    icon = Icons.Default.SwapHoriz,
                    tint = ApkCyan,
                    tag = "Fast & Easiest",
                    testTag = "mode_simple_edit"
                ) {
                    onSelectMode(EditMode.SIMPLE_EDIT)
                }

                EditModeItem(
                    title = "Common Edit",
                    subtitle = "Change app name, package name (Clone App), version code, and icon",
                    icon = Icons.Default.EditNote,
                    tint = ApkBlue,
                    tag = "Cloner",
                    testTag = "mode_common_edit"
                ) {
                    onSelectMode(EditMode.COMMON_EDIT)
                }

                EditModeItem(
                    title = "XML File Edit",
                    subtitle = "Inspect and modify AndroidManifest.xml and binary XMLs",
                    icon = Icons.Default.Code,
                    tint = ApkAmber,
                    tag = "Manifest",
                    testTag = "mode_xml_edit"
                ) {
                    onSelectMode(EditMode.XML_EDIT)
                }

                EditModeItem(
                    title = "Analyze APK & Certificates",
                    subtitle = "View permissions, DEX methods, signature certs (SHA256)",
                    icon = Icons.Default.Analytics,
                    tint = ApkPurple,
                    tag = "Info",
                    testTag = "mode_analyze"
                ) {
                    onSelectMode(EditMode.ANALYZE)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                EditModeItem(
                    title = "Extract APK to Storage",
                    subtitle = "Backup original APK (${app.formattedSize}) to device storage",
                    icon = Icons.Default.Download,
                    tint = MaterialTheme.colorScheme.primary,
                    tag = "Extract",
                    testTag = "mode_extract"
                ) {
                    onExtractApk()
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_close_button")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun EditModeItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    tag: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
