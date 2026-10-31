package com.example.myapplication.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 录音抽象：JVM 单测注入假实现，真机使用 AudioRecordWavCapture */
fun interface AudioCapture {
    suspend fun recordWav(sampleRate: Int, maxDurationMs: Int): Result<ByteArray>
}

/** AudioRecord 采集 16kHz 单声道 PCM16 并封装为 WAV；阻塞 IO 全部在 IO 线程完成 */
class AudioRecordWavCapture(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AudioCapture {

    override suspend fun recordWav(sampleRate: Int, maxDurationMs: Int): Result<ByteArray> =
        withContext(ioDispatcher) {
            try {
                capture(sampleRate, maxDurationMs)
            } catch (e: SecurityException) {
                Result.failure(IllegalStateException(AsrMessages.NO_MIC_PERMISSION))
            } catch (e: IllegalStateException) {
                Result.failure(IllegalStateException(AsrMessages.RECORD_FAILED + "：" + describe(e)))
            } catch (e: RuntimeException) {
                Result.failure(IllegalStateException(AsrMessages.RECORD_FAILED + "：" + describe(e)))
            }
        }

    @Suppress("DEPRECATION")
    private fun capture(sampleRate: Int, maxDurationMs: Int): Result<ByteArray> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(IllegalStateException(AsrMessages.NO_MIC_PERMISSION))
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            return Result.failure(IllegalStateException(AsrMessages.UNSUPPORTED_SAMPLE_RATE))
        }

        val bufferSize = maxOf(minBuffer * 2, 4096)
        val targetBytes = sampleRate / 1000 * 2 * maxDurationMs
        val chunk = ByteArray(bufferSize)
        val sink = ByteArrayOutputStream(targetBytes)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return Result.failure(IllegalStateException(AsrMessages.RECORD_FAILED + "：麦克风未初始化"))
            }
            recorder.startRecording()
            while (sink.size() < targetBytes) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read < 0) {
                    return Result.failure(
                        IllegalStateException(AsrMessages.RECORD_FAILED + "（错误码 " + read + "）")
                    )
                }
                if (read == 0) break
                sink.write(chunk, 0, read)
            }
            recorder.stop()

            val pcm = sink.toByteArray()
            return if (pcm.isEmpty()) {
                Result.failure(IllegalStateException(AsrMessages.EMPTY_AUDIO))
            } else {
                Result.success(WavEncoder.encode(pcm, sampleRate))
            }
        } finally {
            // release() 足以释放麦克风与底层缓冲；任何异常都由 recordWav 顶层转成中文失败原因
            recorder.release()
        }
    }

    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
