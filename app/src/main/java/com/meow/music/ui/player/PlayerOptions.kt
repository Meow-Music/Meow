package com.meow.music.ui.player

import com.meow.music.ui.theme.MeowIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.meow.music.data.model.Song
import com.meow.music.ui.LocalNavigator
import com.meow.music.ui.Routes
import com.meow.music.ui.components.ActionGroup
import com.meow.music.ui.components.ActionHeader
import com.meow.music.ui.components.ActionItem
import com.meow.music.ui.components.ActionSheet
import com.meow.music.ui.components.PlaylistPicker
import com.meow.music.ui.components.QuickActions
import com.meow.music.ui.components.SheetIconButton
import com.meow.music.ui.components.runAction
import com.meow.music.ui.components.shareTarget
import com.meow.music.ui.theme.LocalContainer

/**
 * The player's ⋯ menu, in the app's frosted look (the song's cover as frosted glass): like and
 * share up top, the everyday actions as tiles, then lyrics, player and "go to" groups.
 */
@Composable
fun PlayerOptionsSheet(
    song: Song,
    hasCanvas: Boolean,
    statsShown: Boolean,
    hasNext: Boolean,
    onLyricsSource: () -> Unit,
    onLyricsTiming: () -> Unit,
    onRedownloadLyrics: () -> Unit,
    onToggleStats: () -> Unit,
    onSleepTimer: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val liked by c.library.likedIds.collectAsState()
    val downloads by c.downloads.downloads.collectAsState()
    val videos by c.downloads.videos.collectAsState()
    val hidden by c.canvas.hidden.collectAsState()
    val saved by c.canvas.saved.collectAsState()
    val sleepAt by c.player.sleepAt.collectAsState()
    val sleepEnd by c.player.sleepEndOfSong.collectAsState()
    val together by c.together.state.collectAsState()
    val disliked by c.library.dislikedIds.collectAsState()
    var pickPlaylist by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    if (pickPlaylist) { PlaylistPicker(song) { pickPlaylist = false; onDismiss() }; return }
    if (sharing) { com.meow.music.ui.components.ShareSheet(song.shareTarget()) { sharing = false; onDismiss() }; return }

    val isLiked = song.id in liked
    val dl = downloads[song.id]
    val downloaded = dl?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    val sleepMin = if (sleepAt > 0) ((sleepAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1 else 0
    val sleeping = sleepAt > 0 || sleepEnd
    val inSession = together !is com.meow.music.playback.together.TogetherState.Idle

    val quick = listOfNotNull(
        ActionItem(MeowIcons.Radio, "Radio") { c.player.playRadio(song) },
        ActionItem(
            if (downloaded) MeowIcons.DownloadDone else MeowIcons.Download, "Download",
            value = when { downloaded -> "Downloaded"; dl != null -> "${dl.percent.toInt().coerceAtLeast(0)}%"; else -> null },
            active = downloaded, keepOpen = true,
        ) { if (dl != null) c.downloads.remove(song.id) else c.downloads.download(song) },
        if (c.settings.current.isLoggedIn) ActionItem(MeowIcons.PlaylistAdd, "Playlist", keepOpen = true) { pickPlaylist = true } else null,
        ActionItem(
            MeowIcons.Moon, "Sleep",
            value = when { sleepEnd -> "Song end"; sleepAt > 0 -> "${sleepMin} min"; else -> null },
            active = sleeping, onClick = onSleepTimer,
        ),
    )
    val speed by c.player.episodeSpeed.collectAsState()
    if (song.isEpisode) {
        val speeds = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)
        val episode = listOfNotNull(
            ActionItem(
                MeowIcons.Speed, "Playback speed", value = "${speed.toString().removeSuffix(".0")}×", active = speed != 1f, keepOpen = true,
            ) { c.player.setEpisodeSpeed(speeds[(speeds.indexOf(speed) + 1) % speeds.size]) },
            song.podcastId?.let { id -> ActionItem(MeowIcons.Podcasts, "Go to podcast") { onCollapse(); nav.go(Routes.podcast(id)) } },
            ActionItem(MeowIcons.Stats, "Stats for nerds", active = statsShown, onClick = onToggleStats),
        )
        ActionSheet(song.thumbnail, onDismiss) { close ->
            ActionHeader(song.thumbnail, song.title, song.artistText) {
                SheetIconButton(MeowIcons.Share, "Share") { sharing = true }
            }
            QuickActions(quick.filter { it.label != "Radio" && it.label != "Playlist" }) { runAction(it, close) }
            ActionGroup("Episode", episode) { runAction(it, close) }
        }
        return
    }
    val translating = c.settings.flow.collectAsState().value.lyricsTranslate
    val lyrics = listOf(
        ActionItem(MeowIcons.Lyrics, "Lyrics source", onClick = onLyricsSource),
        ActionItem(MeowIcons.Timer, "Lyrics timing", onClick = onLyricsTiming),
        ActionItem(
            MeowIcons.Translate, "Translate lyrics", value = if (translating) "On" else "Off", active = translating, keepOpen = true,
        ) { c.settings.setLyricsTranslate(!translating) },
        ActionItem(MeowIcons.Refresh, "Download lyrics again") { onRedownloadLyrics(); toast("Downloading lyrics again") },
    ) + listOfNotNull(
        // Into your own FLAC / MP3 file, for other players (#16).
        if (song.isLocal || c.downloads.downloads.value[song.id]?.lossless == true)
            ActionItem(MeowIcons.Download, "Save lyrics into the file") { c.lyricsToFiles.request(listOf(song)) } else null,
    )
    val cover = com.meow.music.data.canvas.CanvasRepository.albumKey(song)
    val immersive = c.settings.flow.collectAsState().value.immersivePlayer
    val player = listOfNotNull(
        // The cover fills the screen with the lyrics over it; a tap there brings this back.
        ActionItem(MeowIcons.Fullscreen, "Immersive mode", value = if (immersive) "On" else null, active = immersive) { c.settings.setImmersivePlayer(!immersive) },
        com.meow.music.ui.components.videoDownloadAction(song, videos[song.id]),
        ActionItem(MeowIcons.Groups, "Listen together", value = if (inSession) "On" else null, active = inSession) { onCollapse(); nav.go(Routes.TOGETHER) },
        ActionItem(MeowIcons.Stats, "Stats for nerds", active = statsShown, onClick = onToggleStats),
        if (hasCanvas) ActionItem(MeowIcons.AnimatedCover, "Animated cover", active = song.id !in hidden, keepOpen = true) { c.canvas.setHidden(song.id, song.id !in hidden) } else null,
        if (!hasCanvas) null else if (saved.has(song.id, cover)) ActionItem(MeowIcons.Download, "Animated cover saved", active = true, keepOpen = true) { c.canvasStore.unsave(song) }
        else ActionItem(MeowIcons.Download, "Save animated cover", keepOpen = true) {
            c.canvasStore.save(song)
            toast("Saving animated covers for this album")
        },
    )
    val go = listOfNotNull(
        song.album?.id?.let { id -> ActionItem(MeowIcons.Album, "Go to album") { onCollapse(); nav.go(Routes.album(id)) } },
    ) + com.meow.music.ui.components.goToArtists(song) { route -> onCollapse(); nav.go(route) } + listOfNotNull(
        if (song.id in disliked) ActionItem(MeowIcons.ThumbDownFilled, "Not for me", active = true, keepOpen = true) { c.library.setDisliked(song, false) }
        else ActionItem(MeowIcons.ThumbDown, "Not for me") {
            c.library.setDisliked(song, true)
            toast("Got it — Meow will steer clear of this song")
            if (hasNext) c.player.next()
        },
    )

    ActionSheet(song.thumbnail, onDismiss) { close ->
        ActionHeader(song.thumbnail, song.title, song.artistText) {
            SheetIconButton(if (isLiked) MeowIcons.HeartFilled else MeowIcons.Heart, if (isLiked) "Remove from liked" else "Like", active = isLiked) {
                c.library.toggleLike(song)
            }
            if (!song.isLocal) SheetIconButton(MeowIcons.Share, "Share") { sharing = true }
        }
        // A file on the phone: no radio, download, YouTube playlist or music video.
        QuickActions(if (song.isLocal) quick.filter { it.label == "Sleep" } else quick) { runAction(it, close) }
        ActionGroup("Lyrics", lyrics) { runAction(it, close) }
        ActionGroup("Player", if (song.isLocal) player.filter { it.label == "Stats for nerds" || it.label == "Immersive mode" } else player) { runAction(it, close) }
        ActionGroup("More", go) { runAction(it, close) }
    }
}
