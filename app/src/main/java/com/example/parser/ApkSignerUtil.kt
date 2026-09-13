package com.example.parser

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.util.Base64

/**
 * Generates V1 APK signatures (JAR Signing) for reconstructed APK packages.
 */
object ApkSignerUtil {

    private val base64Encoder: (ByteArray) -> String = { bytes ->
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    data class SignResult(
        val manifestBytes: ByteArray,
        val signatureFileBytes: ByteArray,
        val signatureBlockBytes: ByteArray
    )

    fun createV1Signature(
        entryDigests: Map<String, ByteArray>
    ): SignResult {
        // 1. Build MANIFEST.MF
        val manifest = Manifest()
        val mainAttrs = manifest.mainAttributes
        mainAttrs[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainAttrs[Attributes.Name("Created-By")] = "1.0 (APK Editor Pro Classic)"

        for ((entryName, digest) in entryDigests) {
            val attr = Attributes()
            attr[Attributes.Name("SHA-256-Digest")] = base64Encoder(digest)
            manifest.entries[entryName] = attr
        }

        val manifestBaos = ByteArrayOutputStream()
        manifest.write(manifestBaos)
        val manifestBytes = manifestBaos.toByteArray()

        // 2. Build CERT.SF
        val sfBaos = ByteArrayOutputStream()
        val sfWriter = sfBaos.bufferedWriter(Charsets.UTF_8)
        sfWriter.write("Signature-Version: 1.0\r\n")
        sfWriter.write("Created-By: 1.0 (APK Editor Pro)\r\n")
        val md = MessageDigest.getInstance("SHA-256")
        val manifestDigest = md.digest(manifestBytes)
        sfWriter.write("SHA-256-Digest-Manifest: ${base64Encoder(manifestDigest)}\r\n\r\n")

        for ((entryName, digest) in entryDigests) {
            sfWriter.write("Name: $entryName\r\n")
            sfWriter.write("SHA-256-Digest: ${base64Encoder(digest)}\r\n\r\n")
        }
        sfWriter.flush()
        val sfBytes = sfBaos.toByteArray()

        // 3. Build CERT.RSA (Self-signed test signature block)
        val rsaBlock = generateTestSignatureBlock(sfBytes)

        return SignResult(
            manifestBytes = manifestBytes,
            signatureFileBytes = sfBytes,
            signatureBlockBytes = rsaBlock
        )
    }

    private fun generateTestSignatureBlock(sfBytes: ByteArray): ByteArray {
        return try {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(1024, SecureRandom())
            val keyPair = keyGen.generateKeyPair()

            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(keyPair.private)
            signer.update(sfBytes)
            val signature = signer.sign()

            // Construct standard DER sequence envelope for PKCS#7 signature block
            val baos = ByteArrayOutputStream()
            baos.write(byteArrayOf(0x30, 0x82.toByte())) // SEQUENCE
            val body = ByteArrayOutputStream()

            // Object ID for PKCS#7 signedData
            val oidSignedData = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
            body.write(oidSignedData)

            // Content sequence
            val contentSeq = ByteArrayOutputStream()
            contentSeq.write(byteArrayOf(0x02, 0x01, 0x01)) // Version 1
            contentSeq.write(byteArrayOf(0x31, 0x00)) // DigestAlgorithms set

            // EncapsulatedContentInfo (data)
            contentSeq.write(byteArrayOf(0x30, 0x0B, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01))

            // Signature bytes
            val sigSeq = ByteArrayOutputStream()
            sigSeq.write(byteArrayOf(0x04)) // OCTET STRING
            writeDerLength(sigSeq, signature.size)
            sigSeq.write(signature)

            contentSeq.write(byteArrayOf(0x31)) // SignerInfos SET
            writeDerLength(contentSeq, sigSeq.size())
            contentSeq.write(sigSeq.toByteArray())

            val contentBytes = contentSeq.toByteArray()
            body.write(byteArrayOf(0xA0.toByte()))
            writeDerLength(body, contentBytes.size)
            body.write(contentBytes)

            val fullBody = body.toByteArray()
            val lenBytes = fullBody.size
            baos.write((lenBytes shr 8) and 0xFF)
            baos.write(lenBytes and 0xFF)
            baos.write(fullBody)

            baos.toByteArray()
        } catch (e: Exception) {
            // Fallback mock signature block
            ByteArray(128) { 0x00 }
        }
    }

    private fun writeDerLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            out.write(length)
        } else if (length < 256) {
            out.write(0x81)
            out.write(length)
        } else {
            out.write(0x82)
            out.write(length shr 8)
            out.write(length and 0xFF)
        }
    }
}
