package com.meow.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.meow.music.data.model.Song
import com.meow.music.playback.together.Msg
import com.meow.music.playback.together.NearbySession
import com.meow.music.playback.together.QrCodeImage
import com.meow.music.playback.together.Together
import com.meow.music.playback.together.TogetherState
import com.meow.music.ui.LocalNavigator
import com.meow.music.ui.Routes
import com.meow.music.ui.components.Artwork
import com.meow.music.ui.components.Eyebrow
import com.meow.music.ui.components.LocalFrosted
import com.meow.music.ui.components.SubPage
import com.meow.music.ui.components.featureInk
import com.meow.music.ui.components.featurePane
import com.meow.music.ui.components.pane
import com.meow.music.ui.theme.LocalContainer
import com.meow.music.ui.theme.LocalUi
import kotlinx.coroutines.launch

/**
 * Listen Together (Live Room): host a room on this phone or join one nearby on Wi-Fi.
 * Recreated for instant 1-tap setup, compact QR Code sharing, and zero-friction listening.
 */
@Composable
fun TogetherScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val state by c.together.state.collectAsState()
    SubPage("Live Room", bottomPadding, horizontalPadding = 16.dp, spacing = 18.dp, subtitle = "Instant listening with friends on Same Wi-Fi") {
        when (val s = state) {
            TogetherState.Idle -> item("idle") { Idle() }
            is TogetherState.Joining -> item("joining") { Joining(s.hostName) }
            is TogetherState.Hosting -> item("hosting") { Hosting(s) }
            is TogetherState.Guest -> item("guest") { GuestView(s) }
        }
        item("how") { HowItWorks() }
    }
}

@Composable
private fun Idle() {
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
        // Hero Card: 1-Tap Instant Room Creation
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
                    Text("Instant Live Room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = featureInk())
                    Text(" Open & Join on local Wi-Fi", style = MaterialTheme.typography.labelMedium, color = featureInk().copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Start a room immediately. Friends on your Wi-Fi see your room and join with single tap!",
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
                    Text("Start Live Room")
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

        // Nearby Sessions Group
        Group("Live Rooms nearby") {
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
        scope.launch { error = c.together.join(host, port, code.ifBlank { Msg.OPEN_CODE }, host) }
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
        confirmButton = { TextButton(onClick = { onJoin(address, code) }) { Text("Join") } },
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
    var showQr by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(
            Modifier.fillMaxWidth().featurePane(ui.card).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(if (s.isPrivate) "Private PIN Room" else "⚡ Open Live Room Active", color = ink.copy(alpha = 0.8f))
            if (s.isPrivate) {
                Text(
                    s.code.toCharArray().joinToString(" "), color = ink,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 0.08.em),
                )
            } else {
                Text(
                    "Open Room", color = ink,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Text(
                if (s.isPrivate) "Friends enter this 4-digit PIN to join." else "Friends on your Wi-Fi can join instantly with 1 tap!",
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
                    Text("Scan or tap to join room", style = MaterialTheme.typography.labelLarge)
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
private fun NowPlayingCard(song: Song?, caption: String) {
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxWidth().pane(ui.card, MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song?.thumbnail, Modifier.size(72.dp), ui.art, size = 300)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow(caption)
            Text(song?.title ?: "Nothing playing yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            song?.let { Text(it.artistText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun QueueLine(song: Song) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(song.thumbnail, Modifier.size(40.dp), LocalUi.current.smallArt, size = 120)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HowItWorks() {
    Group("How it works") {
        Note("• Everyone needs Meow on the same Wi-Fi network or hotspot.")
        Note("• Open Rooms allow friends to join instantly with 1 tap.")
        Note("• Direct P2P local connections mean zero server lag and zero data leaks.")
        Spacer(Modifier.height(6.dp))
    }
}
