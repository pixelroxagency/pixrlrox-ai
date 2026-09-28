package com.example.core.audio

import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode

class LameMp3Encoder(
    sampleRate: Int,
    channels: Int,
    bitrateKbps: Int
) {
    private val lame = Lame()
    private val gfp = lame.lame_init()

    init {
        gfp.in_samplerate = sampleRate
        gfp.out_samplerate = sampleRate
        gfp.num_channels = channels
        gfp.brate = bitrateKbps
        gfp.quality = 5
        gfp.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.STEREO
        lame.lame_init_params(gfp)
    }

    fun encodeBuffer(pcmData: ByteArray, offset: Int, length: Int, mp3Buffer: ByteArray): Int {
        if (length <= 0) return 0
        val channels = gfp.num_channels
        val samplesPerChannel = length / (2 * channels)
        if (samplesPerChannel <= 0) return 0

        val left = IntArray(samplesPerChannel)
        val right = IntArray(samplesPerChannel)

        var idx = offset
        if (channels == 1) {
            for (i in 0 until samplesPerChannel) {
                if (idx + 1 >= pcmData.size) break
                val s = (pcmData[idx].toInt() and 0xFF) or (pcmData[idx + 1].toInt() shl 8)
                left[i] = s.toShort().toInt()
                right[i] = left[i]
                idx += 2
            }
        } else {
            for (i in 0 until samplesPerChannel) {
                if (idx + 3 >= pcmData.size) break
                val s1 = (pcmData[idx].toInt() and 0xFF) or (pcmData[idx + 1].toInt() shl 8)
                val s2 = (pcmData[idx + 2].toInt() and 0xFF) or (pcmData[idx + 3].toInt() shl 8)
                left[i] = s1.toShort().toInt()
                right[i] = s2.toShort().toInt()
                idx += 4
            }
        }

        return lame.lame_encode_buffer_int(gfp, left, right, samplesPerChannel, mp3Buffer, 0, mp3Buffer.size)
    }

    fun encodeFinish(mp3Buffer: ByteArray): Int {
        return lame.lame_encode_flush(gfp, mp3Buffer, 0, mp3Buffer.size)
    }

    fun close() {
        try {
            lame.lame_close(gfp)
        } catch (_: Exception) {}
    }
}
