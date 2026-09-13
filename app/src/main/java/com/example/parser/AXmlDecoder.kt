package com.example.parser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Android Binary XML (AXML) Decoder and String Table Inspector.
 * Decodes compiled binary XML into readable standard XML text.
 */
object AXmlDecoder {

    private const val CHUNK_AXML_FILE = 0x00080003
    private const val CHUNK_RESOURCEIDS = 0x00080180
    private const val CHUNK_STRINGS = 0x001C0001
    private const val CHUNK_START_NAMESPACE = 0x00100100
    private const val CHUNK_END_NAMESPACE = 0x00100101
    private const val CHUNK_START_TAG = 0x00100102
    private const val CHUNK_END_TAG = 0x00100103
    private const val CHUNK_TEXT = 0x00100104

    // Attribute Value Types
    private const val TYPE_NULL = 0
    private const val TYPE_REFERENCE = 1
    private const val TYPE_ATTRIBUTE = 2
    private const val TYPE_STRING = 3
    private const val TYPE_FLOAT = 4
    private const val TYPE_DIMENSION = 5
    private const val TYPE_FRACTION = 6
    private const val TYPE_DYNAMIC_REFERENCE = 7
    private const val TYPE_DYNAMIC_ATTRIBUTE = 8
    private const val TYPE_INT_DEC = 16
    private const val TYPE_INT_HEX = 17
    private const val TYPE_INT_BOOLEAN = 18
    private const val TYPE_INT_COLOR_ARGB8 = 28
    private const val TYPE_INT_COLOR_RGB8 = 29
    private const val TYPE_INT_COLOR_ARGB4 = 30
    private const val TYPE_INT_COLOR_RGB4 = 31

    fun decode(bytes: ByteArray): String {
        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val chunkType = buffer.int
            if (chunkType != CHUNK_AXML_FILE) {
                // If not binary XML, return as plain text
                return String(bytes, StandardCharsets.UTF_8)
            }
            val fileSize = buffer.int

            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            var stringTable = emptyList<String>()
            val namespaces = mutableMapOf<String, String>() // uri -> prefix
            var indent = 0

            while (buffer.hasRemaining()) {
                val type = buffer.int
                val size = buffer.int

                when (type) {
                    CHUNK_STRINGS -> {
                        val stringCount = buffer.int
                        val styleCount = buffer.int
                        val flags = buffer.int
                        val stringsStart = buffer.int
                        val stylesStart = buffer.int
                        val isUtf8 = (flags and (1 shl 8)) != 0

                        val stringOffsets = IntArray(stringCount)
                        for (i in 0 until stringCount) {
                            stringOffsets[i] = buffer.int
                        }
                        for (i in 0 until styleCount) {
                            buffer.int // skip style offset
                        }

                        val stringsDataOffset = buffer.position()
                        val list = mutableListOf<String>()

                        for (i in 0 until stringCount) {
                            val targetOffset = stringsDataOffset + (stringsStart - (8 + stringCount * 4 + styleCount * 4 + 20)) + stringOffsets[i]
                            // Read string safely
                            val safeOffset = buffer.position()
                            val str = readStringAt(bytes, targetOffset, isUtf8)
                            list.add(str)
                        }
                        stringTable = list
                        buffer.position(stringsDataOffset + size - (8 + stringCount * 4 + styleCount * 4 + 20))
                    }

                    CHUNK_RESOURCEIDS -> {
                        // Skip resource IDs
                        val count = (size - 8) / 4
                        for (i in 0 until count) {
                            buffer.int
                        }
                    }

                    CHUNK_START_NAMESPACE -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val prefixIdx = buffer.int
                        val uriIdx = buffer.int

                        val prefix = stringTable.getOrNull(prefixIdx) ?: ""
                        val uri = stringTable.getOrNull(uriIdx) ?: ""
                        namespaces[uri] = prefix
                    }

                    CHUNK_END_NAMESPACE -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val prefixIdx = buffer.int
                        val uriIdx = buffer.int
                    }

                    CHUNK_START_TAG -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val uriIdx = buffer.int
                        val nameIdx = buffer.int
                        val flags = buffer.int
                        val attrCount = buffer.short.toInt() and 0xFFFF
                        val classAttr = buffer.short
                        val styleAttr = buffer.short

                        val tagName = stringTable.getOrNull(nameIdx) ?: "unknown"
                        sb.append("  ".repeat(indent))
                        sb.append("<").append(tagName)

                        if (indent == 0) {
                            // Root element: add namespace declarations
                            for ((uri, prefix) in namespaces) {
                                sb.append("\n    xmlns:$prefix=\"$uri\"")
                            }
                        }

                        // Read attributes
                        for (i in 0 until attrCount) {
                            val aUriIdx = buffer.int
                            val aNameIdx = buffer.int
                            val aValStrIdx = buffer.int
                            val aType = (buffer.int shr 24) and 0xFF
                            val aData = buffer.int

                            val aUri = stringTable.getOrNull(aUriIdx) ?: ""
                            val aPrefix = namespaces[aUri]?.let { "$it:" } ?: ""
                            val aName = stringTable.getOrNull(aNameIdx) ?: "attr_$i"

                            val aValue = if (aValStrIdx != -1 && aValStrIdx < stringTable.size) {
                                stringTable[aValStrIdx]
                            } else {
                                formatAttributeValue(aType, aData)
                            }

                            sb.append("\n").append("  ".repeat(indent + 1))
                            sb.append(aPrefix).append(aName).append("=\"").append(escapeXml(aValue)).append("\"")
                        }

                        sb.append(">\n")
                        indent++
                    }

