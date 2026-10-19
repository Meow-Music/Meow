package com.meow.music.discord

import androidx.compose.runtime.Immutable

@Immutable
data class MeowDiscordButton(
  val label: String,
  val url: String,
)

@Immutable
data class MeowDiscordAssets(
  val largeImage: String? = null,
  val largeText: String? = null,
  val smallImage: String? = null,
  val smallText: String? = null,
)

@Immutable
data class MeowDiscordTimestamps(
  val startMs: Long? = null,
  val endMs: Long? = null,
)

@Immutable
data class MeowDiscordActivity(
  val applicationId: Long,
  val name: String,
  val type: Int, // 2 for Listening
  val details: String?,
  val state: String?,
  val assets: MeowDiscordAssets = MeowDiscordAssets(),
  val buttons: List<MeowDiscordButton> = emptyList(),
  val timestamps: MeowDiscordTimestamps = MeowDiscordTimestamps(),
  val onlineStatus: String = "online",
)

enum class DiscordActivityType(
  val nativeValue: Int,
) {
  Playing(0),
  Streaming(1),
  Listening(2),
  Watching(3),
  Competing(5),
  ;

  companion object {
    fun fromPreference(value: String): DiscordActivityType =
      when (value.uppercase()) {
        "PLAYING" -> Playing
        "STREAMING" -> Streaming
        "WATCHING" -> Watching
        "COMPETING" -> Competing
        else -> Listening
      }
  }
}

enum class DiscordOnlineStatus(
  val nativeValue: Int,
) {
  Online(0),
  Idle(3),
  Dnd(4),
  Invisible(5),
  Streaming(6),
  ;

  companion object {
    fun fromPreference(value: String): DiscordOnlineStatus =
      when (value.lowercase()) {
        "idle" -> Idle
        "dnd" -> Dnd
        "invisible" -> Invisible
        "streaming" -> Streaming
        else -> Online
      }
  }
}
