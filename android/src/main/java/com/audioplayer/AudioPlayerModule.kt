package com.audioplayer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import com.facebook.react.bridge.*
import com.google.common.util.concurrent.MoreExecutors
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.audioplayer.data.AudioData
import com.audioplayer.constants.Events
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import android.media.MediaPlayer
import android.provider.ContactsContract.CommonDataKinds.Website.URL
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteOrder
import kotlin.math.abs

class AudioPlayerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val audioDataMap = mutableMapOf<String, AudioData>()
  private val updateTasks = mutableMapOf<String, ScheduledFuture<*>>()
  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val positionUpdateIntervals = mutableMapOf<String, Long>()
  private val playerUrls = mutableMapOf<String, String>()
  private var currentFocusPlayerId: String? = null

  override fun getName(): String {
    return "AudioPlayerModule"
  }

  @OptIn(UnstableApi::class)
  @ReactMethod
  fun initialize(playerId: String, promise: Promise) {
    Log.d("AudioPlayerModule", "initialize method called")
    try {
      // Check if player with this ID already exists
      if (audioDataMap.containsKey(playerId)) {
        throw Exception("Player with ID '$playerId' already exists. Release the existing player before initializing a new one with the same ID.")
      }

      // Create AudioData instance
      val audioData = AudioData(playerId)

      // Create ExoPlayer instance
      val player = ExoPlayer.Builder(reactApplicationContext).build().apply {
        addListener(object : Player.Listener {

          override fun onPlaybackStateChanged(state: Int) {
            when (state) {
              Player.STATE_ENDED -> {
                val params = Arguments.createMap().apply {
                  putString("playerId", playerId)
                }
                sendEvent(Events.ON_PLAYBACK_FINISHED, params)
//                stopPositionTracking(playerId)
              }
              Player.STATE_READY -> {
                val params = Arguments.createMap().apply {
                  putString("playerId", playerId)
                  putDouble("duration", duration.toDouble())
                  putDouble("currentPosition", currentPosition.toDouble())
                  putDouble("bufferedPosition", bufferedPosition.toDouble())
                  putBoolean("isPlaying", isPlaying)
                }
                Log.d("AudioPlayerModule", "onPlaybackStateChanged: STATE_READY ")
                sendEvent(Events.ON_PLAYBACK_STATE_CHANGED, params)

                // Send onLoaded event
                val loadedParams = Arguments.createMap().apply {
                  putString("playerId", playerId)
                }
                sendEvent(Events.ON_LOADED, loadedParams)
                
                // Start position tracking if player is ready
//                if (isPlaying) {
//                  startPositionTracking(playerId)
//                }
              }
              Player.STATE_BUFFERING -> {
                val params = Arguments.createMap().apply {
                  putString("playerId", playerId)
                  putBoolean("isBuffering", true)
                }
                sendEvent(Events.ON_BUFFERING, params)
              }
              Player.STATE_IDLE -> {
                val params = Arguments.createMap().apply {
                  putString("playerId", playerId)
                  putBoolean("isBuffering", false)
                }
                sendEvent(Events.ON_BUFFERING, params)
//                stopPositionTracking(playerId)
              }
            }
          }

//          override fun onIsPlayingChanged(isPlaying: Boolean) {
//            if (isPlaying) {
//              startPositionTracking(playerId)
//            } else {
//              stopPositionTracking(playerId)
//            }
//          }

          override fun onEvents(player: Player, events: Player.Events) {
            val bufferedPosition = player.bufferedPosition
            val bufferedPercentage = player.bufferedPercentage

            val params = Arguments.createMap().apply {
              putString("playerId", playerId)
              putDouble("bufferedPosition", bufferedPosition.toDouble())
              putDouble("bufferedPercentage", bufferedPercentage.toDouble())
            }
            sendEvent(Events.ON_BUFFERED_PROGRESS, params)
          }
        })
      }

      // Set player to AudioData
      audioData.setPlayer(player)

      // Store AudioData in map
      audioDataMap[playerId] = audioData
      
      // Initialize update interval
//      positionUpdateIntervals[playerId] = DEFAULT_POSITION_UPDATE_INTERVAL_MS

      promise.resolve(null)
    } catch (e: Exception) {
      Log.d("AudioPlayerModule", "initial method error${e}")
      promise.reject("INIT_ERROR", e)
    }
  }

  @ReactMethod
  fun setPositionUpdateInterval(playerId: String, intervalMs: Double, promise: Promise) {
    try {
      val interval = intervalMs.toLong()
      if (interval < 50) {
        throw Exception("Position update interval must be at least 50ms")
      }
      
//      positionUpdateIntervals[playerId] = interval
      
      // If tracking is already active, restart it with the new interval
//      val player = audioDataMap[playerId]?.getPlayer()
//      if (player != null && player.isPlaying && updateTasks.containsKey(playerId)) {
//        stopPositionTracking(playerId)
//        startPositionTracking(playerId)
//      }
      
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("INTERVAL_ERROR", e)
    }
  }

  private fun sendEvent(eventName: String, params: WritableMap?) {
    reactApplicationContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  @OptIn(UnstableApi::class)
  @ReactMethod
  fun load(playerId: String, url: String, promise: Promise) {
    Log.d("AudioPlayerModule", "load method called")
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")

      // Update the URL in AudioData
      audioData.setUrlOrPath(url)

      val dataSourceFactory = DefaultHttpDataSource.Factory()
      val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(url))
      player.setMediaSource(mediaSource)
      player.prepare()

      // Generate and emit initial waveform data
//      val waveformData = WaveformDataGenerator.generateWaveformData(url)
//      emitWaveformData(playerId, waveformData, 0)

      promise.resolve(null)
    } catch (e: Exception) {
      Log.d("AudioPlayerModule", "load method error${e}")
      promise.reject("LOAD_ERROR", e)
    }
  }

  @OptIn(UnstableApi::class)
  @ReactMethod
  fun play(playerId: String, promise: Promise) {
    Log.d("AudioPlayerModule", "play method called")
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      player.play()
      currentFocusPlayerId = playerId;
      promise.resolve(null)
    } catch (e: Exception) {
      Log.d("AudioPlayerModule", "play method error${e}")
      promise.reject("PLAY_ERROR", e)
    }
  }

  @ReactMethod
  fun pause(playerId: String, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      player.pause()
        promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("PAUSE_ERROR", e)
    }
  }

  @ReactMethod
  fun stop(playerId: String, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      player.stop()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("STOP_ERROR", e)
    }
  }

  @ReactMethod
  fun seekTo(playerId: String, position: Double, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      player.seekTo(position.toLong())
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("SEEK_ERROR", e)
    }
  }

    @OptIn(UnstableApi::class)
    @ReactMethod
  fun getDuration(playerId: String, promise: Promise) {
    Log.d("AudioPlayerModule", "get duration method called")
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      promise.resolve(player.duration.toDouble())
    } catch (e: Exception) {
      promise.reject("DURATION_ERROR", e)
    }
  }

  @ReactMethod
  fun getCurrentLiveOffset(playerId: String, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      promise.resolve(player.bufferedPercentage.toDouble())
    } catch (e: Exception) {
      promise.reject("DURATION_ERROR", e)
    }
  }

  @ReactMethod
  fun getCurrentPosition(playerId: String, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      promise.resolve(player.currentPosition.toDouble())
    } catch (e: Exception) {
      promise.reject("POSITION_ERROR", e)
    }
  }

  @ReactMethod
  fun setVolume(playerId: String, volume: Double, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      player.volume = volume.toFloat()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("VOLUME_ERROR", e)
    }
  }

  @ReactMethod
  fun setRate(playerId: String, rate: Double, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      player.setPlaybackSpeed(rate.toFloat())
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("RATE_ERROR", e)
    }
  }

  @ReactMethod
  fun setPitch(playerId: String, pitch: Double, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      // player.setPitch(pitch.toFloat())  // Commented out as in original
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("PITCH_ERROR", e)
    }
  }

  @ReactMethod
  fun release(playerId: String, promise: Promise) {
    try {
      val audioData = audioDataMap.remove(playerId) ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")

      player.release()
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("RELEASE_ERROR", e)
    }
  }

  @ReactMethod
  fun hasPlayer(playerId: String, promise: Promise) {
    try {
      val exists = audioDataMap.containsKey(playerId)
      promise.resolve(exists)
    } catch (e: Exception) {
      promise.reject("PLAYER_CHECK_ERROR", e)
    }
  }

  private fun emitWaveformData(playerId: String, waveformData: List<Float>, bufferedPosition: Long) {
    Arguments.createMap().apply {
      putString("playerId", playerId)
      putArray("waveformData", Arguments.createArray().apply {
        waveformData.forEach { value -> pushDouble(value.toDouble()) }
      })
      putDouble("bufferedPosition", bufferedPosition.toDouble())
    }.also { event ->
      reactApplicationContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("onWaveformData", event)
    }
  }

  @ReactMethod
  fun getWaveformData(playerId: String, promise: Promise) {
    try {
      val url = playerUrls[playerId] ?: run {
        promise.reject("ERROR", "Player not found")
        return
      }

      val waveformData = WaveformDataGenerator.generateWaveformData(url)
      Arguments.createArray().apply {
        waveformData.forEach { value -> pushDouble(value.toDouble()) }
        promise.resolve(this)
      }
    } catch (e: Exception) {
      promise.reject("ERROR", e.message)
    }
  }

  @ReactMethod
  fun extractWaveformFromStreamedUrl(playerId: String, intervalMs: Int = 100): List<Float> {
    val tmpFile = File.createTempFile("buffered_audio", ".mp3", reactApplicationContext.cacheDir)
    tmpFile.deleteOnExit()

    val url = audioDataMap[playerId]?.getUrlOrPath();

    // Download some initial portion (e.g. 1MB)
    val conn = URL(url).openConnection()
    conn.connect()
    val input = conn.getInputStream()
    val output = FileOutputStream(tmpFile)

    val buffer = ByteArray(1024 * 1024) // 1MB
    val read = input.read(buffer)
    output.write(buffer, 0, read)
    output.close()
    input.close()

    // Now decode that downloaded portion
    return decodePcmAndAnalyzeWaveform(tmpFile.absolutePath, intervalMs)
  }

    @UnstableApi
    fun decodePcmAndAnalyzeWaveform(filePath: String, intervalMs: Int): List<Float> {
    val extractor = MediaExtractor()
    extractor.setDataSource(filePath)

    val trackIndex = (0 until extractor.trackCount).firstOrNull {
      extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: throw RuntimeException("No audio track found")

    extractor.selectTrack(trackIndex)
    val format = extractor.getTrackFormat(trackIndex)
    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

    val codec = MediaCodec.createDecoderByType(mime)
    codec.configure(format, null, null, 0)
    codec.start()

    val info = MediaCodec.BufferInfo()
    val waveform = mutableListOf<Float>()
    val bufferChunk = mutableListOf<Short>()

    var inputDone = false
    var outputDone = false

    while (!outputDone) {
      if (!inputDone) {
        val inputBufferIndex = codec.dequeueInputBuffer(10000)
        if (inputBufferIndex >= 0) {
          val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
          val sampleSize = extractor.readSampleData(inputBuffer, 0)
          if (sampleSize < 0) {
            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
          } else {
            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
            extractor.advance()
          }
        }
      }

      val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
      if (outputBufferIndex >= 0) {
        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
        outputBuffer.order(ByteOrder.nativeOrder())
        while (outputBuffer.remaining() >= 2) {
          val sample = outputBuffer.short
          bufferChunk.add(sample)

          // Once you have enough samples for interval
          if (bufferChunk.size >= (sampleRate * intervalMs / 1000)) {
            val peak = bufferChunk.maxOf { abs(it.toFloat()) } / 32768f
            waveform.add(peak)
            bufferChunk.clear()
          }
        }

        codec.releaseOutputBuffer(outputBufferIndex, false)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
          outputDone = true
        }
      }
    }

    codec.stop()
    codec.release()
    extractor.release()
    Log.e("AudioPlayerModuleWave", "Waveform extracted: $waveform")

    return waveform
  }
}
