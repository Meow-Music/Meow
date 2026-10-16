package com.meow.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meow.music.data.internetroom.*
import com.meow.music.data.model.Song
import com.meow.music.playback.together.Msg
import com.meow.music.playback.together.NearbySession
import com.meow.music.playback.together.QrCodeImage
import com.meow.music.playback.together.Together
import com.meow.music.playback.together.TogetherState
import com.meow.music.ui.LocalNavigator
import com.meow.music.ui.Routes
import com.meow.music.ui.components.*
import com.meow.music.ui.theme.LocalContainer
import com.meow.music.ui.theme.LocalUi
import kotlinx.coroutines.launch

/**
 * Unified Live Room: Local Wi-Fi P2P rooms & Internet Cloud sync rooms.
 * Supports same Wi-Fi, different Wi-Fi, and internet servers with zero-friction setup.
 */
@Composable
fun TogetherScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val localState by c.together.state.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Local Wi-Fi, 1: Internet Cloud

    SubPage("Live Room & Sync", bottomPadding, horizontalPadding = 16.dp, spacing = 18.dp, subtitle = "Listen together on Wi-Fi or globally via internet server") {
        item("tabs") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .featurePane(LocalUi.current.card)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Icon(Icons.Rounded.Wifi, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Local Wi-Fi")
                }
                Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Icon(Icons.Rounded.Public, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Internet Cloud")
                }
            }
        }

        if (selectedTab == 0) {
            when (val s = localState) {
                TogetherState.Idle -> item("idle") { LocalIdle() }
                is TogetherState.Joining -> item("joining") { Joining(s.hostName) }
                is TogetherState.Hosting -> item("hosting") { Hosting(s) }
                is TogetherState.Guest -> item("guest") { GuestView(s) }
            }
            item("how") { HowItWorksLocal() }
        } else {
            item("internet_status") { InternetRoomContent() }
            item("internet_how") { HowItWorksInternet() }
        }
    }
}

@Composable
private fun LocalIdle() {
    val c = LocalContainer.current
    val nearby by c.together.nearby.collectAsState()
    val scope = rememberCoroutineScope()
    var joinTarget by remember { mutableStateOf<NearbySession?>(null) }
    var manual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        c.together.startDiscovery()
        onDispose { c.together.stopDiscovery() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .featurePane(LocalUi.current.card)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Same Wi-Fi Live Room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = featureInk())
                    Text("Instant P2P connection on local network", style = MaterialTheme.typography.labelMedium, color = featureInk().copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Start a local room immediately. Friends on your Wi-Fi see your room and join with a single tap!",
                style = MaterialTheme.typography.bodyMedium,
                color = featureInk().copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { c.together.host(isPrivate = false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.WifiTethering, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start Room")
                }
                OutlinedButton(
                    onClick = { c.together.host(isPrivate = true) },
                ) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PIN Room")
                }
            }
        }

        Group("Live Rooms nearby on Wi-Fi") {
            if (nearby.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Scanning Wi-Fi network…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                nearby.forEach { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (n.isPrivate) {
                                    joinTarget = n
                                } else {
                                    scope.launch { error = c.together.joinSession(n) }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (n.isPrivate) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (n.isPrivate) Icons.Rounded.Lock else Icons.Rounded.Groups,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (n.isPrivate) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (n.isPrivate) "Requires 4-digit PIN" else "Open Room • 1-Tap Join",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                if (n.isPrivate) {
                                    joinTarget = n
                                } else {
                                    scope.launch { error = c.together.joinSession(n) }
                                }
                            },
                        ) {
                            Text(if (n.isPrivate) "Enter PIN" else "Join Room")
                        }
                    }
                }
            }
            Item("Manual IP Join", "Join with custom IP address", onClick = { manual = true })
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp)) }
    }

    joinTarget?.let { n ->
        JoinDialog(n.name, askAddress = false, onDismiss = { joinTarget = null }) { _, code ->
            joinTarget = null
            scope.launch { error = c.together.joinSession(n, code) }
        }
    }
    if (manual) JoinDialog("a room", askAddress = true, onDismiss = { manual = false }) { address, code ->
        manual = false
        val host = address.substringBefore(':').trim()
        val port = address.substringAfter(':', "").trim().toIntOrNull() ?: Together.PORT
        if (host.isNotBlank()) {
            scope.launch { error = c.together.join(host, port, code.ifBlank { Msg.OPEN_CODE }, host) }
        }
    }
}

