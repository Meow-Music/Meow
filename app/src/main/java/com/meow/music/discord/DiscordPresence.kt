package com.meow.music.discord

import android.content.Context
import android.net.Uri
import android.util.Log
import com.meow.music.data.model.Song
import com.meow.music.data.prefs.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Discord Rich Presence manager for Meow.
 * Manages OAuth2 authorization session and gateway rich presence updates.
 */
class DiscordPresence(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  enum class State { OFF, CONNECTING, CONNECTED, SIGNED_OUT }

  private val _state = MutableStateFlow(State.OFF)
  val state: StateFlow<State> = _state

  private val _userName = MutableStateFlow<String?>(null)
  val userName: StateFlow<String?> = _userName

  private val _signedIn = MutableStateFlow(false)
  val signedIn: Boolean get() = _signedIn.value

  private var enabled = false
  private var lastSongKey: String? = null
  private var lastPositionMs: Long = 0L
  private var lastIsPlaying: Boolean = false
  private var currentAuthSession: DiscordAuthorizationSession? = null

  init {
    // Observe stored username and token state from DataStore
    scope.launch(Dispatchers.IO) {
      context.dataStore.data.collect { prefs ->
        val username = prefs[DiscordNameKey] ?: prefs[DiscordUsernameKey]
        _userName.value = username
        val token = prefs[DiscordTokenKey]
        val hasToken = !token.isNullOrBlank()
        _signedIn.value = hasToken
        if (hasToken && enabled) {
          _state.value = State.CONNECTED
        }
      }
    }

    // Handle OAuth deep link callbacks
    scope.launch(Dispatchers.IO) {
      DiscordAuthCoordinator.redirects.collect { uri ->
        val session = currentAuthSession
        if (session != null) {
          val result = DiscordOAuthRepository.completeAuthorization(context, session, uri)
          currentAuthSession = null
          if (result.isSuccess) {
            _state.value = State.CONNECTED
          } else {
            Log.w("DiscordPresence", "Authorization failed", result.exceptionOrNull())
          }
        }
      }
    }
  }

  fun startAuthorization(): Uri {
    val session = DiscordOAuthRepository.createAuthorizationSession()
    currentAuthSession = session
    return session.authorizationUri
  }

  fun signInWithToken(token: String, onResult: ((Boolean) -> Unit)? = null) {
    scope.launch(Dispatchers.IO) {
      val result = DiscordOAuthRepository.loginWithToken(context, token)
      if (result.isSuccess) {
        _state.value = State.CONNECTED
        onResult?.invoke(true)
      } else {
        Log.w("DiscordPresence", "Sign in with token failed", result.exceptionOrNull())
        onResult?.invoke(false)
      }
    }
  }

  fun setEnabled(on: Boolean) {
    if (on == enabled) return
    enabled = on
    if (on && signedIn) {
      _state.value = State.CONNECTED
    } else if (!on) {
      lastSongKey = null
      scope.launch(Dispatchers.IO) {
        DiscordSocialPresenceClient.close()
        _state.value = State.OFF
      }
    }
  }

  fun show(song: Song?, playing: Boolean, positionMs: Long, durationMs: Long) {
    if (!enabled || !signedIn) return
    val songKey = song?.id ?: ""
    if (song == null || !playing) {
      if (lastSongKey != null) {
        lastSongKey = null
        scope.launch(Dispatchers.IO) {
          val token = DiscordOAuthRepository.getValidAccessToken(context)
          if (!token.isNullOrBlank()) {
            DiscordSocialPresenceClient.clearPresence(token)
          }
        }
      }
      return
    }

    // Avoid redundant updates if playing state hasn't changed meaningfully
    if (songKey == lastSongKey && playing == lastIsPlaying && kotlin.math.abs(positionMs - lastPositionMs) < 3000) {
      return
    }
    lastSongKey = songKey
    lastPositionMs = positionMs
    lastIsPlaying = playing

    scope.launch(Dispatchers.IO) {
      val token = DiscordOAuthRepository.getValidAccessToken(context)
      if (token.isNullOrBlank()) return@launch

      _state.value = State.CONNECTING
      val now = System.currentTimeMillis()
      val startMs = now - positionMs.coerceAtLeast(0)
      val endMs = if (!song.isLive && durationMs > 0) startMs + durationMs else null

      val primaryArtist = song.primaryArtist.ifBlank { song.artists.joinToString(", ") { it.name } }
      val activity = DiscordPresenceActivity(
        applicationId = DiscordOAuthRepository.applicationId,
        name = "Meow",
        type = DiscordActivityType.Listening,
        details = song.title.take(128),
        state = ("by $primaryArtist").take(128).ifBlank { "Meow" },
        assets = DiscordPresenceAssets(
          largeImage = song.thumbnail,
          largeText = song.title,
          smallImage = null,
          smallText = "Meow Music",
        ),
        timestamps = DiscordPresenceTimestamps(
          startMs = startMs,
          endMs = endMs,
        ),
      )

      val res = DiscordSocialPresenceClient.updatePresence(token, activity)
      if (res.isSuccess) {
        _state.value = State.CONNECTED
      } else {
        Log.w("DiscordPresence", "Failed to update Discord presence", res.exceptionOrNull())
      }
    }
  }

  fun signOut() {
    scope.launch(Dispatchers.IO) {
      val token = DiscordOAuthRepository.getValidAccessToken(context)
      if (!token.isNullOrBlank()) {
        DiscordSocialPresenceClient.clearPresence(token)
      }
      DiscordSocialPresenceClient.close()
      DiscordOAuthRepository.clearSession(context)
      _state.value = State.SIGNED_OUT
      _userName.value = null
      _signedIn.value = false
    }
  }
}
