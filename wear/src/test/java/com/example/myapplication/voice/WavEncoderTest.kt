package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class WavEncoderTest {

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, StandardCharsets.US_ASCII)

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    @Test
    fun encodesCanonicalRiffHeaderFor16kMono16bit() {
        val pcm = ByteArray(3200)
        val wav = WavEncoder.encode(pcm, 16000)

        assertEquals(44, WavEncoder.HEADER_SIZE)
        assertEquals(44 + 3200, wav.size)
        assertEquals("RIFF", ascii(wav, 0, 4))
        assertEquals(36 + 3200, le32(wav, 4))
        assertEquals("WAVE", ascii(wav, 8, 4))
        assertEquals("fmt ", ascii(wav, 12, 4))
        assertEquals(16, le32(wav, 16))
        assertEquals(1, le16(wav, 20))
        assertEquals(1, le16(wav, 22))
        assertEquals(16000, le32(wav, 24))
        assertEquals(32000, le32(wav, 28))
        assertEquals(2, le16(wav, 32))
        assertEquals(16, le16(wav, 34))
        assertEquals("data", ascii(wav, 36, 4))
        assertEquals(3200, le32(wav, 40))
    }

    @Test
    fun recomputesByteRateFor8kSampleRate() {
        val wav = WavEncoder.encode(ByteArray(1600), 8000)
        assertEquals(8000, le32(wav, 24))
        assertEquals(16000, le32(wav, 28))
        assertEquals(36 + 1600, le32(wav, 4))
        assertEquals(1600, le32(wav, 40))
    }

    @Test
    fun keepsHeaderValidForEmptyPayload() {
        val wav = WavEncoder.encode(ByteArray(0), 16000)
        assertEquals(44, wav.size)
        assertEquals(36, le32(wav, 4))
        assertEquals(0, le32(wav, 40))
    }

    @Test
    fun copiesPcmPayloadVerbatimAfterHeader() {
        val pcm = byteArrayOf(0x01, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val wav = WavEncoder.encode(pcm, 16000)
        assertEquals(48, wav.size)
        for (i in pcm.indices) {
            assertEquals(pcm[i], wav[44 + i])
        }
    }
}
