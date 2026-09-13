package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ApkAmber
import com.example.ui.theme.ApkBlue
import com.example.ui.theme.ApkCyan
import com.example.ui.theme.ApkEmeraldPrimary
import com.example.ui.theme.ApkPurple
import com.example.ui.theme.ApkRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "APK Editor Classic Guide",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("help_screen_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                GuideCard(
                    title = "1. How to Clone an App (Dual Accounts)",
                    icon = Icons.Default.ContentCopy,
                    accentColor = ApkBlue,
                    steps = listOf(
                        "1. Tap 'Select Apk from App' and pick the target app.",
                        "2. Select 'Common Edit' from the popup dialog.",
                        "3. Change the Package Name (e.g. add '.clone' or '.mod' to the end).",
                        "4. Optionally change the App Name and App Icon.",
                        "5. Tap 'SAVE / BUILD' and install the generated cloned APK."
                    )
                )
            }

            item {
                GuideCard(
                    title = "2. How to Replace Images & Logos",
                    icon = Icons.Default.Image,
                    accentColor = ApkCyan,
                    steps = listOf(
                        "1. Select an APK file or installed app.",
                        "2. Choose 'Simple Edit (FILE REPLACEMENT)'.",
                        "3. Stay on the 'Images' tab and search for the logo/icon/background.",
                        "4. Tap the 'Replace' button next to the image and choose your replacement PNG/WebP.",
                        "5. Tap 'BUILD APK' at the bottom when finished."
                    )
                )
            }

            item {
                GuideCard(
                    title = "3. How to Replace Sounds & Audios",
                    icon = Icons.Default.Audiotrack,
                    accentColor = ApkRose,
                    steps = listOf(
                        "1. Select an APK and open 'Simple Edit'.",
                        "2. Switch to the 'Audios' tab.",
                        "3. Find the sound effect (OGG, MP3, WAV) you wish to modify.",
                        "4. Tap 'Replace' to substitute with your custom audio file.",
                        "5. Tap 'BUILD APK' to re-package."
                    )
                )
            }

            item {
                GuideCard(
                    title = "4. How to Edit Manifest & Toggles",
                    icon = Icons.Default.Code,
                    accentColor = ApkAmber,
                    steps = listOf(
                        "1. Choose 'XML File Edit' for your chosen app.",
                        "2. In 'Quick Tweaks', you can toggle Debuggable, Cleartext HTTP, and Large Heap.",
                        "3. Switch to 'Raw XML' to view and modify any permission or intent-filter.",
                        "4. Tap 'SAVE & BUILD' to compile and re-sign."
                    )
                )
            }

            item {
                GuideCard(
                    title = "5. Solving 'App Not Installed' Errors",
                    icon = Icons.Default.Key,
                    accentColor = ApkPurple,
                    steps = listOf(
                        "• Original App Conflict: If you didn't change the package name, uninstall the original app before installing the modified version.",
                        "• Test Key Signature: Built APKs are signed with a standard test key. Android will block updating over an existing Google Play signature unless the package name is changed or the previous app is uninstalled."
                    )
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GuideCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    steps: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}
