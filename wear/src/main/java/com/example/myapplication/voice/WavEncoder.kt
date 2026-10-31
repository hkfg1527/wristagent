package com.example.myapplication.voice

/** 16bit PCM 封装为标准 44 字节 RIFF/WAVE，供百度短语音识别解析 */
object WavEncoder {
    const val HEADER_SIZE = 44

    fun encode(pcm: ByteArray, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val out = ByteArray(HEADER_SIZE + pcm.size)

        writeAscii(out, 0, "RIFF")
        writeIntLe(out, 4, HEADER_SIZE - 8 + pcm.size)
        writeAscii(out, 8, "WAVE")
        writeAscii(out, 12, "fmt ")
        writeIntLe(out, 16, 16)
        writeShortLe(out, 20, 1)
        writeShortLe(out, 22, channels)
        writeIntLe(out, 24, sampleRate)
        writeIntLe(out, 28, byteRate)
        writeShortLe(out, 32, blockAlign)
        writeShortLe(out, 34, bitsPerSample)
        writeAscii(out, 36, "data")
        writeIntLe(out, 40, pcm.size)
        System.arraycopy(pcm, 0, out, HEADER_SIZE, pcm.size)
        return out
    }

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        for (i in value.indices) {
            target[offset + i] = value[i].code.toByte()
        }
    }

    private fun writeShortLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
