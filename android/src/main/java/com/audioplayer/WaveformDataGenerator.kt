package com.audioplayer

import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveformDataGenerator {
    companion object {
        private const val TAG = "WaveformDataGenerator"
        private const val SAMPLE_SIZE = 100 // Number of samples to generate
        private const val BYTES_PER_SAMPLE = 2 // 16-bit audio

        fun generateWaveformData(audioPath: String): List<Float> {
            val waveformData = mutableListOf<Float>()
            
            FileInputStream(audioPath).use { fis ->
                try {
                    val audioFile = File(audioPath)
                    if (!audioFile.exists()) {
                        Log.e(TAG, "Audio file does not exist: $audioPath")
                        return waveformData
                    }

                    // Get audio duration using MediaMetadataRetriever
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(audioPath)
//                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
//                        val duration = durationStr?.toLongOrNull() ?: 0L
                    }

                    // Calculate samples per segment
                    val fileSize = audioFile.length()
                    val samplesPerSegment = (fileSize / (SAMPLE_SIZE * BYTES_PER_SAMPLE)).toInt()
                    
                    val buffer = ByteArray(samplesPerSegment * BYTES_PER_SAMPLE)
                    
                    repeat(SAMPLE_SIZE) {
                        val bytesRead = fis.read(buffer)
                        if (bytesRead <= 0) return@repeat

                        // Calculate RMS value for this segment
                        val rms = calculateRMS(buffer, bytesRead)
                        waveformData.add(rms)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error generating waveform data: ${e.message}")
                }
            }

            return normalizeWaveformData(waveformData)
        }

        private fun calculateRMS(buffer: ByteArray, bytesRead: Int): Float {
            val samples = bytesRead / BYTES_PER_SAMPLE
            var sum = 0f
            
            for (i in 0 until samples) {
                val offset = i * BYTES_PER_SAMPLE
                val sample = ByteBuffer.wrap(buffer, offset, BYTES_PER_SAMPLE)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .short
                sum += sample * sample
            }
            
            return kotlin.math.sqrt(sum / samples) / Short.MAX_VALUE
        }

        private fun normalizeWaveformData(data: List<Float>): List<Float> {
            if (data.isEmpty()) return data

            val maxValue = data.maxOrNull() ?: 0f
            return data.map { value -> if (maxValue > 0) value / maxValue else 0f }
        }
    }
} 