package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ApkEntry
import com.example.model.AppInfo
import com.example.model.BuildState
import com.example.model.EditMode
import com.example.model.EntryCategory
import com.example.model.ExtractedApkItem
import com.example.model.ManifestToggles
import com.example.parser.AXmlDecoder
import com.example.parser.ApkBuilder
import com.example.parser.ApkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ScreenRoute {
    HOME,
    APP_PICKER,
    SIMPLE_EDIT,
    COMMON_EDIT,
    FULL_EDIT,
    XML_EDIT,
    ANALYZE,
    EXTRACTED_LIST,
    HELP_GUIDE
}

enum class AppFilter {
    ALL,
    USER,
    SYSTEM
}

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC
}

data class CommonEditForm(
    val appName: String = "",
    val packageName: String = "",
    val versionCode: String = "1",
    val versionName: String = "1.0",
    val minSdkVersion: String = "21",
    val targetSdkVersion: String = "34",
    val installLocation: Int = 0, // 0: Auto, 1: Internal, 2: External
    val replacementIconBitmap: Bitmap? = null,
    val replacementIconBytes: ByteArray? = null
)

data class ApkEditorUiState(
    val currentRoute: ScreenRoute = ScreenRoute.HOME,
    val navBackStack: List<ScreenRoute> = listOf(ScreenRoute.HOME),
    val installedApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val appSearchQuery: String = "",
    val appFilter: AppFilter = AppFilter.USER,
    val sortOption: SortOption = SortOption.NAME_ASC,
    val selectedApp: AppInfo? = null,
    val showEditModeDialog: Boolean = false,
    val apkEntries: List<ApkEntry> = emptyList(),
    val isLoadingEntries: Boolean = false,
    val selectedCategoryTab: EntryCategory = EntryCategory.IMAGE,
    val replacements: Map<String, ByteArray> = emptyMap(),
    val replacementBitmaps: Map<String, Bitmap> = emptyMap(),
    val commonEditForm: CommonEditForm = CommonEditForm(),
    val manifestXml: String = "",
    val manifestToggles: ManifestToggles = ManifestToggles(),
    val signatures: List<String> = emptyList(),
    val currentEditingFilePath: String? = null,
    val currentEditingFileContent: String = "",
    val buildState: BuildState = BuildState.Idle,
    val extractedApks: List<ExtractedApkItem> = emptyList(),
    val isExtractingApk: Boolean = false,
    val toastMessage: String? = null
)

class ApkEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ApkEditorUiState())
    val uiState: StateFlow<ApkEditorUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
        loadExtractedApks()
    }

    fun navigateTo(route: ScreenRoute) {
        _uiState.update { state ->
            state.copy(
                currentRoute = route,
                navBackStack = state.navBackStack + route
            )
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            if (state.navBackStack.size > 1) {
                val newStack = state.navBackStack.dropLast(1)
                state.copy(
                    currentRoute = newStack.last(),
                    navBackStack = newStack
                )
            } else {
                state.copy(currentRoute = ScreenRoute.HOME)
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = ApkExtractor.getInstalledApps(getApplication())
            _uiState.update { state ->
                val filtered = applyFilters(apps, state.appSearchQuery, state.appFilter, state.sortOption)
                state.copy(
                    installedApps = apps,
                    filteredApps = filtered,
                    isLoadingApps = false
                )
            }
        }
    }

    fun setAppSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.installedApps, query, state.appFilter, state.sortOption)
            state.copy(appSearchQuery = query, filteredApps = filtered)
        }
    }

    fun setAppFilter(filter: AppFilter) {
        _uiState.update { state ->
            val filtered = applyFilters(state.installedApps, state.appSearchQuery, filter, state.sortOption)
            state.copy(appFilter = filter, filteredApps = filtered)
        }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.update { state ->
            val filtered = applyFilters(state.installedApps, state.appSearchQuery, state.appFilter, sort)
            state.copy(sortOption = sort, filteredApps = filtered)
        }
    }

    private fun applyFilters(
        apps: List<AppInfo>,
        query: String,
        filter: AppFilter,
        sort: SortOption
    ): List<AppInfo> {
        val q = query.trim().lowercase()
        return apps
            .filter { app ->
                val matchesFilter = when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !app.isSystemApp
                    AppFilter.SYSTEM -> app.isSystemApp
                }
                val matchesQuery = q.isEmpty() ||
                        app.appName.lowercase().contains(q) ||
                        app.packageName.lowercase().contains(q)
                matchesFilter && matchesQuery
            }
            .sortedWith { a, b ->
                when (sort) {
                    SortOption.NAME_ASC -> a.appName.compareTo(b.appName, ignoreCase = true)
                    SortOption.NAME_DESC -> b.appName.compareTo(a.appName, ignoreCase = true)
                    SortOption.SIZE_DESC -> b.apkSize.compareTo(a.apkSize)
                    SortOption.SIZE_ASC -> a.apkSize.compareTo(b.apkSize)
                }
            }
    }

    fun selectApp(app: AppInfo) {
        _uiState.update {
            it.copy(
                selectedApp = app,
                showEditModeDialog = true,
                replacements = emptyMap(),
                replacementBitmaps = emptyMap(),
                commonEditForm = CommonEditForm(
                    appName = app.appName,
                    packageName = app.packageName,
                    versionCode = app.versionCode.toString(),
                    versionName = app.versionName,
                    minSdkVersion = app.minSdkVersion.toString(),
                    targetSdkVersion = app.targetSdkVersion.toString(),
                    installLocation = app.installLocation,
                    replacementIconBitmap = app.iconBitmap
                )
            )
        }
        loadApkDetails(File(app.sourceDir))
    }

    fun dismissEditModeDialog() {
        _uiState.update { it.copy(showEditModeDialog = false) }
    }

    fun onSelectEditMode(mode: EditMode) {
        _uiState.update { it.copy(showEditModeDialog = false) }
        when (mode) {
            EditMode.SIMPLE_EDIT -> navigateTo(ScreenRoute.SIMPLE_EDIT)
            EditMode.COMMON_EDIT -> navigateTo(ScreenRoute.COMMON_EDIT)
            EditMode.FULL_EDIT -> navigateTo(ScreenRoute.FULL_EDIT)
            EditMode.XML_EDIT -> navigateTo(ScreenRoute.XML_EDIT)
            EditMode.ANALYZE -> navigateTo(ScreenRoute.ANALYZE)
        }
    }

    fun loadApkFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val tempFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val pm = context.packageManager
                val pkgInfo = pm.getPackageArchiveInfo(tempFile.absolutePath, 0)
                val appName = pkgInfo?.packageName ?: tempFile.nameWithoutExtension
                val app = AppInfo(
                    packageName = pkgInfo?.packageName ?: "com.custom.apk",
                    appName = appName,
                    versionName = pkgInfo?.versionName ?: "1.0",
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo?.longVersionCode ?: 1 else 1,
                    sourceDir = tempFile.absolutePath,
                    apkSize = tempFile.length(),
                    isSelectedFile = true
                )

                withContext(Dispatchers.Main) {
                    selectApp(app)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(toastMessage = "Failed to load APK: ${e.message}") }
                }
            }
        }
    }

    private fun loadApkDetails(apkFile: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEntries = true) }
            val entries = ApkExtractor.extractApkEntries(apkFile)
            val manifest = ApkExtractor.readManifestXml(apkFile)
            val signatures = ApkExtractor.getApkSignatures(getApplication(), apkFile)

            _uiState.update {
                it.copy(
                    apkEntries = entries,
                    manifestXml = manifest,
                    signatures = signatures,
                    isLoadingEntries = false
                )
            }
        }
    }

    fun setSelectedCategoryTab(category: EntryCategory) {
        _uiState.update { it.copy(selectedCategoryTab = category) }
    }

    fun replaceEntryWithUri(entryPath: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val updatedReplacements = state.replacements + (entryPath to bytes)
                        val updatedBitmaps = if (bitmap != null) {
                            state.replacementBitmaps + (entryPath to bitmap)
                        } else state.replacementBitmaps

                        val updatedEntries = state.apkEntries.map { entry ->
                            if (entry.path == entryPath) {
                                entry.copy(
                                    isModified = true,
                                    replacementBytes = bytes,
                                    previewBitmap = bitmap ?: entry.previewBitmap
                                )
                            } else entry
                        }

                        state.copy(
                            replacements = updatedReplacements,
                            replacementBitmaps = updatedBitmaps,
                            apkEntries = updatedEntries,
                            toastMessage = "Replaced: ${entryPath.substringAfterLast('/')}"
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(toastMessage = "Replacement failed: ${e.message}") }
                }
            }
        }
    }

    fun removeReplacement(entryPath: String) {
        _uiState.update { state ->
            val updatedReplacements = state.replacements - entryPath
            val updatedBitmaps = state.replacementBitmaps - entryPath
            val updatedEntries = state.apkEntries.map { entry ->
                if (entry.path == entryPath) {
                    entry.copy(isModified = false, replacementBytes = null)
                } else entry
            }
            state.copy(
                replacements = updatedReplacements,
                replacementBitmaps = updatedBitmaps,
                apkEntries = updatedEntries
            )
        }
    }

    fun updateCommonEditForm(transform: CommonEditForm.() -> CommonEditForm) {
        _uiState.update { state ->
            state.copy(commonEditForm = state.commonEditForm.transform())
        }
    }

    fun setReplacementIcon(bitmap: Bitmap, bytes: ByteArray) {
        _uiState.update { state ->
            state.copy(
                commonEditForm = state.commonEditForm.copy(
                    replacementIconBitmap = bitmap,
                    replacementIconBytes = bytes
                )
            )
        }
    }

    fun openFileForEditing(path: String) {
        val app = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = ApkExtractor.readEntryBytes(File(app.sourceDir), path)
            val text = if (bytes != null) {
                if (path.endsWith(".xml", ignoreCase = true)) {
                    AXmlDecoder.decode(bytes)
                } else {
                    try {
                        String(bytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        "// Binary content (${bytes.size} bytes)"
                    }
                }
            } else ""

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        currentEditingFilePath = path,
                        currentEditingFileContent = text
                    )
                }
            }
        }
    }

    fun closeFileEditor() {
        _uiState.update {
            it.copy(
                currentEditingFilePath = null,
                currentEditingFileContent = ""
            )
        }
    }

    fun saveEditedFileContent(path: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        _uiState.update { state ->
            val updatedReplacements = state.replacements + (path to bytes)
            val updatedEntries = state.apkEntries.map { entry ->
                if (entry.path == path) {
                    entry.copy(isModified = true, replacementBytes = bytes)
                } else entry
            }
            state.copy(
                replacements = updatedReplacements,
                apkEntries = updatedEntries,
                currentEditingFilePath = null,
                toastMessage = "Saved file: ${path.substringAfterLast('/')}"
            )
        }
    }

    fun updateManifestXml(xml: String) {
        _uiState.update { it.copy(manifestXml = xml) }
    }

    fun updateManifestToggles(transform: ManifestToggles.() -> ManifestToggles) {
        _uiState.update { state ->
            state.copy(manifestToggles = state.manifestToggles.transform())
        }
    }

    fun buildModifiedApk() {
        val app = _uiState.value.selectedApp ?: return
        val sourceApk = File(app.sourceDir)
        if (!sourceApk.exists()) {
            _uiState.update { it.copy(toastMessage = "Source APK file not found") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    buildState = BuildState.InProgress(
                        step = 1,
                        message = "Initializing build environment...",
                        percentage = 0.05f
                    )
                )
            }

            val replacements = _uiState.value.replacements.toMutableMap()

            // If common edit has replaced icon, also inject to launcher drawables
            _uiState.value.commonEditForm.replacementIconBytes?.let { iconBytes ->
                _uiState.value.apkEntries
                    .filter { it.name.contains("ic_launcher", ignoreCase = true) || it.name.contains("app_icon", ignoreCase = true) }
                    .forEach { entry ->
                        replacements[entry.path] = iconBytes
                    }
            }

            val targetPkg = _uiState.value.commonEditForm.packageName.ifEmpty { app.packageName }
            val targetName = _uiState.value.commonEditForm.appName.ifEmpty { app.appName }

            val result = ApkBuilder.buildApk(
                context = getApplication(),
                sourceApkFile = sourceApk,
                replacements = replacements,
                targetPackageName = targetPkg,
                targetAppName = targetName,
                onProgress = { step, totalSteps, msg, pct ->
                    _uiState.update {
                        it.copy(
                            buildState = BuildState.InProgress(
                                step = step,
                                totalSteps = totalSteps,
                                message = msg,
                                percentage = pct
                            )
                        )
                    }
                }
            )

            _uiState.update { it.copy(buildState = result) }
            loadExtractedApks()
        }
    }

    fun dismissBuildState() {
        _uiState.update { it.copy(buildState = BuildState.Idle) }
    }

    fun extractApk(app: AppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExtractingApk = true) }
            try {
                val context = getApplication<Application>()
                val source = File(app.sourceDir)
                val outDir = File(context.getExternalFilesDir(null), "ExtractedAPKs").apply { mkdirs() }
                val cleanName = app.appName.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                val targetFile = File(outDir, "${cleanName}_v${app.versionName}.apk")

                source.copyTo(targetFile, overwrite = true)
                loadExtractedApks()

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isExtractingApk = false,
                            toastMessage = "APK Extracted: ${targetFile.name}"
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isExtractingApk = false,
                            toastMessage = "Extraction failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun loadExtractedApks() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val dirs = listOf(
                File(context.getExternalFilesDir(null), "ModifiedAPKs"),
                File(context.getExternalFilesDir(null), "ExtractedAPKs")
            )

            val items = mutableListOf<ExtractedApkItem>()
            val pm = context.packageManager

            for (dir in dirs) {
                if (dir.exists()) {
                    dir.listFiles()?.filter { it.extension.equals("apk", ignoreCase = true) }?.forEach { apkFile ->
                        val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                        val appName = apkFile.nameWithoutExtension.substringBefore("_edited_").substringBefore("_v")
                        val sizeMb = apkFile.length() / (1024.0 * 1024.0)
                        val sizeFormatted = String.format("%.2f MB", sizeMb)
                        val isModified = dir.name == "ModifiedAPKs"

                        items.add(
                            ExtractedApkItem(
                                file = apkFile,
                                appName = appName,
                                packageName = pkgInfo?.packageName ?: "com.app.apk",
                                versionName = pkgInfo?.versionName ?: "1.0",
                                sizeFormatted = sizeFormatted,
                                dateModified = apkFile.lastModified(),
                                isModifiedBuild = isModified
                            )
                        )
                    }
                }
            }

            items.sortByDescending { it.dateModified }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(extractedApks = items) }
            }
        }
    }

    fun deleteExtractedApk(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            file.delete()
            loadExtractedApks()
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
