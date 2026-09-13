package com.example.model

import android.graphics.Bitmap

enum class EntryCategory {
    IMAGE,
    AUDIO,
    FONT,
    ASSET,
    XML,
    DEX,
    SMALI,
    OTHER
}

data class ApkEntry(
    val path: String,
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean = false,
    val category: EntryCategory = EntryCategory.OTHER,
    val isModified: Boolean = false,
    val replacementFilePath: String? = null,
    val replacementBytes: ByteArray? = null,
    val previewBitmap: Bitmap? = null,
    val textContent: String? = null
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val formattedSize: String
        get() {
            return when {
                size >= 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
                size >= 1024 -> String.format("%.1f KB", size / 1024.0)
                else -> "$size B"
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApkEntry

        if (path != other.path) return false
        if (isModified != other.isModified) return false
        if (replacementFilePath != other.replacementFilePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + isModified.hashCode()
        result = 31 * result + (replacementFilePath?.hashCode() ?: 0)
        return result
    }
}
