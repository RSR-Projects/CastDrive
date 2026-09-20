package com.example.service

import android.media.MediaDescription
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService

/**
 * MediaBrowserService implementation for Android Auto.
 * This registers AutoMirror into Android Auto's native media framework,
 * allowing it to appear in Android Auto's media drawer and audio apps menu.
 */
class AutoMirrorMediaBrowserService : MediaBrowserService() {

    private lateinit var mediaSession: MediaSession

    companion object {
        private const val ROOT_ID = "automirror_root"
        private const val MEDIA_ID_MIRROR = "automirror_live_stream"
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSession(this, "AutoMirrorMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    updatePlaybackState(PlaybackState.STATE_PLAYING)
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        launchIntent?.let { startActivity(it) }
                    } catch (_: Exception) {}
                }

                override fun onPause() {
                    updatePlaybackState(PlaybackState.STATE_PAUSED)
                }

                override fun onStop() {
                    updatePlaybackState(PlaybackState.STATE_STOPPED)
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        sessionToken = mediaSession.sessionToken
        updatePlaybackState(PlaybackState.STATE_PAUSED)
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP
            )
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        val mediaItems = mutableListOf<MediaBrowser.MediaItem>()
        if (parentId == ROOT_ID) {
            val description = MediaDescription.Builder()
                .setMediaId(MEDIA_ID_MIRROR)
                .setTitle("CastDrive Live Stream")
                .setSubtitle("Phone Screen & Audio Mirror")
                .setDescription("Wireless & USB screen casting")
                .build()

            mediaItems.add(
                MediaBrowser.MediaItem(
                    description,
                    MediaBrowser.MediaItem.FLAG_PLAYABLE
                )
            )
        }
        result.sendResult(mediaItems)
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }
}
