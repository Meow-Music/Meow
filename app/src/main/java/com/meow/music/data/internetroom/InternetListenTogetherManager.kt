package com.meow.music.data.internetroom

import android.content.Context
import com.meow.music.AppContainer
import com.meow.music.data.model.AlbumRef
import com.meow.music.data.model.ArtistRef
import com.meow.music.data.model.Song
import com.meow.music.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InternetListenTogetherManager(
    val client: InternetListenTogetherClient,
    private val context: Context,
    private val appContainer: AppContainer
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playerConnection: PlayerConnection? = null
    private var hostWatchJob: kotlinx.coroutines.Job? = null

    val connectionState = client.connectionState
    val roomState = client.roomState
    val role = client.role
    val userId = client.userId
    val pendingJoinRequests = client.pendingJoinRequests
    val pendingSuggestions = client.pendingSuggestions
    val events = client.events

    private val _listenHere = MutableStateFlow(true)
    val listenHere: StateFlow<Boolean> = _listenHere.asStateFlow()

    private val _inSync = MutableStateFlow(true)
    val inSync: StateFlow<Boolean> = _inSync.asStateFlow()

    init {
        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is ListenTogetherEvent.JoinApproved -> {
                        if (_listenHere.value) syncRoomStateToPlayer(event.state)
                    }
                    is ListenTogetherEvent.SyncStateReceived -> {
                        if (_listenHere.value) syncSyncStateToPlayer(event.state)
                    }
                    is ListenTogetherEvent.PlaybackSync -> {
                        if (_listenHere.value) applyPlaybackAction(event.action)
                    }
                    is ListenTogetherEvent.LocalSuggestionApproved -> {
                        if (role.value == RoomRole.HOST) {
                            val song = event.payload.trackInfo.toSong()
                            playerConnection?.addToQueue(song)
                        }
                    }
                    else -> {}
                }
            }
        }

        // Watch role changes to start/stop host broadcasting
        scope.launch {
            role.collect { r ->
                hostWatchJob?.cancel()
                if (r == RoomRole.HOST) {
                    hostWatchJob = scope.launch { watchHostPlayer() }
                }
            }
        }
    }

    private suspend fun watchHostPlayer() {
        var lastSongId: String? = null
        var lastPlaying: Boolean? = null
        var lastPos = -1L
        var lastAt = android.os.SystemClock.elapsedRealtime()

        while (true) {
            kotlinx.coroutines.delay(500)
            val pc = playerConnection ?: continue
            if (role.value != RoomRole.HOST) break

            val currentSong = pc.currentSong.value
            val playing = pc.playWhenReady
            val pos = pc.position
            val queueSongs = pc.queue.value.map { it.song.toTrackInfo() }
            val trackInfo = currentSong?.toTrackInfo()

            client.updateLocalState(trackInfo, playing, pos, queueSongs)

            if (currentSong?.id != lastSongId) {
                lastSongId = currentSong?.id
                if (currentSong != null) {
                    client.sendPlaybackAction(
                        action = PlaybackActions.CHANGE_TRACK,
                        trackId = currentSong.id,
                        position = pos,
                        trackInfo = trackInfo,
                        queue = queueSongs
                    )
                }
            }

            if (playing != lastPlaying) {
                lastPlaying = playing
                client.sendPlaybackAction(
                    action = if (playing) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                    position = pos
                )
            }

            val now = android.os.SystemClock.elapsedRealtime()
            val expected = if (playing && lastPos >= 0) lastPos + (now - lastAt) else lastPos
            val jumped = lastPos >= 0 && kotlin.math.abs(pos - expected) > 1_500
            lastPos = pos
            lastAt = now

            if (jumped) {
                client.sendPlaybackAction(
                    action = PlaybackActions.SEEK,
                    position = pos
                )
            }
        }
    }

    private fun Song.toTrackInfo(): TrackInfo {
        return TrackInfo(
            id = id,
            title = title,
            artist = artists.joinToString(", ") { it.name },
            album = album?.title,
            duration = (durationSec * 1000L),
            thumbnail = thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg".takeIf { id.length == 11 && !id.contains("..") }
        )
    }

    private fun TrackInfo.toSong(): Song {
        val artwork = thumbnail?.ifBlank { null }
            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg".takeIf { id.length == 11 && !id.contains("..") }
        return Song(
            id = id,
            title = title,
            artists = listOf(ArtistRef(name = artist)),
            album = album?.takeIf { it.isNotBlank() }?.let { AlbumRef(title = it) },
            durationSec = (duration / 1000L).toInt(),
            thumbnail = artwork
        )
    }

    fun setListenHere(on: Boolean) {
        _listenHere.value = on
        appContainer.queue.followingHost = on
        if (on) {
            _inSync.value = true
            roomState.value?.let { syncRoomStateToPlayer(it) }
        } else {
            appContainer.queue.followingHost = false
        }
    }

    fun resync() {
        _inSync.value = true
        appContainer.queue.followingHost = _listenHere.value
        roomState.value?.let { syncRoomStateToPlayer(it) }
    }

    fun control(action: String, positionMs: Long? = null) {
        if (!canControl) return
        val pc = playerConnection
        if (client.connectionState.value != ConnectionState.CONNECTED) {
            client.connect()
        }
        val mappedAction = when (action) {
            "previous", "skip_prev" -> PlaybackActions.SKIP_PREV
            "next", "skip_next" -> PlaybackActions.SKIP_NEXT
            "play" -> PlaybackActions.PLAY
            "pause" -> PlaybackActions.PAUSE
            "seek" -> PlaybackActions.SEEK
            else -> action
        }
        client.sendPlaybackAction(mappedAction, position = positionMs ?: pc?.position)
    }

    val isHost: Boolean
        get() = role.value == RoomRole.HOST || (roomState.value?.hostId != null && roomState.value?.hostId == client.userId.value)

    val canControl: Boolean
        get() = isHost || roomState.value?.allowParticipantControl == true

    fun addSongToQueue(song: Song, next: Boolean = false) {
        if (roomState.value != null) {
            if (canControl) {
                client.sendPlaybackAction(
                    action = PlaybackActions.QUEUE_ADD,
                    trackInfo = song.toTrackInfo(),
                    insertNext = next
                )
            } else {
                client.suggestTrack(song.toTrackInfo())
            }
        }
    }

    fun playSong(song: Song) {
        if (roomState.value != null) {
            if (canControl) {
                client.sendPlaybackAction(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = song.id,
                    position = 0L,
                    trackInfo = song.toTrackInfo()
                )
            } else {
                client.suggestTrack(song.toTrackInfo())
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (roomState.value != null) {
            if (canControl && songs.isNotEmpty()) {
                val target = songs.getOrNull(startIndex) ?: songs.first()
                val q = songs.filter { it.id != target.id }.map { it.toTrackInfo() }
                client.sendPlaybackAction(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = target.id,
                    position = 0L,
                    trackInfo = target.toTrackInfo(),
                    queue = q
                )
            }
        }
    }

    fun clearQueue() {
        if (roomState.value != null) {
            if (canControl) {
                client.sendPlaybackAction(action = PlaybackActions.QUEUE_CLEAR)
            }
        }
    }

    fun suggestTrack(trackInfo: TrackInfo) {
        client.suggestTrack(trackInfo)
    }

    fun setAllowParticipantControl(allow: Boolean) {
        client.updateRoomSettings(allow)
    }

    fun setPlayerConnection(connection: PlayerConnection?) {
        playerConnection = connection
    }

    private fun syncRoomStateToPlayer(state: RoomState) {
        val pc = playerConnection ?: return
        val track = state.currentTrack ?: return
        val song = track.toSong()
        val queueSongs = state.queue.map { it.toSong() }
        val allSongs = listOf(song) + queueSongs
        appContainer.queue.followingHost = _listenHere.value
        pc.follow(allSongs, state.position, "Internet Room ${state.roomCode}", state.isPlaying)
    }

    private fun syncSyncStateToPlayer(state: SyncStatePayload) {
        val pc = playerConnection ?: return
        val track = state.currentTrack ?: return
        val song = track.toSong()
        val queueSongs = state.queue.orEmpty().map { it.toSong() }
        val allSongs = listOf(song) + queueSongs
        appContainer.queue.followingHost = _listenHere.value
        if (pc.currentSong.value?.id != song.id) {
            pc.follow(allSongs, state.position, "Internet Room", state.isPlaying)
        } else {
            if (state.isPlaying != pc.playWhenReady) pc.setPlaying(state.isPlaying)
            if (kotlin.math.abs(pc.position - state.position) > 1_500) pc.seekTo(state.position)
        }
    }

    private fun applyPlaybackAction(action: PlaybackActionPayload) {
        val pc = playerConnection ?: return
        if (!_listenHere.value) return
        _inSync.value = true
        when (action.action) {
            "play", PlaybackActions.PLAY -> if (!pc.playWhenReady) pc.setPlaying(true)
            "pause", PlaybackActions.PAUSE -> if (pc.playWhenReady) pc.setPlaying(false)
            "seek", PlaybackActions.SEEK -> action.position?.let { pos ->
                pc.seekTo(pos)
            }
            "next", "skip_next", PlaybackActions.SKIP_NEXT -> pc.next()
            "previous", "skip_prev", PlaybackActions.SKIP_PREV -> pc.previous()
            "track", "play_track", "change_track", PlaybackActions.CHANGE_TRACK -> {
                action.trackInfo?.let { t ->
                    val song = t.toSong()
                    val queueSongs = action.queue?.map { it.toSong() }.orEmpty()
                    val allSongs = listOf(song) + queueSongs
                    appContainer.queue.followingHost = _listenHere.value
                    if (pc.currentSong.value?.id != song.id) {
                        pc.follow(allSongs, action.position ?: 0L, "Internet Room", true)
                    } else {
                        action.position?.let { pos ->
                            if (kotlin.math.abs(pc.position - pos) > 1_500) {
                                pc.seekTo(pos)
                            }
                        }
                    }
                }
            }
            "queue_add", "add_queue", PlaybackActions.QUEUE_ADD -> {
                action.trackInfo?.let { t ->
                    val song = t.toSong()
                    if (action.insertNext == true) pc.playNext(song) else pc.addToQueue(song)
                }
            }
            "queue_remove", PlaybackActions.QUEUE_REMOVE -> {
                action.trackId?.let { id ->
                    val idx = pc.queue.value.indexOfFirst { it.song.id == id }
                    if (idx >= 0) pc.removeAt(idx)
                }
            }
            "queue_clear", PlaybackActions.QUEUE_CLEAR -> {
                pc.clearUpcoming()
            }
        }
    }

    fun createRoom(username: String) {
        client.connect()
        client.createRoom(username)
    }

    fun joinRoom(roomCode: String, username: String) {
        client.connect()
        client.joinRoom(roomCode, username)
    }

    fun approveJoin(userId: String) = client.approveJoin(userId)
    fun rejectJoin(userId: String, reason: String? = null) = client.rejectJoin(userId, reason)
    fun approveSuggestion(suggestionId: String) = client.approveSuggestion(suggestionId)
    fun rejectSuggestion(suggestionId: String, reason: String? = null) = client.rejectSuggestion(suggestionId, reason)
    fun kickUser(userId: String, reason: String? = null) = client.kickUser(userId, reason)

    fun leaveRoom() {
        appContainer.queue.followingHost = false
        client.leaveRoom()
        client.disconnect()
    }
}
