package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.EditMode
import com.example.ui.components.BuildProgressDialog
import com.example.ui.components.EditModeDialog
import com.example.ui.screens.ApkAnalyzerScreen
import com.example.ui.screens.AppPickerScreen
import com.example.ui.screens.CommonEditScreen
import com.example.ui.screens.ExtractedApksScreen
import com.example.ui.screens.FullEditScreen
import com.example.ui.screens.HelpGuideScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SimpleEditScreen
import com.example.ui.screens.XmlEditScreen
import com.example.ui.theme.ApkEditorTheme
import com.example.viewmodel.ApkEditorViewModel
import com.example.viewmodel.ScreenRoute
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApkEditorTheme {
                ApkEditorApp(
                    onInstallApk = { apkFile -> installApk(apkFile) },
                    onShareApk = { apkFile -> shareApk(apkFile) }
                )
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareApk(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(Intent.createChooser(intent, "Share APK via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share APK: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ApkEditorApp(
    viewModel: ApkEditorViewModel = viewModel(),
    onInstallApk: (File) -> Unit,
    onShareApk: (File) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // APK File Picker Launcher
    val apkFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadApkFromUri(uri)
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = state.currentRoute,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { route ->
                when (route) {
                    ScreenRoute.HOME -> {
                        HomeScreen(
                            installedCount = state.installedApps.size,
                            extractedApks = state.extractedApks,
                            onSelectApkFile = {
                                apkFilePicker.launch("application/vnd.android.package-archive")
                            },
                            onSelectApkFromApp = {
                                viewModel.navigateTo(ScreenRoute.APP_PICKER)
                            },
                            onOpenExtractedList = {
                                viewModel.navigateTo(ScreenRoute.EXTRACTED_LIST)
                            },
                            onOpenHelpGuide = {
                                viewModel.navigateTo(ScreenRoute.HELP_GUIDE)
                            },
                            onInstallApk = onInstallApk,
                            onShareApk = onShareApk,
                            onDeleteApk = { file -> viewModel.deleteExtractedApk(file) }
                        )
                    }

                    ScreenRoute.APP_PICKER -> {
                        AppPickerScreen(
                            apps = state.filteredApps,
                            isLoading = state.isLoadingApps,
                            searchQuery = state.appSearchQuery,
                            currentFilter = state.appFilter,
                            currentSort = state.sortOption,
                            onSearchChanged = { viewModel.setAppSearchQuery(it) },
                            onFilterChanged = { viewModel.setAppFilter(it) },
                            onSortChanged = { viewModel.setSortOption(it) },
                            onAppSelected = { app -> viewModel.selectApp(app) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    ScreenRoute.SIMPLE_EDIT -> {
                        val app = state.selectedApp
                        if (app != null) {
                            SimpleEditScreen(
                                app = app,
                                entries = state.apkEntries,
                                isLoading = state.isLoadingEntries,
                                selectedTab = state.selectedCategoryTab,
                                replacementsCount = state.replacements.size,
                                onTabSelected = { viewModel.setSelectedCategoryTab(it) },
                                onReplaceEntry = { path, uri -> viewModel.replaceEntryWithUri(path, uri) },
                                onRemoveReplacement = { path -> viewModel.removeReplacement(path) },
                                onBuildApk = { viewModel.buildModifiedApk() },
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }

                    ScreenRoute.COMMON_EDIT -> {
                        val app = state.selectedApp
                        if (app != null) {
                            CommonEditScreen(
                                app = app,
                                form = state.commonEditForm,
                                onFormChange = { transform -> viewModel.updateCommonEditForm(transform) },
                                onSetIcon = { bitmap, bytes -> viewModel.setReplacementIcon(bitmap, bytes) },
                                onSaveBuild = { viewModel.buildModifiedApk() },
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }

                    ScreenRoute.FULL_EDIT -> {
                        val app = state.selectedApp
                        if (app != null) {
                            FullEditScreen(
                                app = app,
                                entries = state.apkEntries,
                                isLoading = state.isLoadingEntries,
                                selectedFilePath = state.currentEditingFilePath,
                                fileContent = state.currentEditingFileContent,
                                onSelectFile = { path -> viewModel.openFileForEditing(path) },
                                onCloseEditor = { viewModel.closeFileEditor() },
                                onSaveFile = { path, content -> viewModel.saveEditedFileContent(path, content) },
                                onBuildApk = { viewModel.buildModifiedApk() },
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }

                    ScreenRoute.XML_EDIT -> {
                        val app = state.selectedApp
                        if (app != null) {
                            XmlEditScreen(
                                app = app,
                                manifestXml = state.manifestXml,
                                toggles = state.manifestToggles,
                                onManifestChange = { xml -> viewModel.updateManifestXml(xml) },
                                onToggleChange = { transform -> viewModel.updateManifestToggles(transform) },
                                onSaveBuild = { viewModel.buildModifiedApk() },
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }

                    ScreenRoute.ANALYZE -> {
                        val app = state.selectedApp
                        if (app != null) {
                            ApkAnalyzerScreen(
                                app = app,
                                signatures = state.signatures,
                                entries = state.apkEntries,
                                onExtractApk = { viewModel.extractApk(app) },
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }

                    ScreenRoute.EXTRACTED_LIST -> {
                        ExtractedApksScreen(
                            items = state.extractedApks,
                            onInstall = onInstallApk,
                            onShare = onShareApk,
                            onDelete = { file -> viewModel.deleteExtractedApk(file) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    ScreenRoute.HELP_GUIDE -> {
                        HelpGuideScreen(
                            onBack = { viewModel.navigateBack() }
                        )
                    }
                }
            }

            // Edit Mode Dialog popup
            if (state.showEditModeDialog && state.selectedApp != null) {
                EditModeDialog(
                    app = state.selectedApp!!,
                    onDismiss = { viewModel.dismissEditModeDialog() },
                    onSelectMode = { mode -> viewModel.onSelectEditMode(mode) },
                    onExtractApk = { viewModel.extractApk(state.selectedApp!!) }
                )
            }

            // Build Progress & Success / Error Dialog
            BuildProgressDialog(
                buildState = state.buildState,
                onDismiss = { viewModel.dismissBuildState() },
                onInstall = onInstallApk,
                onShare = onShareApk
            )
        }
    }
}
