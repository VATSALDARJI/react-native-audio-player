package com.audioplayer.data

import androidx.media3.exoplayer.ExoPlayer

class AudioData(private val playerId: String) {
    private var urlOrPath: String? = null
    private var player: ExoPlayer? = null
    
    // Getters
    fun getPlayerId(): String {
        return playerId
    }
    
    fun getUrlOrPath(): String? {
        return urlOrPath
    }
    
    fun getPlayer(): ExoPlayer? {
        return player
    }
    
    // Setters
    fun setUrlOrPath(url: String) {
        this.urlOrPath = url
    }
    
    fun setPlayer(exoPlayer: ExoPlayer) {
        this.player = exoPlayer
    }
}
