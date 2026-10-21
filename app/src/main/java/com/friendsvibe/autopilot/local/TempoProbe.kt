package com.friendsvibe.autopilot.local

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.friendsvibe.autopilot.rank.BpmEstimate
import com.friendsvibe.autopilot.rank.TagBpm
import java.io.FileInputStream
import java.nio.ByteBuffer

class TempoProbe(private val context: Context) {
    fun readTag(uri: Uri): Int? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    val channel = input.channel
                    val size = channel.size()
                    if (size <= 0L) return null
                    val headLength = minOf(size, HEAD_BYTES).toInt()
                    val head = ByteArray(headLength)
                    channel.read(ByteBuffer.wrap(head))
                    TagBpm.read(head)?.let { return it }
                    if (size > headLength) {
                        val tailLength = minOf(TAIL_BYTES, size).toInt()
                        val tail = ByteArray(tailLength)
                        channel.position(size - tailLength)
                        channel.read(ByteBuffer.wrap(tail))
                        return TagBpm.read(tail)
                    }
                    null
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Tempo tag unreadable", error)
        }.getOrNull()
    }

    fun estimate(uri: Uri): Int? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val samples = ShortArray(48_000 * 8)
            var filled = 0
            var sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44_100
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + DECODE_BUDGET_MS
            var inputDone = false
            while (SystemClock.elapsedRealtime() < deadline && filled < sampleRate * 8 && filled < samples.size) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(5_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 5_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        sampleRate = codec.outputFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                    }
                    else -> if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 1) {
                            val channels = codec.outputFormat.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
                            sampleRate = codec.outputFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val decoded = ShortArray(info.size / 2)
                            output.asShortBuffer().get(decoded)
                            val step = channels.coerceAtLeast(1)
                            var cursor = 0
                            while (cursor < decoded.size && filled < samples.size) {
                                samples[filled] = decoded[cursor]
                                filled += 1
                                cursor += step
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            if (filled == 0) null else BpmEstimate.estimate(samples.copyOf(filled), sampleRate)
        } catch (error: Exception) {
            Log.w(TAG, "Tempo estimate failed", error)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private companion object {
        const val TAG = "FriendsVibe"
        const val HEAD_BYTES = 512L * 1024L
        const val TAIL_BYTES = 256L * 1024L
        const val DECODE_BUDGET_MS = 2_500L
    }
}
