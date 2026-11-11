package org.fossify.gallery.helpers

import androidx.media3.exoplayer.ExoPlayer

object PlayerHolder {
    @Volatile var player: ExoPlayer? = null
        private set
    @Volatile var backgroundMode: Boolean = false
        private set

    fun attach(p: ExoPlayer) { player = p }
    fun exitBackground() { backgroundMode = false }
    fun enterBackground() { backgroundMode = true }
    fun clear() {
        player = null
        backgroundMode = false
    }
    fun shouldSkipRelease(callerPlayer: ExoPlayer?): Boolean =
        backgroundMode && player === callerPlayer
}
