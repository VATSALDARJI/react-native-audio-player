package com.audioplayer

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

class AudioPlayerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private var player: ExoPlayer? = null
  private val players = mutableMapOf<String, ExoPlayer>()
  private val audioDataMap = mutableMapOf<String, AudioData>()
  private val updateTasks = mutableMapOf<String, ScheduledFuture<*>>()
  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val positionUpdateIntervals = mutableMapOf<String, Long>()
  
  companion object {
    private const val DEFAULT_POSITION_UPDATE_INTERVAL_MS = 200L
    
    init {
      try {
        System.loadLibrary("react-native-audio-player")
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override fun getName(): String {
    return "AudioPlayerModule"
  }

  @OptIn(UnstableApi::class)
  @ReactMethod
  fun initialize(playerId: String, promise: Promise) {
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
                stopPositionTracking(playerId)
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
                if (isPlaying) {
                  startPositionTracking(playerId)
                }
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
                stopPositionTracking(playerId)
              }
            }
          }

          override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
              startPositionTracking(playerId)
            } else {
              stopPositionTracking(playerId)
            }
          }

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
      positionUpdateIntervals[playerId] = DEFAULT_POSITION_UPDATE_INTERVAL_MS

      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("INIT_ERROR", e)
    }
  }

  private fun startPositionTracking(playerId: String) {
    stopPositionTracking(playerId) // Stop any existing tracking first
    
    val player = audioDataMap[playerId]?.getPlayer() ?: return
    val interval = positionUpdateIntervals[playerId] ?: DEFAULT_POSITION_UPDATE_INTERVAL_MS
    
    val task = executor.scheduleAtFixedRate({
      try {
        if (player.isPlaying) {
          val params = Arguments.createMap().apply {
            putString("playerId", playerId)
            putDouble("currentPosition", player.currentPosition.toDouble())
            putDouble("duration", player.duration.toDouble())
            putDouble("playbackSpeed", player.playbackParameters.speed.toDouble())
          }
          
          // Use the UI thread to emit the event to React Native
          reactApplicationContext.runOnUiQueueThread {
            sendEvent(Events.ON_POSITION_CHANGED, params)
          }
        }
      } catch (e: Exception) {
        Log.e("AudioPlayerModule", "Error in position tracking", e)
      }
    }, 0, interval, TimeUnit.MILLISECONDS)
    
    updateTasks[playerId] = task
  }
  
  private fun stopPositionTracking(playerId: String) {
    val task = updateTasks[playerId] ?: return
    task.cancel(false)
    updateTasks.remove(playerId)
  }

  @ReactMethod
  fun setPositionUpdateInterval(playerId: String, intervalMs: Double, promise: Promise) {
    try {
      val interval = intervalMs.toLong()
      if (interval < 50) {
        throw Exception("Position update interval must be at least 50ms")
      }
      
      positionUpdateIntervals[playerId] = interval
      
      // If tracking is already active, restart it with the new interval
      val player = audioDataMap[playerId]?.getPlayer()
      if (player != null && player.isPlaying && updateTasks.containsKey(playerId)) {
        stopPositionTracking(playerId)
        startPositionTracking(playerId)
      }
      
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
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("LOAD_ERROR", e)
    }
  }

  @OptIn(UnstableApi::class)
  @ReactMethod
  fun addBufferingListener(playerId: String, callback: Callback) {
    Log.d("AudioPlayerModule", "addBufferingListener called")
    callback.invoke(players[playerId]?.currentPosition);
  }

  @ReactMethod
  fun play(playerId: String, promise: Promise) {
    try {
      val audioData = audioDataMap[playerId] ?: throw Exception("Player not initialized")
      val player = audioData.getPlayer() ?: throw Exception("Player not initialized")
      player.play()
      promise.resolve(null)
    } catch (e: Exception) {
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

  @ReactMethod
  fun getDuration(playerId: String, promise: Promise) {
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
      
      // Stop position tracking
      stopPositionTracking(playerId)
      positionUpdateIntervals.remove(playerId)
      
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

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    
    // Clean up all players and executor when the module is destroyed
    audioDataMap.keys.toList().forEach { playerId ->
      try {
        val player = audioDataMap[playerId]?.getPlayer()
        stopPositionTracking(playerId)
        player?.release()
      } catch (e: Exception) {
        Log.e("AudioPlayerModule", "Error releasing player on destroy", e)
      }
    }
    
    audioDataMap.clear()
    positionUpdateIntervals.clear()
    updateTasks.clear()
    
    try {
      executor.shutdown()
      if (!executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
        executor.shutdownNow()
      }
    } catch (e: Exception) {
      executor.shutdownNow()
    }
  }

  // Native methods
  private external fun nativePlay(url: String): String
  private external fun nativePause(): String
  private external fun nativeStop(): String
  private external fun nativeGetCurrentPosition(): Long
  private external fun nativeGetDuration(): Long
  private external fun nativeGetBufferedPosition(): Long
  private external fun nativeSeekTo(position: Double): String
}
