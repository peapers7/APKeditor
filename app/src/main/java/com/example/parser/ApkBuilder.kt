package com.example.parser

import android.content.Context
import com.example.model.BuildState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ApkBuilder {

    suspend fun buildApk(
        context: Context,
        sourceApkFile: File,
        replacements: Map<String, ByteArray>, // path -> replaced bytes
        targetPackageName: String?,
        targetAppName: String?,
        onProgress: (step: Int, totalSteps: Int, message: String, percentage: Float) -> Unit
    ): BuildState = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<String>()

        try {
            logs.add("Starting build from: ${sourceApkFile.name}")
            onProgress(1, 4, "Indexing APK structures and resources...", 0.15f)

            val outputDir = File(context.getExternalFilesDir(null), "ModifiedAPKs").apply { mkdirs() }
            val cleanName = (targetAppName ?: sourceApkFile.nameWithoutExtension)
                .replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val outputFile = File(outputDir, "${cleanName}_edited_${System.currentTimeMillis()}.apk")

            logs.add("Target output: ${outputFile.absolutePath}")
            onProgress(2, 4, "Applying modifications & file replacements (${replacements.size} files)...", 0.40f)

            val md = MessageDigest.getInstance("SHA-256")
            val entryDigests = mutableMapOf<String, ByteArray>()

            // Open source zip and write to output zip
            ZipFile(sourceApkFile).use { zipIn ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zipOut ->
                    zipOut.setLevel(6) // standard compression
                    val entries = zipIn.entries()
                    val totalEntries = zipIn.size()
                    var processedCount = 0

                    onProgress(3, 4, "Packaging ZIP container...", 0.65f)

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name

                        // Skip old signature files
                        if (entryName.startsWith("META-INF/") &&
                            (entryName.endsWith(".SF") || entryName.endsWith(".RSA") ||
                             entryName.endsWith(".DSA") || entryName.endsWith(".EC") ||
                             entryName == "META-INF/MANIFEST.MF")
                        ) {
                            continue
                        }

                        val newEntry = ZipEntry(entryName)
                        val entryBytes: ByteArray = if (replacements.containsKey(entryName)) {
                            logs.add("Replaced entry: $entryName (${replacements[entryName]?.size ?: 0} bytes)")
                            replacements[entryName] ?: byteArrayOf()
                        } else {
                            zipIn.getInputStream(entry).use { it.readBytes() }
                        }

                        // Write to zip
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(entryBytes)
                        zipOut.closeEntry()

                        // Calculate SHA-256 digest for manifest
                        if (!entryName.startsWith("META-INF/")) {
                            val digest = md.digest(entryBytes)
                            entryDigests[entryName] = digest
                        }

                        processedCount++
                    }

                    // Write new replaced files that didn't exist in original APK
                    for ((path, bytes) in replacements) {
                        if (zipIn.getEntry(path) == null && !path.startsWith("META-INF/")) {
                            val newEntry = ZipEntry(path)
                            zipOut.putNextEntry(newEntry)
                            zipOut.write(bytes)
                            zipOut.closeEntry()

                            val digest = md.digest(bytes)
                            entryDigests[path] = digest
                            logs.add("Added new entry: $path (${bytes.size} bytes)")
                        }
                    }

                    onProgress(4, 4, "Generating test signature & signing APK...", 0.90f)
                    logs.add("Signing APK with v1 test signature...")

                    // 4. Generate & write V1 Signature
                    val signResult = ApkSignerUtil.createV1Signature(entryDigests)

                    // Write MANIFEST.MF
                    zipOut.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                    zipOut.write(signResult.manifestBytes)
                    zipOut.closeEntry()

                    // Write CERT.SF
                    zipOut.putNextEntry(ZipEntry("META-INF/CERT.SF"))
                    zipOut.write(signResult.signatureFileBytes)
                    zipOut.closeEntry()

                    // Write CERT.RSA
                    zipOut.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                    zipOut.write(signResult.signatureBlockBytes)
                    zipOut.closeEntry()
                }
            }

            val duration = System.currentTimeMillis() - startTime
            val sizeMb = outputFile.length() / (1024.0 * 1024.0)
            val sizeFormatted = String.format("%.2f MB", sizeMb)

            logs.add("Build completed successfully in ${duration}ms (${sizeFormatted})")
            onProgress(4, 4, "Build completed successfully!", 1.0f)

            BuildState.Success(
                outputApk = outputFile,
                packageName = targetPackageName ?: "com.edited.apk",
                appName = targetAppName ?: sourceApkFile.nameWithoutExtension,
                fileSizeFormatted = sizeFormatted,
                durationMillis = duration,
                logs = logs
            )
        } catch (e: Exception) {
            e.printStackTrace()
            logs.add("Error during build: ${e.message}")
            BuildState.Error(
                errorMessage = e.message ?: "Unknown build error",
                details = logs.joinToString("\n")
            )
        }
    }
}
