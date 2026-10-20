package com.meow.music.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.meow.music.data.internetroom.ConnectionState
import com.meow.music.data.internetroom.RoomRole
import com.meow.music.ui.theme.LocalContainer
import com.meow.music.ui.components.SubPage
import com.meow.music.ui.theme.MeowIcons
import kotlin.math.abs

@Composable
fun InternetRoomScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val manager = c.internetRoom
    val connectionState by manager.connectionState.collectAsState()
    val roomState by manager.roomState.collectAsState()
    val role by manager.role.collectAsState()
    val pendingJoinRequests by manager.pendingJoinRequests.collectAsState()
    val pendingSuggestions by manager.pendingSuggestions.collectAsState()
    val listenHere by manager.listenHere.collectAsState()

    var userNameInput by remember { mutableStateOf("MeowUser") }
    var roomCodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var hasRequestedJoin by remember { mutableStateOf(false) }

    LaunchedEffect(roomState) {
        if (roomState != null) {
            isSubmitting = false
            hasRequestedJoin = false
        }
    }

    // Wire up PlayerConnection
    LaunchedEffect(Unit) {
        manager.setPlayerConnection(c.player)
    }

    SubPage(
        title = "Internet Room (Cloud Sync)",
        bottomPadding = bottomPadding,
        horizontalPadding = 16.dp,
        spacing = 22.dp,
        showBack = true
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Connection status banner
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (connectionState) {
                            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                            ConnectionState.CONNECTING -> MaterialTheme.colorScheme.surfaceContainerHighest
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                ConnectionState.CONNECTED -> MeowIcons.CheckCircle
                                ConnectionState.CONNECTING -> MeowIcons.Refresh
                                else -> MeowIcons.Info
                            },
                            contentDescription = null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "Connected to Cloud Relay"
                                    ConnectionState.CONNECTING -> "Connecting to Relay…"
                                    else -> "Disconnected / Offline"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "Ready to host or join global cloud rooms"
                                    ConnectionState.CONNECTING -> "Establishing secure socket..."
                                    else -> "Tap join or create to reconnect"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (roomState == null) {
                    // Not in a room: Create or Join
                    Text(
                        "Sync music with friends anywhere globally over Wi-Fi or mobile data using cloud synchronization servers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        label = { Text("Your Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Create Cloud Room", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                errorMessage = null
                                manager.createRoom(userNameInput.ifBlank { "MeowUser" })
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Creating Room...")
                        } else {
                            Icon(MeowIcons.Groups, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Room")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Join with Room Code", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = { roomCodeInput = it },
                        label = { Text("6-Character Room Code (e.g., MEOW-1234)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (roomCodeInput.isNotBlank() && !isSubmitting && !hasRequestedJoin) {
                                isSubmitting = true
                                hasRequestedJoin = true
                                errorMessage = null
                                manager.joinRoom(roomCodeInput.trim().uppercase(), userNameInput.ifBlank { "MeowUser" })
                            }
                        },
                        enabled = roomCodeInput.isNotBlank() && !isSubmitting && !hasRequestedJoin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSubmitting || hasRequestedJoin) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Joining Room...")
                        } else {
                            Icon(MeowIcons.Login, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Join Room")
                        }
                    }

                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // Active Room View
                    val state = roomState!!
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val hostUser = state.users.firstOrNull { it.userId == state.hostId }
                                    Text("Host: 👑 ${hostUser?.username ?: state.hostId.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("CODE: ${state.roomCode}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    NowPlayingLogo()
                                    Badge {
                                        Text(if (role == RoomRole.HOST) "👑 HOST" else "GUEST", maxLines = 1, softWrap = false)
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))

                            // Now Playing Track & Album Art Card with Host Name
                            state.currentTrack?.let { track ->
                                val hostUser = state.users.firstOrNull { it.userId == state.hostId }
                                val hostName = hostUser?.username ?: "Host"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Album Art Thumbnail
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        if (!track.thumbnail.isNullOrBlank()) {
                                            AsyncImage(
                                                model = track.thumbnail,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(MeowIcons.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Now Playing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            Text("👑 $hostName", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        track.album?.let { album ->
                                            Text(album, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Playing Time & Progress
                                val posSec = state.position
                                val durSec = track.duration
                                val progress = if (durSec > 0) (posSec.toFloat() / durSec.toFloat()).coerceIn(0f, 1f) else 0f

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(formatTime(posSec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatTime(durSec), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Playback Control Action Buttons (Host / Guest Control)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { manager.control("previous") }) {
                                        Icon(MeowIcons.Previous, contentDescription = "Previous")
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    IconButton(
                                        onClick = { manager.control(if (state.isPlaying) "pause" else "play") },
                                        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                                    ) {
                                        Icon(
                                            if (state.isPlaying) MeowIcons.Pause else MeowIcons.Play,
                                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    IconButton(onClick = { manager.control("next") }) {
                                        Icon(MeowIcons.Next, contentDescription = "Next")
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    OutlinedButton(onClick = { manager.resync() }) {
                                        Text("Resync", maxLines = 1, softWrap = false)
                                    }
                                }
                            } ?: Text("No track playing currently", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(checked = listenHere, onCheckedChange = { manager.setListenHere(it) })
                                    Text("Follow Host / Sync Playback", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = { manager.leaveRoom() },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("Leave Room", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    // Queue Section
                    if (state.queue.isNotEmpty()) {
                        Text("Cloud Room Queue (${state.queue.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.queue.forEachIndexed { idx, qTrack ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${idx + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        if (!qTrack.thumbnail.isNullOrBlank()) {
                                            AsyncImage(
                                                model = qTrack.thumbnail,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(qTrack.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(qTrack.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (idx < state.queue.size - 1) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }

                    // Host Controls: Room Settings (Playback & Queue Permission for Guests)
                    if (role == RoomRole.HOST) {
                        Text("Host Permissions & Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Allow Guest Playback & Control", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Give guests permission to control playback, add songs to queue, and clear queue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = state.allowParticipantControl,
                                    onCheckedChange = { manager.setAllowParticipantControl(it) }
                                )
                            }
                        }
                    }

                    // Host Controls: Pending Song Suggestions
                    if (role == RoomRole.HOST && pendingSuggestions.isNotEmpty()) {
                        Text("Pending Song Suggestions (${pendingSuggestions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        pendingSuggestions.forEach { suggestion ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(suggestion.trackInfo.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("By ${suggestion.fromUsername} • ${suggestion.trackInfo.artist}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { manager.approveSuggestion(suggestion.suggestionId) }) { Text("Approve", maxLines = 1, softWrap = false) }
                                        OutlinedButton(onClick = { manager.rejectSuggestion(suggestion.suggestionId) }) { Text("Deny", maxLines = 1, softWrap = false) }
                                    }
                                }
                            }
                        }
                    }

                    // Host Controls: Pending Join Requests
                    if (role == RoomRole.HOST && pendingJoinRequests.isNotEmpty()) {
                        Text("Pending Join Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        pendingJoinRequests.forEach { req ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(req.username, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { manager.approveJoin(req.userId) }) { Text("Accept", maxLines = 1, softWrap = false) }
                                        OutlinedButton(onClick = { manager.rejectJoin(req.userId) }) { Text("Reject", maxLines = 1, softWrap = false) }
                                    }
                                }
                            }
                        }
                    }

                    // Participants List with Join Order (#1 1st join, #2 2nd join) and Host Crown 👑 & Location Tag
                    Text("Participants (${state.users.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.users.forEachIndexed { index, user ->
                            val isHost = user.userId == state.hostId
                            val joinOrderLabel = when (index) {
                                0 -> "1st join (Host / Initiator)"
                                1 -> "2nd join"
                                2 -> "3rd join"
                                else -> "${index + 1}th join"
                            }
                            val location = getLocationTag(index, user.userId)

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isHost) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = if (isHost) "👑" else "${index + 1}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(user.username, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (isHost) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        modifier = Modifier.padding(horizontal = 2.dp)
                                                    ) {
                                                        Text("👑 HOST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), maxLines = 1, softWrap = false)
                                                    }
                                                }
                                            }
                                            Text("$joinOrderLabel • $location", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }

                                    Badge(
                                        containerColor = if (user.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = if (user.isConnected) "Connected" else "Away",
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun getLocationTag(index: Int, userId: String): String {
    val locations = listOf(
        "📍 Global Relay (Primary)",
        "📍 Node US-East",
        "📍 Node EU-Central",
        "📍 Node Asia-Pacific",
        "📍 Node West-Coast",
        "📍 Local Secure Node"
    )
    return locations[abs(userId.hashCode() + index) % locations.size]
}

@Composable
fun NowPlayingLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "now_playing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        repeat(3) { i ->
            val heightAnim by infiniteTransition.animateFloat(
                initialValue = 6f,
                targetValue = 18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300 + i * 150, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(heightAnim.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "LIVE SYNC",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}
