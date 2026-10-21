package com.friendsvibe.autopilot.rank

import kotlin.math.roundToInt
import kotlin.math.sqrt

object BpmEstimate {
    fun estimate(pcm: ShortArray, sampleRate: Int): Int? {
        if (sampleRate < 1000 || pcm.size < sampleRate / 2) return null
        val envelopeRate = 100
        val hop = maxOf(1, sampleRate / envelopeRate)
        val envelopeCount = pcm.size / hop
        if (envelopeCount < 8) return null
        val envelope = DoubleArray(envelopeCount)
        var offset = 0
        for (bin in envelope.indices) {
            var energy = 0.0
            val end = offset + hop
            var cursor = offset
            while (cursor < end) {
                val sample = pcm[cursor].toDouble()
                energy += sample * sample
                cursor += 1
            }
            envelope[bin] = sqrt(energy / hop)
            offset = end
        }
        val flux = DoubleArray(envelope.size)
        var previous = envelope[0]
        for (bin in envelope.indices) {
            val delta = envelope[bin] - previous
            flux[bin] = if (delta > 0) delta else 0.0
            previous = envelope[bin]
        }
        val peak = flux.maxOrNull() ?: return null
        if (peak <= 0.0) return null
        for (bin in flux.indices) flux[bin] /= peak

        val minBpm = 70
        val maxBpm = 180
        val minLag = maxOf(1, envelopeRate * 60 / maxBpm)
        val maxLag = envelopeRate * 60 / minBpm
        var bestLag = -1
        var best = 0.0
        val scores = HashMap<Int, Double>()
        var lag = minLag
        while (lag <= maxLag && lag < flux.size) {
            var correlation = 0.0
            var count = 0
            var index = 0
            while (index + lag < flux.size) {
                correlation += flux[index] * flux[index + lag]
                count += 1
                index += 1
            }
            if (count > 0) correlation /= count
            scores[lag] = correlation
            if (correlation > best) {
                best = correlation
                bestLag = lag
            }
            lag += 1
        }
        if (bestLag <= 0 || best <= 0.002) return null
        val half = bestLag / 2
        val halfScore = scores[half]
        if (half >= minLag && halfScore != null && halfScore >= best * 0.9) {
            bestLag = half
        }
        return (60.0 * envelopeRate / bestLag).roundToInt().takeIf { it in minBpm..maxBpm }
    }
}
