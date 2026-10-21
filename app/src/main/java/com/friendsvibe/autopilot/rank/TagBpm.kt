package com.friendsvibe.autopilot.rank

object TagBpm {
    fun read(bytes: ByteArray): Int? {
        if (bytes.size >= 10 && isId3(bytes)) {
            readId3(bytes)?.let { return it }
        }
        return readMp4(bytes)
    }

    private fun isId3(bytes: ByteArray): Boolean {
        return bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()
    }

    private fun readId3(bytes: ByteArray): Int? {
        val version = bytes[3].toInt() and 0xFF
        if (version !in 2..4) return null
        val flags = bytes[5].toInt() and 0xFF
        if (flags and 0x80 != 0) return null
        val tagSize = syncsafe(bytes, 6)
        var pos = 10
        if (version >= 3 && flags and 0x40 != 0 && pos + 4 <= bytes.size) {
            val extended = if (version == 4) syncsafe(bytes, pos) else be32(bytes, pos)
            if (extended <= 0) return null
            pos += extended
        }
        val end = minOf(bytes.size, 10 + tagSize)
        while (pos + 6 <= end) {
            if (bytes[pos] == 0.toByte()) break
            val (id, headerLength, frameSize) = if (version == 2) {
                if (pos + 6 > end) break
                Triple(String(bytes, pos, 3, Charsets.ISO_8859_1), 6, be24(bytes, pos + 3))
            } else {
                if (pos + 10 > end) break
                val size = if (version == 4) syncsafe(bytes, pos + 4) else be32(bytes, pos + 4)
                Triple(String(bytes, pos, 4, Charsets.ISO_8859_1), 10, size)
            }
            if (frameSize <= 0) break
            val dataStart = pos + headerLength
            if (dataStart + frameSize > bytes.size) break
            if (id == "TBPM" || id == "TBP") {
                parseText(bytes, dataStart, frameSize)?.let { return it }
            }
            pos = dataStart + frameSize
        }
        return null
    }

    private fun parseText(bytes: ByteArray, start: Int, size: Int): Int? {
        if (size < 2 || start + size > bytes.size) return null
        val encoding = bytes[start].toInt() and 0xFF
        val raw = bytes.copyOfRange(start + 1, start + size)
        val text = when (encoding) {
            0 -> String(raw, Charsets.ISO_8859_1)
            1 -> String(raw, Charsets.UTF_16)
            2 -> String(raw, Charsets.UTF_16BE)
            else -> String(raw, Charsets.UTF_8)
        }.trim().trim('\u0000')
        val number = text.takeWhile { it.isDigit() || it == '.' }
        val bpm = number.toFloatOrNull()?.toInt() ?: return null
        return bpm.takeIf { it in 40..240 }
    }

    private fun readMp4(bytes: ByteArray): Int? {
        var index = 4
        while (index <= bytes.size - 10) {
            if (
                bytes[index] == 't'.code.toByte() &&
                bytes[index + 1] == 'm'.code.toByte() &&
                bytes[index + 2] == 'p'.code.toByte() &&
                bytes[index + 3] == 'o'.code.toByte()
            ) {
                val size = be32(bytes, index - 4)
                if (size in 12..32 && index + 10 <= bytes.size) {
                    val bpm = ((bytes[index + 8].toInt() and 0xFF) shl 8) or (bytes[index + 9].toInt() and 0xFF)
                    if (bpm in 40..240) return bpm
                }
            }
            index += 1
        }
        return null
    }

    private fun syncsafe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun be32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun be24(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
    }
}