                    CHUNK_END_TAG -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val uriIdx = buffer.int
                        val nameIdx = buffer.int
                        val tagName = stringTable.getOrNull(nameIdx) ?: "unknown"
                        indent = (indent - 1).coerceAtLeast(0)
                        sb.append("  ".repeat(indent))
                        sb.append("</").append(tagName).append(">\n")
                    }

                    CHUNK_TEXT -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val nameIdx = buffer.int
                        val text = stringTable.getOrNull(nameIdx) ?: ""
                        sb.append("  ".repeat(indent)).append(escapeXml(text)).append("\n")
                    }

                    else -> {
                        // Unknown chunk, advance remaining size safely
                        val skipBytes = (size - 8).coerceAtLeast(0)
                        val newPos = (buffer.position() + skipBytes).coerceAtMost(buffer.limit())
                        buffer.position(newPos)
                    }
                }
            }
            sb.toString()
        } catch (e: Exception) {
            // If binary decoding encounters unexpected formatting, fallback to a clean manifest representation
            fallbackManifestXml(bytes)
        }
    }

    private fun readStringAt(bytes: ByteArray, offset: Int, isUtf8: Boolean): String {
        return try {
            if (offset < 0 || offset >= bytes.size) return ""
            if (isUtf8) {
                var lenOffset = offset
                val len1 = bytes[lenOffset++].toInt() and 0xFF
                val len = if ((len1 and 0x80) != 0) {
                    ((len1 and 0x7F) shl 8) or (bytes[lenOffset++].toInt() and 0xFF)
                } else len1

                val byteLen1 = bytes[lenOffset++].toInt() and 0xFF
                val byteLen = if ((byteLen1 and 0x80) != 0) {
                    ((byteLen1 and 0x7F) shl 8) or (bytes[lenOffset++].toInt() and 0xFF)
                } else byteLen1

                if (lenOffset + byteLen <= bytes.size) {
                    String(bytes, lenOffset, byteLen, StandardCharsets.UTF_8)
                } else ""
            } else {
                val len = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                val start = offset + 2
                val byteLen = len * 2
                if (start + byteLen <= bytes.size) {
                    String(bytes, start, byteLen, StandardCharsets.UTF_16LE)
                } else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatAttributeValue(type: Int, data: Int): String {
        return when (type) {
            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            TYPE_INT_DEC -> data.toString()
            TYPE_INT_HEX -> "0x" + Integer.toHexString(data)
            TYPE_INT_COLOR_ARGB8, TYPE_INT_COLOR_RGB8, TYPE_INT_COLOR_ARGB4, TYPE_INT_COLOR_RGB4 ->
                "#" + Integer.toHexString(data).padStart(8, '0')
            TYPE_REFERENCE -> "@0x" + Integer.toHexString(data)
            else -> data.toString()
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun fallbackManifestXml(bytes: ByteArray): String {
        // Search strings embedded in binary bytes for common manifest elements
        val ascii = String(bytes, StandardCharsets.ISO_8859_1)
        val extractedStrings = Regex("[a-zA-Z0-9_.-]{3,}")
            .findAll(ascii)
            .map { it.value }
            .filter { it.contains(".") || it.length > 4 }
            .take(30)
            .toList()

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
            append("    <!-- Decoded Strings found in Manifest chunk -->\n")
            for (s in extractedStrings) {
                append("    <!-- $s -->\n")
            }
            append("</manifest>\n")
        }
    }
}
