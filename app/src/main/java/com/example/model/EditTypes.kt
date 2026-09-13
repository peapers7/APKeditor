package com.example.model

import java.io.File

enum class EditMode(
    val title: String,
    val subtitle: String,
    val iconName: String,
    val tag: String
) {
    FULL_EDIT(
        title = "Full Edit (RESOURCE RE-BUILD)",
        subtitle = "Decompile & edit manifest, string resources, layouts, DEX smali & rebuild APK",
        iconName = "construction",
        tag = "Recommended for Advanced Mods"
    ),
    SIMPLE_EDIT(
        title = "Simple Edit (FILE REPLACEMENT)",
        subtitle = "Directly replace images, audios, fonts, and assets inside the APK",
        iconName = "swap_horiz",
        tag = "Fast & Easiest"
    ),
    COMMON_EDIT(
        title = "Common Edit",
        subtitle = "Change app name, package name (Clone App), version code, target SDK, and app icon",
        iconName = "edit_note",
        tag = "App Cloner & Renamer"
    ),
    XML_EDIT(
        title = "XML File Edit",
        subtitle = "Inspect and modify AndroidManifest.xml and binary XML configurations",
        iconName = "code",
        tag = "Permissions & Toggles"
    ),
    ANALYZE(
        title = "Analyze APK & Signatures",
        subtitle = "View certificates, MD5/SHA256, DEX method counts, and full permissions map",
        iconName = "analytics",
        tag = "Security & Telemetry"
    )
}

sealed interface BuildState {
    object Idle : BuildState
    data class InProgress(
        val step: Int,
        val totalSteps: Int = 4,
        val message: String,
        val percentage: Float
    ) : BuildState
    data class Success(
        val outputApk: File,
        val packageName: String,
        val appName: String,
        val fileSizeFormatted: String,
        val durationMillis: Long,
        val logs: List<String>
    ) : BuildState
    data class Error(val errorMessage: String, val details: String? = null) : BuildState
}

data class ExtractedApkItem(
    val file: File,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val sizeFormatted: String,
    val dateModified: Long,
    val isModifiedBuild: Boolean = false
)

data class ManifestToggles(
    val debuggable: Boolean = false,
    val allowBackup: Boolean = true,
    val usesCleartextTraffic: Boolean = true,
    val hardwareAccelerated: Boolean = true,
    val testOnly: Boolean = false,
    val largeHeap: Boolean = true
)
