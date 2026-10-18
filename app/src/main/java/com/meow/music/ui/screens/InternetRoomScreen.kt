package com.meow.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meow.music.data.internetroom.*
import com.meow.music.data.model.*
import com.meow.music.ui.components.*
import com.meow.music.ui.theme.LocalContainer
import com.meow.music.ui.theme.LocalUi
import com.meow.music.ui.theme.MeowIcons
import androidx.compose.ui.draw.clip

@Composable
fun InternetRoomScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val roomState by c.internetRoom.roomState.collectAsState()
    val connectionState by c.internetRoom.connectionState.collectAsState()
    val role by c.internetRoom.role.collectAsState()
    val settings = c.settings.current

    var username by remember { mutableStateOf(settings.internetRoomUsername.ifBlank { "User" }) }
    var roomCodeInput by remember { mutableStateOf("") }

    SubPage("Internet Room", bottomPadding, horizontalPadding = 16.dp, spacing = 18.dp, subtitle = "Global cloud & server synchronized listening") {
        item("status") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .featurePane(LocalUi.current.card)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (connectionState) {
                                    ConnectionState.CONNECTED -> MeowIcons.CheckCircle
                                    ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MeowIcons.Sync
                                    else -> MeowIcons.CloudOff
                                },
                                null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Internet Sync Server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> "Connected"
                                ConnectionState.CONNECTING -> "Connecting to server…"
                                ConnectionState.RECONNECTING -> "Reconnecting…"
                                else -> "Disconnected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (roomState == null) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            c.settings.setInternetRoomUsername(it)
                        },
                        label = { Text("Your Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = { roomCodeInput = it.uppercase() },
                        label = { Text("Room Code (to join)") },
                        placeholder = { Text("ABC12345") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                c.internetRoom.createRoom(username.ifBlank { "Master" })
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(MeowIcons.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Room")
                        }
                        Button(
                            onClick = {
                                if (roomCodeInput.isNotBlank()) {
                                    c.internetRoom.joinRoom(roomCodeInput.trim(), username.ifBlank { "Guest" })
                                }
                            },
                            enabled = roomCodeInput.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(MeowIcons.Play, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Join Room")
                        }
                    }
                } else {
                    val rs = roomState!!
                    val pendingRequests by c.internetRoom.pendingJoinRequests.collectAsState()
                    val listenHere by c.internetRoom.listenHere.collectAsState()
                    val inSync by c.internetRoom.inSync.collectAsState()

                    val currentSong = rs.currentTrack?.let { t ->
                        Song(
                            id = t.id,
                            title = t.title,
                            artists = listOf(ArtistRef(name = t.artist)),
                            album = t.album?.let { AlbumRef(title = it) },
                            durationSec = (t.duration / 1000L).toInt(),
                            thumbnail = t.thumbnail
                        )
                    }

                    Text("Room Code: ${rs.roomCode}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Role: $role • Users: ${rs.users.size}", style = MaterialTheme.typography.bodyMedium)

                    if (role == RoomRole.HOST || (role == RoomRole.GUEST && rs.allowParticipantControl)) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { c.internetRoom.control("previous") }) { Icon(MeowIcons.Previous, "Previous", Modifier.size(30.dp)) }
                            Spacer(Modifier.width(18.dp))
                            Box(
                                Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                    .clickable { c.internetRoom.control(if (rs.isPlaying) "pause" else "play") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(if (rs.isPlaying) MeowIcons.Pause else MeowIcons.Play, if (rs.isPlaying) "Pause" else "Play", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.width(18.dp))
                            IconButton(onClick = { c.internetRoom.control("next") }) { Icon(MeowIcons.Next, "Next", Modifier.size(30.dp)) }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Group("Playback Sync") {
                        Toggle(
                            "Sync Audio Here", "Listen to internet room playback on this phone in real time.",
                            listenHere,
                        ) { c.internetRoom.setListenHere(it) }
                        if (listenHere && !inSync) Item(
                            "Out of step", "Tap to resync audio with room stream.",
                            onClick = { c.internetRoom.resync() }, icon = MeowIcons.Sync,
                        )
                    }

                    if (role == RoomRole.HOST) {
                        val pendingSuggestions by c.internetRoom.pendingSuggestions.collectAsState()

                        Spacer(Modifier.height(8.dp))
                        Toggle(
                            "Allow guest playback controls", "Guests can play, pause, and skip from their phones.",
                            rs.allowParticipantControl,
                        ) { c.internetRoom.setAllowParticipantControl(it) }

                        if (pendingSuggestions.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Pending Song Suggestions (${pendingSuggestions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            pendingSuggestions.forEach { sug ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(sug.trackInfo.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Suggested by ${sug.fromUsername}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(
                                        onClick = { c.internetRoom.approveSuggestion(sug.suggestionId) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Approve")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { c.internetRoom.rejectSuggestion(sug.suggestionId) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Reject")
                                    }
                                }
                            }
                        }

                        if (pendingRequests.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Pending Join Requests (${pendingRequests.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            pendingRequests.forEach { req ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(req.username, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = { c.internetRoom.approveJoin(req.userId) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Approve")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { c.internetRoom.rejectJoin(req.userId) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Reject")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Connected Users (${rs.users.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    rs.users.forEach { user ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(user.username + if (user.isHost) " (Host)" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (user.isConnected) "Connected" else "Disconnected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (role == RoomRole.HOST && !user.isHost) {
                                OutlinedButton(
                                    onClick = { c.internetRoom.kickUser(user.userId, "Kicked by host") },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Kick")
                                }
                            }
                        }
                    }

                    val queueSongs = rs.queue.map { t ->
                        Song(
                            id = t.id,
                            title = t.title,
                            artists = listOf(ArtistRef(name = t.artist)),
                            album = t.album?.let { AlbumRef(title = it) },
                            durationSec = (t.duration / 1000L).toInt(),
                            thumbnail = t.thumbnail
                        )
                    }
                    if (queueSongs.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Room Queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        queueSongs.take(8).forEach { song ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { c.internetRoom.leaveRoom() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Leave / End Internet Room")
                    }
                }
            }
        }

        item("info") {
            Group("About Internet Room") {
                Note("• Connects over the internet via WebSockets to synchronize music globally.")
                Note("• Create a room to host friends anywhere in the world with a unique code.")
            }
        }
    }
}