@Composable
private fun InternetRoomContent() {
    val c = LocalContainer.current
    val context = LocalContext.current
    val roomState by c.internetRoom.roomState.collectAsState()
    val connectionState by c.internetRoom.connectionState.collectAsState()
    val role by c.internetRoom.role.collectAsState()
    val settings = c.settings.current

    var username by remember { mutableStateOf(settings.internetRoomUsername.ifBlank { settings.displayName }) }
    var roomCodeInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                                ConnectionState.CONNECTED -> Icons.Rounded.CloudDone
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Icons.Rounded.Sync
                                else -> Icons.Rounded.CloudOff
                            },
                            null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Internet & Cellular Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            try {
                                c.internetRoom.createRoom(username.ifBlank { "Master" })
                                Toast.makeText(context, "Creating room…", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Room")
                    }
                    Button(
                        onClick = {
                            try {
                                if (roomCodeInput.isNotBlank()) {
                                    c.internetRoom.joinRoom(roomCodeInput.trim(), username.ifBlank { "Guest" })
                                    Toast.makeText(context, "Joining room…", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = roomCodeInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(18.dp))
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
                    com.meow.music.data.model.Song(
                        id = t.id,
                        title = t.title,
                        artists = listOf(com.meow.music.data.model.ArtistRef(name = t.artist)),
                        album = t.album?.let { com.meow.music.data.model.AlbumRef(title = it) },
                        durationSec = (t.duration / 1000L).toInt(),
                        thumbnail = t.thumbnail
                    )
                }

                Text("Room Code: ${rs.roomCode}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Role: $role • Users: ${rs.users.size}", style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(8.dp))
                NowPlayingCard(currentSong, if (role == RoomRole.HOST) "Hosting Internet Room" else "Connected to Internet Room")

                if (role == RoomRole.HOST || (role == RoomRole.GUEST && rs.allowParticipantControl)) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { c.internetRoom.control("previous") }) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(30.dp)) }
                        Spacer(Modifier.width(18.dp))
                        Box(
                            Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                .clickable { c.internetRoom.control(if (rs.isPlaying) "pause" else "play") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (rs.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (rs.isPlaying) "Pause" else "Play", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.width(18.dp))
                        IconButton(onClick = { c.internetRoom.control("next") }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(30.dp)) }
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
                        onClick = { c.internetRoom.resync() }, icon = Icons.Rounded.Sync,
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
                    com.meow.music.data.model.Song(
                        id = t.id,
                        title = t.title,
                        artists = listOf(com.meow.music.data.model.ArtistRef(name = t.artist)),
                        album = t.album?.let { com.meow.music.data.model.AlbumRef(title = it) },
                        durationSec = (t.duration / 1000L).toInt(),
                        thumbnail = t.thumbnail
                    )
                }
                if (queueSongs.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Room Queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    queueSongs.take(8).forEach { song -> QueueLine(song) }
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
}

@Composable
private fun JoinDialog(name: String, askAddress: Boolean, onDismiss: () -> Unit, onJoin: (address: String, code: String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (askAddress) OutlinedTextField(
                    address, { address = it.trim() }, label = { Text("Address on host's screen") }, placeholder = { Text("192.168.1.20") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    code, { code = it.filter(Char::isDigit).take(4) }, label = { Text("PIN (if private)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onJoin(address, code) }, enabled = !askAddress || address.isNotBlank()) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Joining(name: String) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(14.dp))
        Text("Joining $name's Room…", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Hosting(s: TogetherState.Hosting) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val song by c.player.currentSong.collectAsState()
    val ui = LocalUi.current
    val ink = featureInk()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showQr by remember { mutableStateOf(false) }

    val fullAddress = remember(s.address, s.port) {
        val ip = s.address ?: "Searching Wi-Fi..."
        if (s.port != Together.PORT && s.address != null) "$ip:${s.port}" else ip
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier.fillMaxWidth().featurePane(ui.card).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(if (s.isPrivate) "🔒 Private PIN Room Active" else "⚡ Open Live Room Active", color = ink.copy(alpha = 0.8f))
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (!s.address.isNullOrBlank()) {
                                    clipboardManager.setText(AnnotatedString(fullAddress))
                                    Toast.makeText(context, "IP Address copied: $fullAddress", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Wifi, null, Modifier.size(15.dp), tint = ink.copy(alpha = 0.8f))
                            Spacer(Modifier.width(4.dp))
                            Text("IP Address", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            fullAddress,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                            maxLines = 1,
                        )
                        Text("Tap to copy", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.6f))
                    }

                    Box(Modifier.width(1.dp).height(38.dp).background(ink.copy(alpha = 0.2f)))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(s.code))
                                Toast.makeText(context, "PIN copied: ${s.code}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (s.isPrivate) Icons.Rounded.Lock else Icons.Rounded.Key, null, Modifier.size(15.dp), tint = ink.copy(alpha = 0.8f))
                            Spacer(Modifier.width(4.dp))
                            Text(if (s.isPrivate) "Room PIN" else "Room Code", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.code,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                        )
                        Text("Tap to copy", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.6f))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (s.isPrivate) "Friends enter this IP and PIN in Manual Join." else "Friends on Wi-Fi join automatically, or enter this IP address.",
                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showQr = true }) {
                Icon(Icons.Rounded.QrCode, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Show QR Code")
            }
        }

        NowPlayingCard(song, if (song == null) "Play something to share with room listeners" else "Live in your room right now")

        Group("Connected Listeners (${s.guests.size})") {
            if (s.guests.isEmpty()) Note("Waiting for friends to join nearby…")
            s.guests.forEach { g -> Item(g, null, onClick = {}, icon = Icons.Rounded.Headphones) }
            Toggle("Allow guest playback controls", "Guests can play, pause, and skip from their phones.", s.guestsControl) { c.together.setGuestsControl(it) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { nav.go(Routes.SEARCH) }, Modifier.weight(1f)) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Find songs")
            }
            OutlinedButton(onClick = { c.together.leave() }, Modifier.weight(1f)) { Text("End Live Room") }
        }
    }

    if (showQr) {
        val qrData = "meow://room?host=${s.address.orEmpty()}&port=${s.port}&code=${s.code}"
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Room QR Code", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    QrCodeImage(data = qrData, modifier = Modifier.size(200.dp))
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("IP: $fullAddress", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("PIN / Code: ${s.code}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Scan with phone camera or Meow to join room", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun GuestView(s: TogetherState.Guest) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val now = s.now
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        NowPlayingCard(now?.songs?.firstOrNull(), "In ${s.hostName}'s Live Room" + (now?.guests?.size?.takeIf { it > 1 }?.let { " · $it listening" } ?: ""))
        if (now?.canControl == true) HostControls(now)
        Group("Playback Settings") {
            Toggle(
                "Sync Audio Here", "Listen to live music playback on this phone in real time.",
                s.listenHere,
            ) { c.together.setListenHere(it) }
            if (s.listenHere && !s.inSync) Item(
                "Out of step", "Tap to resync audio with ${s.hostName}'s stream.",
                onClick = { c.together.resync() }, icon = Icons.Rounded.Sync,
            )
        }
        val upcoming = now?.songs?.drop(1).orEmpty()
        if (upcoming.isNotEmpty()) Group("Room Queue") {
            upcoming.take(8).forEach { song -> QueueLine(song) }
            if (upcoming.size > 8) Note("…and ${upcoming.size - 8} more")
        }
        Note("Tip: Tap ⋯ on any song anywhere in Meow to add it to this Live Room.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { nav.go(Routes.SEARCH) }, Modifier.weight(1f)) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add songs")
            }
            OutlinedButton(onClick = { c.together.leave() }, Modifier.weight(1f)) { Text("Leave Room") }
        }
    }
}

@Composable
private fun HostControls(now: Msg.State) {
    val c = LocalContainer.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { c.together.control("previous") }) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(30.dp)) }
        Spacer(Modifier.width(18.dp))
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .clickable { c.together.control(if (now.playing) "pause" else "play") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (now.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (now.playing) "Pause" else "Play", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(18.dp))
        IconButton(onClick = { c.together.control("next") }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(30.dp)) }
    }
}

@Composable
private fun HowItWorksLocal() {
    Group("How it works (Local Wi-Fi)") {
        Note("• Same Wi-Fi: Everyone needs Meow on the same Wi-Fi network or hotspot.")
        Note("• Direct P2P local connections mean zero server lag and zero data leaks.")
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun HowItWorksInternet() {
    Group("How it works (Internet Cloud)") {
        Note("• Different Wi-Fi / Cellular: Connects globally via secure WebSocket sync servers.")
        Note("• Create or join rooms with a unique room code from anywhere in the world.")
        Spacer(Modifier.height(6.dp))
    }
}
