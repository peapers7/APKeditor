package com.example.parser

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import com.example.model.ApkEntry
import com.example.model.AppInfo
import com.example.model.EntryCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ApkExtractor {

    suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
            }
        } catch (e: Exception) {
            emptyList()
        }

        packages.mapNotNull { pkg ->
            try {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val sourceDir = appInfo.sourceDir ?: return@mapNotNull null
                val apkFile = File(sourceDir)
                if (!apkFile.exists()) return@mapNotNull null

                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.packageName
                }

                val iconDrawable = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }

                val iconBitmap = if (iconDrawable is BitmapDrawable) {
                    iconDrawable.bitmap
                } else null

                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 21
                val targetSdk = appInfo.targetSdkVersion

                @Suppress("DEPRECATION")
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    pkg.versionCode.toLong()
                }

                val perms = try {
                    val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg.packageName, PackageManager.GET_PERMISSIONS)
                    }
                    pInfo.requestedPermissions?.toList() ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                AppInfo(
                    packageName = pkg.packageName,
                    appName = appName,
                    versionName = pkg.versionName ?: "1.0",
                    versionCode = versionCode,
                    minSdkVersion = minSdk,
                    targetSdkVersion = targetSdk,
                    sourceDir = sourceDir,
                    apkSize = apkFile.length(),
                    isSystemApp = isSystem,
                    iconBitmap = iconBitmap,
                    iconDrawable = iconDrawable,
                    permissions = perms
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }

    suspend fun extractApkEntries(apkFile: File): List<ApkEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ApkEntry>()
        if (!apkFile.exists()) return@withContext entries

        try {
            ZipFile(apkFile).use { zip ->
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    val entry = enumEntries.nextElement()
                    val path = entry.name
                    val isDir = entry.isDirectory
                    val name = path.substringAfterLast('/')
                    val category = categorizeEntry(path, name)

                    entries.add(
                        ApkEntry(
                            path = path,
                            name = name.ifEmpty { path },
                            size = entry.size.coerceAtLeast(0),
                            compressedSize = entry.compressedSize.coerceAtLeast(0),
                            isDirectory = isDir,
                            category = category
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries.sortedWith(compareBy({ it.category.ordinal }, { it.path }))
    }

    fun categorizeEntry(path: String, name: String): EntryCategory {
        val lowerPath = path.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()

        return when {
            ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp") ||
                    (lowerPath.startsWith("res/drawable") && ext == "xml") ||
                    lowerPath.startsWith("res/mipmap") -> EntryCategory.IMAGE

            ext in listOf("mp3", "ogg", "wav", "aac", "m4a", "flac", "mid") ||
                    lowerPath.startsWith("res/raw") -> EntryCategory.AUDIO

            ext in listOf("ttf", "otf", "woff", "woff2") ||
                    lowerPath.startsWith("res/font") -> EntryCategory.FONT

            lowerPath == "androidmanifest.xml" || ext == "xml" -> EntryCategory.XML

            ext == "dex" -> EntryCategory.DEX

            lowerPath.startsWith("assets/") || ext in listOf("json", "txt", "html", "js", "css", "properties") -> EntryCategory.ASSET

            lowerPath.startsWith("smali/") -> EntryCategory.SMALI

            else -> EntryCategory.OTHER
        }
    }

    suspend fun readEntryBytes(apkFile: File, entryPath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(entryPath) ?: return@withContext null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun readEntryBitmap(apkFile: File, entryPath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = readEntryBytes(apkFile, entryPath) ?: return@withContext null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun readManifestXml(apkFile: File): String = withContext(Dispatchers.IO) {
        try {
            val bytes = readEntryBytes(apkFile, "AndroidManifest.xml")
            if (bytes != null) {
                AXmlDecoder.decode(bytes)
            } else {
                "<!-- AndroidManifest.xml not found in APK -->"
            }
        } catch (e: Exception) {
            "<!-- Error reading AndroidManifest.xml: ${e.message} -->"
        }
    }

    suspend fun getApkSignatures(context: Context, apkFile: File): List<String> = withContext(Dispatchers.IO) {
        val certs = mutableListOf<String>()
        try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pkgInfo?.signatures
            }

            signatures?.forEachIndexed { index, sig ->
                val certBytes = sig.toByteArray()
                val certFactory = CertificateFactory.getInstance("X.509")
                val cert = certFactory.generateCertificate(certBytes.inputStream()) as? X509Certificate
                if (cert != null) {
                    val md5 = getDigest(certBytes, "MD5")
                    val sha1 = getDigest(certBytes, "SHA-1")
                    val sha256 = getDigest(certBytes, "SHA-256")
                    certs.add(
                        """
                        Signature #${index + 1}:
                        Subject: ${cert.subjectDN.name}
                        Issuer: ${cert.issuerDN.name}
                        Serial: ${cert.serialNumber}
                        Algorithm: ${cert.sigAlgName}
                        Valid From: ${cert.notBefore}
                        Valid Until: ${cert.notAfter}
                        MD5: $md5
                        SHA-1: $sha1
                        SHA-256: $sha256
                        """.trimIndent()
                    )
                }
            }
        } catch (e: Exception) {
            certs.add("Signature info unavailable: ${e.message}")
        }
        certs
    }

    private fun getDigest(bytes: ByteArray, algorithm: String): String {
        return try {
            val md = MessageDigest.getInstance(algorithm)
            val digest = md.digest(bytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "N/A"
        }
    }
}
