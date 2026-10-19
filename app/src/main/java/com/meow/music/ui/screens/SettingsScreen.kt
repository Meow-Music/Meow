package com.meow.music.ui.screens

import com.meow.music.ui.theme.MeowIcons
import com.meow.music.R
import com.meow.music.ui.theme.AppIcon
import com.meow.music.ui.theme.AppIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import android.os.Build
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import com.meow.music.data.local.LocalLossless
import com.meow.music.data.prefs.AppSettings
import com.meow.music.data.prefs.AudioQuality
import com.meow.music.data.prefs.CarLyricsStyle
import com.meow.music.data.prefs.CardSize
import com.meow.music.data.prefs.ColorSource
import com.meow.music.data.prefs.Corners
import com.meow.music.data.prefs.CoverSize
import com.meow.music.data.prefs.FontChoice
import com.meow.music.data.prefs.GlassKind
import com.meow.music.data.prefs.LibraryView
import com.meow.music.data.prefs.LyricsAlign
import com.meow.music.data.prefs.MiniPlayerStyle
import com.meow.music.data.prefs.MiniProgress
import com.meow.music.data.prefs.NavBarStyle
import com.meow.music.data.prefs.NavLabels
import com.meow.music.data.prefs.NavTab
import com.meow.music.data.prefs.PlayerBackground
import com.meow.music.data.prefs.ScrubberStyle
import com.meow.music.data.prefs.ThemeMode
import com.meow.music.data.prefs.TitleSize
import com.meow.music.data.prefs.TransportStyle
import com.meow.music.data.prefs.UiDensity
import com.meow.music.download.SMART_DOWNLOAD_SIZES
import com.meow.music.download.SmartDownloadWorker
import com.meow.music.download.formatGb
import com.meow.music.download.smartSongEstimate
import com.meow.music.ui.LocalNavigator
import com.meow.music.ui.Routes
import com.meow.music.ui.components.MeowChip
import com.meow.music.ui.components.SubPage
import com.meow.music.ui.components.featurePane
import com.meow.music.ui.theme.AccentSwatches
import com.meow.music.ui.theme.isNeutralSeed
import com.meow.music.ui.theme.AppFonts
import com.meow.music.ui.theme.LocalAppSettings
import com.meow.music.ui.theme.LocalContainer
import com.meow.music.ui.theme.LocalUi
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The settings categories, in the order they're listed; each opens its own page. */
enum class SettingsSection(val key: String, val title: String, val icon: ImageVector) {
    APPEARANCE("appearance", "Appearance", MeowIcons.Palette),
    INTERFACE("interface", "Layout & navigation", MeowIcons.Customize),
    PLAYER("player", "Player", MeowIcons.PlayCircle),
    SOUND("sound", "Playback & sound", MeowIcons.Equalizer),
    LYRICS("lyrics", "Lyrics", MeowIcons.Lyrics),
    DOWNLOADS("downloads", "Downloads & storage", MeowIcons.DownloadCircle),
    HOME("home", "Home screen", MeowIcons.Home),
    LIBRARY("library", "Library & Replay", MeowIcons.Library),
    LISTEN_TOGETHER("together", "Listen Together", MeowIcons.Groups),
    DISCORD("discord", "Discord Presence", MeowIcons.Share),
    ABOUT("about", "About", MeowIcons.Info),
    /** Not on the hub: opened from About. */
    LICENSES("licenses", "Open-source licenses", MeowIcons.Info);

    fun summary(s: AppSettings): String = when (this) {
        APPEARANCE -> listOf(s.themeMode.label + " theme", s.fontChoice.label + " font", when { s.liquidGlass -> "Liquid Glass"; s.frostedGlass -> "Frosted Glass"; else -> "Solid bars" }).joinToString(" · ")
        INTERFACE -> listOf(s.navStyle.label + " bar", "${s.navTabs.size} tabs", s.miniPlayerStyle.label + " mini player").joinToString(" · ")
        PLAYER -> listOfNotNull(s.playerBackground.label, if (s.immersivePlayer) "Immersive" else if (s.fullBleedArtwork) "Full-bleed artwork" else "Framed artwork", if (s.canvasEnabled) "Animated covers" else null).joinToString(" · ")
        SOUND -> listOfNotNull(if (s.lossless) "Lossless" else s.audioQuality.label + " quality", if (s.mix) "Crossfade" else null, if (s.normalize) "Normalized" else null, if (s.spatial) "Meow Spatial" else null, if (s.eq.enabled) "EQ ${s.eq.preset}" else null).joinToString(" · ")
        LYRICS -> "${s.lyricsOrder.first().label} first · ${if (s.preferSynced) "karaoke preferred" else "any lyrics"}"
        DOWNLOADS -> listOfNotNull("Smart Downloads ${formatGb(s.offlineGb)}", if (s.downloadWifiOnly) "Wi-Fi only" else "Any network").joinToString(" · ")
        HOME -> "Sections, order and styles" + if (s.contentCountry.isNotBlank()) " · ${countryName(s.contentCountry)}" else ""
        LIBRARY -> "${s.libraryView.label} view · ${if (s.replayHours) "hours" else "minutes"}"
        LISTEN_TOGETHER -> "Live Room (Same Wi-Fi) · Internet Room (Global Sync)"
        DISCORD -> "Rich Presence · ${if (s.discordPresence) "enabled" else "disabled"}"
        ABOUT -> "Meow ${com.meow.music.BuildConfig.VERSION_NAME} · backup, updates"
        LICENSES -> "GPL-3.0-or-later"
    }

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
}

private val hubGroups = listOf(
    "Look & feel" to listOf(SettingsSection.APPEARANCE, SettingsSection.INTERFACE, SettingsSection.PLAYER),
    "Listening" to listOf(SettingsSection.SOUND, SettingsSection.LYRICS, SettingsSection.DOWNLOADS, SettingsSection.LISTEN_TOGETHER),
    "Your Meow" to listOf(SettingsSection.HOME, SettingsSection.LIBRARY),
    "" to listOf(SettingsSection.ABOUT),
)

/** Settings: the account, then the categories; [section] shows one category's page. */
@Composable
fun SettingsScreen(bottomPadding: Dp, section: SettingsSection? = null) {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val nav = LocalNavigator.current
    // As a tab, the hub is a root page and has nothing to go back to.
    val isTab = section == null && NavTab.SETTINGS in s.navTabs

    SubPage(
        title = section?.title ?: "Settings",
        bottomPadding = bottomPadding,
        horizontalPadding = 16.dp,
        spacing = 22.dp,
        showBack = !isTab,
    ) {
        when (section) {
            null -> {
                item { AccountCard() }
                hubGroups.forEach { (title, sections) ->
                    val filteredSections = if (title == "Listening") {
                        if (s.showDiscord) sections + SettingsSection.DISCORD else sections
                    } else {
                        sections
                    }
                    if (filteredSections.isNotEmpty()) {
                        item {
                            Group(title) {
                                filteredSections.forEach { sec -> NavRow(sec.icon, sec.title, sec.summary(s)) { nav.go(Routes.settings(sec.key)) } }
                            }
                        }
                    }
                }
            }
            SettingsSection.APPEARANCE -> appearance()
            SettingsSection.INTERFACE -> layoutAndNavigation()
            SettingsSection.PLAYER -> item { PlayerSettings() }
            SettingsSection.SOUND -> item { SoundPage() }
            SettingsSection.LYRICS -> item { LyricsSettings() }
            SettingsSection.DISCORD -> item { DiscordGroup() }
            SettingsSection.DOWNLOADS -> item { DownloadSettings() }
            SettingsSection.HOME -> item {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Group("Home") {
                        Item("Customize Home", "Reorder, hide and restyle Home's sections, shortcuts and greeting", onClick = { nav.go(Routes.CATALOGUE) }) { Chevron() }
                        Toggle("Add new sections automatically", "New shelves from YouTube Music show up on Home as they appear", s.autoAddSections) { c.settings.setAutoAddSections(it) }
                        Toggle("Podcasts button", "The round Podcasts button at the top of Home. Off, Podcasts opens from Library → Playlists", s.homePodcastsButton) { c.settings.setHomePodcastsButton(it) }
                        Toggle("Customize button at the top", "The round button at the top right of Home", s.homeCustomizeTopButton) { c.settings.setHomeCustomizeTopButton(it) }
                        Toggle("Customize button at the bottom", "The \"Customize Home\" button at the very end of Home", s.homeCustomizeButton) { c.settings.setHomeCustomizeButton(it) }
                    }
                    ContentCountrySetting()
                }
            }
            SettingsSection.LIBRARY -> item { LibraryReplaySettings() }
            SettingsSection.LISTEN_TOGETHER -> item { ListenTogetherSettings() }
            SettingsSection.ABOUT -> item {
                var showDevTeam by remember { mutableStateOf(false) }
                androidx.activity.compose.BackHandler(enabled = showDevTeam) {
                    showDevTeam = false
                }

                if (showDevTeam) {
                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDevTeam = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(MeowIcons.ArrowBack, contentDescription = "Back to About", tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "← Back to About",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        DevelopmentTeamPage()
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        DevelopmentTeamCompactCard(onClick = { showDevTeam = true })
                        BackupGroup()
                        Group("About") {
                            CheckForUpdates()
                        }
                        ContributorsGroup()
                    }
                }
            }
            SettingsSection.LICENSES -> item { LicensesPage() }
        }
    }
}

@Composable
private fun ListenTogetherSettings() {
    val nav = LocalNavigator.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group("Live Room (Same Wi-Fi)") {
            Item(
                title = "Live Room (Local Network)",
                subtitle = "Sync playback with friends on the same local Wi-Fi network. Zero delay, zero server.",
                onClick = { nav.go(Routes.TOGETHER) },
                icon = MeowIcons.Groups
            ) { Chevron() }
            Note("Recommended when everyone is in the same room or connected to the same Wi-Fi router.")
        }

        Group("Internet Room (Global Cloud Sync)") {
            Item(
                title = "Internet Room (Any Network / Far Away)",
                subtitle = "Sync music with friends anywhere globally using cloud sync servers over Wi-Fi or mobile data.",
                onClick = { nav.go(Routes.INTERNET_ROOM) },
                icon = MeowIcons.CheckCircle
            ) { Chevron() }
            Note("Recommended when friends are far away or on mobile data. Join with a 6-character room code.")
        }
    }
}

private data class ContactLink(
    val label: String,
    val url: String,
    val drawableRes: Int
)

@Composable
private fun DevelopmentTeamCompactCard(onClick: () -> Unit) {
    Group("Development Team") {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.dev_lead_dev),
                        contentDescription = "Rexx",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rexx & Meow Team",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Lead Architect & Creators · Tap to view team",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Chevron()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevelopmentTeamPage() {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group("Lead Developer") {
            BigBannerDeveloperCard(
                name = "Rexx",
                title = "Lead Architect & Core Engine Master",
                badge = "Lead Dev",
                bio = "Maintains 99.9% uptime powered purely by caffeine and cat memes. Converts raw Kotlin code into silky smooth audio frequencies while debugging in his sleep.",
                imageRes = R.drawable.dev_lead_dev,
                links = listOf(
                    ContactLink("GitHub", "https://github.com/Meow-Music/Meow", R.drawable.ic_github),
                    ContactLink("Messenger", "https://m.me/rexofhell", R.drawable.ic_messenger),
                    ContactLink("Telegram", "https://t.me/rex_mahakaal", R.drawable.ic_telegram)
                ),
                onOpen = { url -> runCatching { uriHandler.openUri(url) } }
            )
        }

        Group("Core Team & Alchemists") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DeveloperSectionCard(
                    name = "Rozan",
                    title = "UI/UX Alchemist & Design Specialist",
                    bio = "Measures margins in pixels and perfection in alignment. Will re-color a button 17 times until the cat approves the precise shade of pink.",
                    imageRes = R.drawable.dev_ui_rozan,
                    imageAlignment = Alignment.TopCenter,
                    links = listOf(
                        ContactLink("Messenger", "https://m.me/rojan.lama.9", R.drawable.ic_messenger),
                        ContactLink("WhatsApp", "https://wa.me/971564506103", R.drawable.ic_whatsapp)
                    ),
                    onOpen = { url -> runCatching { uriHandler.openUri(url) } }
                )

                DeveloperSectionCard(
                    name = "Laxmee",
                    title = "Feature Architect & Harmony Enforcer",
                    bio = "The true brain behind the operation who keeps the team focused, fixes edge-case bugs before anyone notices, and makes sure the app actually works on real phones.",
                    imageRes = R.drawable.dev_mrs_dev,
                    links = listOf(
                        ContactLink("TikTok", "https://tiktok.com/@laxmeee11", R.drawable.ic_tiktok)
                    ),
                    onOpen = { url -> runCatching { uriHandler.openUri(url) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperSectionCard(
    name: String,
    title: String,
    bio: String,
    imageRes: Int,
    imageAlignment: Alignment = Alignment.Center,
    links: List<ContactLink>,
    onOpen: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        alignment = imageAlignment,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (links.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    links.forEach { link ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onOpen(link.url) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = link.drawableRes),
                                    contentDescription = link.label,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = link.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BigBannerDeveloperCard(
    name: String,
    title: String,
    badge: String,
    bio: String,
    imageRes: Int,
    imageAlignment: Alignment = Alignment.Center,
    links: List<ContactLink>,
    onOpen: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    alignment = imageAlignment,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                startY = 60f
                            )
                        )
                )
                
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                if (links.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        links.forEach { link ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.clickable { onOpen(link.url) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = link.drawableRes),
                                        contentDescription = link.label,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = link.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
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

private fun countryName(code: String): String =
    java.util.Locale("", code).displayCountry.takeIf { it.isNotBlank() && it != code } ?: code

/**
 * The country YouTube Music is asked as. Where it isn't offered, picking one where it is (the US, say) brings
 * Home, charts and search back; songs then play as long as YouTube itself is available there.
 */
@Composable
private fun ContentCountrySetting() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    var picking by remember { mutableStateOf(false) }
    val phone = java.util.Locale.getDefault().country
    Group("Region") {
        Item(
            "Content country",
            if (s.contentCountry.isBlank()) "Automatic (${if (phone.isBlank()) "United States" else countryName(phone)})" else countryName(s.contentCountry),
            onClick = { picking = true },
        ) { Chevron() }
        Note(
            "Home, charts, new releases and search follow this country. If YouTube Music isn't available where you live, " +
                "pick a country where it is. Restart Meow for playback to follow too. Songs blocked in your country by their " +
                "label still won't play: that's decided by your connection, not this setting."
        )
    }
    if (picking) {
        val countries = remember {
            java.util.Locale.getISOCountries().map { it to countryName(it) }.sortedBy { it.second }
        }
        val pick: (String) -> Unit = { code -> c.settings.setContentCountry(code); picking = false }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Content country") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(420.dp)) {
                    item { CountryRow("Automatic (follow the phone)", s.contentCountry.isBlank()) { pick("") } }
                    item { CountryRow("United States", s.contentCountry == "US") { pick("US") } }
                    items(countries.size) { i ->
                        val (code, name) = countries[i]
                        CountryRow(name, s.contentCountry == code) { pick(code) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CountryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (selected) Icon(MeowIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Meow also checks on its own from Home; tapping this asks GitHub right now. */
@Composable
private fun CheckForUpdates() {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Item(
        "Check for updates",
        when {
            checking -> "Asking GitHub…"
            result != null -> result
            else -> "Meow looks for new versions on its own and says so on Home"
        },
        onClick = {
            if (!checking) scope.launch {
                checking = true
                result = runCatching { c.updates.checkNow() }.fold(
                    { v -> if (v != null) "Meow $v is out · install it from the banner on Home" else "You're up to date" },
                    { "Couldn't reach GitHub · try again in a moment" },
                )
                checking = false
            }
        },
    ) {
        if (checking) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

private data class Contributor(
    val name: String,
    val link: String,
    val drawableRes: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContributorsGroup() {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val contributors = remember {
        runCatching {
            context.assets.open("contributors/contributors.txt").use { stream ->
                stream.readBytes().decodeToString()
            }
        }.getOrNull()?.lines()?.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            val name = parts.getOrNull(0)?.trim() ?: return@mapNotNull null
            val rawLink = parts.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: "https://github.com/Meow-Music/Meow"
            val link = if (rawLink.startsWith("http://") || rawLink.startsWith("https://")) rawLink else "https://$rawLink"
            val drawableRes = when (name.lowercase()) {
                "zilon" -> R.drawable.zilon
                else -> R.drawable.dev_lead_dev
            }
            Contributor(name, link, drawableRes)
        }?.takeIf { it.isNotEmpty() } ?: listOf(
            Contributor("Zilon", "https://m.me/zilontamang.tamang.3", R.drawable.zilon)
        )
    }

    Group("Contributors") {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Special thanks to our amazing contributors:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                contributors.forEach { contributor ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { runCatching { uriHandler.openUri(contributor.link) } }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = contributor.drawableRes),
                                contentDescription = contributor.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = contributor.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chevron() = Icon(MeowIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

// ---------------------------------------------------------------------------- Account

@Composable
private fun AccountCard() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val nav = LocalNavigator.current
    val ui = LocalUi.current
    val scheme = MaterialTheme.colorScheme
    // A pane of frosted glass on a frosted page, the accent's gradient otherwise.
    val frosted = com.meow.music.ui.components.LocalFrosted.current
    val ink = com.meow.music.ui.components.featureInk()
    val scope = rememberCoroutineScope()
    var switching by remember { mutableStateOf(false) }
    if (switching) AccountSwitcher { switching = false }
    Column(Modifier.fillMaxWidth().featurePane(ui.card)) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (s.isLoggedIn) Modifier else Modifier.clickable { nav.go(Routes.LOGIN) })
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (frosted) com.meow.music.ui.components.frostFill(1.6f) else scheme.surface.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            if (s.isLoggedIn && s.accountAvatar.isNotBlank()) AsyncImage(s.accountAvatar, null, Modifier.fillMaxSize())
            else Icon(if (s.isLoggedIn) MeowIcons.Person else MeowIcons.Login, null, tint = if (frosted) scheme.primary else ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (s.isLoggedIn) s.accountName.ifBlank { "YouTube Music" } else "Sign in to YouTube Music",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (s.isLoggedIn) s.accountEmail.ifBlank { "Signed in" } else "Sync likes, playlists, history and recommendations",
                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (s.isLoggedIn) OutlinedButton(onClick = {
            scope.launch {
                c.settings.logout()
                // The account's library kept for offline goes with it.
                c.library.forgetAccount()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }) { Icon(MeowIcons.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Sign out") }
        else Icon(MeowIcons.ChevronRight, null, tint = ink)
    }
    // Another channel of the same Google account (brand accounts), or another Google account altogether.
    if (s.isLoggedIn) Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
        TextButton(onClick = { switching = true }) {
            Icon(MeowIcons.Person, null, Modifier.size(18.dp), tint = ink); Spacer(Modifier.width(6.dp)); Text("Switch account", color = ink)
        }
        TextButton(onClick = { nav.go(Routes.LOGIN_ADD) }) {
            Icon(MeowIcons.Add, null, Modifier.size(18.dp), tint = ink); Spacer(Modifier.width(6.dp)); Text("Add a Google account", color = ink)
        }
    }
    }
}

/**
 * Discord presence: "Listening to Meow" with the song on the listener's Discord profile. Turning it on the first time
 * signs in to Discord (on Discord's own page); the session stays on this phone.
 */
@Composable
private fun DiscordGroup() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val nav = LocalNavigator.current
    val state by c.discord.state.collectAsState()
    val user by c.discord.userName.collectAsState()

    var showActivityDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    Group("Discord Rich Presence") {
        Toggle(
            "Show what you're playing",
            when {
                !c.discord.signedIn -> "\"Listening to Meow\" on your Discord profile, with the song and how far in. You sign in to Discord using official OAuth."
                state == com.meow.music.discord.DiscordPresence.State.CONNECTED -> "Connected as ${user ?: "you"}"
                state == com.meow.music.discord.DiscordPresence.State.CONNECTING -> "Connecting to Discord…"
                else -> "Signed in as ${user ?: "you"}"
            },
            s.discordPresence && c.discord.signedIn,
        ) { on ->
            if (on && !c.discord.signedIn) nav.go(Routes.DISCORD_LOGIN) else prefs.setDiscordPresence(on)
        }

        if (s.discordPresence && c.discord.signedIn) {
            Toggle(
                "Show timestamps",
                "Show elapsed time and song duration in rich presence",
                s.discordShowTimestamps,
            ) { prefs.setDiscordShowTimestamps(it) }

            Toggle(
                "Show action buttons",
                "Show 'Listen on Meow' button on your rich presence",
                s.discordShowButtons,
            ) { prefs.setDiscordShowButtons(it) }

            Item(
                "Activity type",
                s.discordActivityType.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { showActivityDialog = true },
            )

            Item(
                "Online status",
                s.discordOnlineStatus.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { showStatusDialog = true },
            )
        }

        if (c.discord.signedIn) {
            Item("Sign out of Discord", "Forgets the Discord sign-in on this device", onClick = {
                prefs.setDiscordPresence(false)
                c.discord.signOut()
            })
        }

        Item(
            "About Discord Integration",
            "Meow uses official Discord OAuth and Gateway Rich Presence to broadcast what you're playing. Your credentials remain private on this device.",
            onClick = {},
        )
    }

    if (showActivityDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showActivityDialog = false },
            title = { Text("Select activity type") },
            text = {
                Column {
                    listOf("LISTENING", "PLAYING", "STREAMING", "WATCHING", "COMPETING").forEach { type ->
                        TextButton(
                            onClick = {
                                prefs.setDiscordActivityType(type)
                                showActivityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(type.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActivityDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showStatusDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Select online status") },
            text = {
                Column {
                    listOf("ONLINE", "IDLE", "DND", "INVISIBLE").forEach { status ->
                        TextButton(
                            onClick = {
                                prefs.setDiscordOnlineStatus(status)
                                showStatusDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(status.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Every channel Meow can use on this sign-in: each Google account's own and its brand accounts (one email can have
 * several YouTube channels). Picking one switches the library, likes and recommendations to it.
 */
@Composable
private fun AccountSwitcher(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val accounts by androidx.compose.runtime.produceState<List<com.meow.music.data.model.YtAccount>?>(null) {
        value = runCatching { c.ytm.accounts() }.getOrDefault(emptyList())
    }
    com.meow.music.ui.components.ActionSheet(s.accountAvatar.ifBlank { null }, onDismiss) { close ->
        Text(
            "Accounts and channels", Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        val list = accounts
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
            list.isEmpty() -> Text(
                "YouTube didn't list any other channels. Sign out and in again if you've just made one.",
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium,
            )
            else -> list.groupBy { it.email }.forEach { (email, channels) ->
                if (email != null) Text(
                    email, Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                channels.forEach { a ->
                    val current = a.authUser == s.authUser && a.dataSyncId.substringBefore("||") == s.dataSyncId.substringBefore("||")
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !current) {
                            close {
                                c.scope.launch {
                                    c.library.switchAccount(a)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Switched to ${a.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }.padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (a.avatar != null) AsyncImage(a.avatar, null, Modifier.fillMaxSize()) else Icon(MeowIcons.Person, null)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            a.byline?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        }
                        if (current) Icon(MeowIcons.Check, "In use", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------- Appearance

private fun androidx.compose.foundation.lazy.LazyListScope.appearance() {
    item(key = "discord") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Discord Presence") {
            Toggle(
                "Enable Discord integration",
                "Show Discord Rich Presence settings in the settings menu",
                s.showDiscord,
            ) { prefs.setShowDiscord(it) }
        }
    }
    item(key = "theme") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Theme") {
            Chips(ThemeMode.entries, s.themeMode, { it.label }) { prefs.setThemeMode(it) }
            Toggle("Pure black", "AMOLED-friendly dark theme", s.pureBlack) { prefs.setPureBlack(it) }
        }
    }
    item(key = "colour") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Colour") {
            Chips(ColorSource.entries.filter { it != ColorSource.DYNAMIC || Build.VERSION.SDK_INT >= 31 }, s.colorSource, { it.label }) { prefs.setColorSource(it) }
            if (s.colorSource != ColorSource.DYNAMIC) {
                Note(
                    if (s.colorSource == ColorSource.ARTWORK) "Adapts to the cover of what's playing; this accent is used when nothing is."
                    else "Pick an accent colour"
                )
                AccentPicker(s.accent) { prefs.setAccent(it) }
            }
        }
    }
    item(key = "type") { TypeSettings() }
    item(key = "icons") { IconSettings() }
    item(key = "app-icon") { AppIconSettings() }
    item(key = "shape") { ShapeSettings() }
    item(key = "backgrounds") {
        val s = LocalAppSettings.current
        val nav = LocalNavigator.current
        Group("Backgrounds") {
            val count = s.backdrops.size
            Item(
                "Screen backgrounds",
                if (count == 0) "Ready-made designs or your own photo for Home, Search, Library and more"
                else s.backdrops.values.map { com.meow.music.ui.theme.Backdrop.parse(it).first.label }.distinct().joinToString(" · ") + " on $count ${if (count == 1) "screen" else "screens"}",
                onClick = { nav.go(Routes.BACKGROUNDS) },
            ) { Chevron() }
        }
    }
    item(key = "glass") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Glass") {
            Toggle("Liquid Glass", "Refractive glass for the bars, mini player and player controls", s.liquidGlass) { prefs.setLiquidGlass(it) }
            if (s.liquidGlass) {
                Label("Glass style")
                Chips(GlassKind.entries, s.glassKind, { it.label }) { prefs.setGlassKind(it) }
            }
            Toggle("Frosted Glass", "Soft blurred glass instead, without the refraction", s.frostedGlass) { prefs.setFrostedGlass(it) }
            Toggle("Frosted pages", "Artist, album and playlist pages sit on frosted glass made from their picture, and take its colour; Home, Library, Search and Settings on frost from what's playing", s.frostedPages) { prefs.setFrostedPages(it) }
        }
    }
    item(key = "motion") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Motion") {
            Toggle("Reduce motion", "Calmer animations: no drifting backgrounds, no artwork bounce, quicker transitions", s.reduceMotion) { prefs.setReduceMotion(it) }
        }
    }
    item(key = "reset") { ResetLook() }
}

@Composable
private fun TypeSettings() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    // A font file from the phone (#12), copied into Meow: for the whole app, or only for the lyrics.
    fun importer(slot: AppFonts.FontSlot, done: (String) -> Unit) = { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { AppFonts.import(context, uri, slot) } }
            r.onSuccess { error = null; done(it) }.onFailure { error = it.message ?: "That font couldn't be used" }
        }
        Unit
    }
    val fontTypes = arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype", "application/octet-stream")
    val pickApp = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        importer(AppFonts.FontSlot.APP) { name -> prefs.setAppFont(name); prefs.setFontChoice(FontChoice.CUSTOM) })
    val pickLyrics = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        importer(AppFonts.FontSlot.LYRICS) { name -> prefs.setLyricsFontFile(name); prefs.setLyricsFont(FontChoice.CUSTOM.name) })
    val hasAppFont = remember(s.fontStamp) { AppFonts.customFile(AppFonts.FontSlot.APP) != null }
    val hasLyricsFont = remember(s.fontStamp) { AppFonts.customFile(AppFonts.FontSlot.LYRICS) != null }
    Group("Text") {
        Label("Font")
        FontPicker(s.fontChoice, customName = s.appFontName.takeIf { hasAppFont }, stamp = s.fontStamp) { f ->
            if (f == FontChoice.CUSTOM && !hasAppFont) pickApp.launch(fontTypes) else prefs.setFontChoice(f)
        }
        Item(
            if (hasAppFont) "Your font: ${s.appFontName}" else "Use a font from your phone",
            if (hasAppFont) "Tap to pick a different .ttf or .otf file" else "Pick a .ttf or .otf file (Inter, SF Pro…). Meow keeps its own copy",
            onClick = { pickApp.launch(fontTypes) },
        ) { Chevron() }
        if (hasAppFont) Item("Remove your font", "Back to a built-in font", onClick = {
            AppFonts.remove(AppFonts.FontSlot.APP)
            if (s.fontChoice == FontChoice.CUSTOM) prefs.setFontChoice(FontChoice.CONDENSED)
            prefs.setAppFont("")
        })
        Label("Lyrics font")
        // "App font" keeps each lyric view's own face (the app's); the rest are the same fonts as above, or a file just for lyrics.
        val lyricOptions = listOf("") + FontChoice.entries.filter { it != FontChoice.CUSTOM && AppFonts.available(it) }.map { it.name } + FontChoice.CUSTOM.name
        Chips(lyricOptions, s.lyricsFont, { v ->
            when (v) {
                "" -> "App font"
                FontChoice.CUSTOM.name -> if (hasLyricsFont) s.lyricsFontName.ifBlank { "Your font" } else "From a file…"
                else -> FontChoice.valueOf(v).label
            }
        }) { v -> if (v == FontChoice.CUSTOM.name && !hasLyricsFont) pickLyrics.launch(fontTypes) else prefs.setLyricsFont(v) }
        if (hasLyricsFont) {
            Item("Lyrics font file: ${s.lyricsFontName}", "Tap to pick a different file", onClick = { pickLyrics.launch(fontTypes) }) { Chevron() }
            Item("Remove the lyrics font file", null, onClick = {
                AppFonts.remove(AppFonts.FontSlot.LYRICS)
                if (s.lyricsFont == FontChoice.CUSTOM.name) prefs.setLyricsFont("")
                prefs.setLyricsFontFile("")
            })
        }
        error?.let { Note(it) }
        SliderSetting("Text size", s.textScale, 0.85f..1.25f, { "${(it * 100).roundToInt()}%" }, steps = 7) { prefs.setTextScale(it) }
        Note("On top of your phone's own text size.")
    }
}

/**
 * Icon weight and ends (#19). Meow's icons are built as it starts, so a change is saved for the next start and a
 * Restart button appears; [com.meow.music.ui.theme.IconStyle] holds what's drawn now.
 */
@Composable
private fun IconSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawn = com.meow.music.ui.theme.IconStyle.weight to com.meow.music.ui.theme.IconStyle.rounded
    var saved by remember { mutableStateOf(com.meow.music.ui.theme.IconStyle.saved(context)) }
    fun save(v: Pair<com.meow.music.ui.theme.IconWeight, Boolean>) {
        saved = v
        com.meow.music.ui.theme.IconStyle.save(context, v.first, v.second)
    }
    var pack by remember { mutableStateOf(com.meow.music.ui.theme.IconStyle.savedPack(context)) }
    Group("Icons") {
        Label("Pack")
        Chips(com.meow.music.ui.theme.IconPack.entries, pack, { it.label }) {
            pack = it
            com.meow.music.ui.theme.IconStyle.savePack(context, it)
        }
        Note(pack.detail)
        Label("Weight")
        Chips(com.meow.music.ui.theme.IconWeight.entries, saved.first, { it.label }) { save(it to saved.second) }
        Toggle("Rounded", "Round line ends and softer corners. Off, icons have square ends and sharper corners", saved.second) { save(saved.first to it) }
        if (saved != drawn || pack != com.meow.music.ui.theme.IconStyle.pack) {
            Item("Restart Meow to see them", "Icons are drawn as Meow starts. Playback stops for a moment", onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launch != null) {
                    context.startActivity(android.content.Intent.makeRestartActivityTask(launch.component).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    Runtime.getRuntime().exit(0)
                }
            }) { Chevron() }
        }
    }
}

/** The launcher icon (#20), each shown as its own two layers in a rounded square. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIconSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var current by remember { mutableStateOf(com.meow.music.ui.theme.AppIcons.current(context)) }
    Group("App icon") {
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.meow.music.ui.theme.AppIcon.entries.forEach { icon ->
                val on = icon == current
                Column(
                    Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable {
                        if (!on) { com.meow.music.ui.theme.AppIcons.set(context, icon); current = icon }
                    }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(60.dp).border(if (on) 3.dp else 0.dp, if (on) scheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                            .padding(if (on) 4.dp else 0.dp).clip(RoundedCornerShape(15.dp)),
                    ) {
                        Image(painterResource(icon.iconResId), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(icon.label, style = MaterialTheme.typography.labelMedium, color = if (on) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Note("Your launcher can take a few seconds to show the new icon. An icon on the home screen may need adding again.")
    }
}

/** Each font's name, set in that font. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontPicker(current: FontChoice, customName: String?, stamp: Long, onPick: (FontChoice) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ui = LocalUi.current
    // "Your font" is always pickable: without a file yet, picking it asks for one.
    val missing = remember(stamp) { FontChoice.entries.filterNot { it == FontChoice.CUSTOM || AppFonts.available(it) } }
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FontChoice.entries.forEach { f ->
            val on = f == current
            val usable = f !in missing
            Column(
                Modifier.width(96.dp).clip(ui.tile)
                    .background(if (on) scheme.primaryContainer else com.meow.music.ui.components.quietFill())
                    .border(if (on) 2.dp else 0.dp, if (on) scheme.primary else Color.Transparent, ui.tile)
                    .clickable(enabled = usable) { onPick(f) }
                    .then(if (usable) Modifier else Modifier.alpha(0.38f))
                    .padding(10.dp),
            ) {
                Text("Aa", style = TextStyle(fontFamily = AppFonts.family(f), fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.headlineSmall.fontSize), color = if (on) scheme.onPrimaryContainer else scheme.onSurface)
                Text(
                    if (f == FontChoice.CUSTOM) customName ?: "Your font…" else f.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppFonts.family(f)), color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (missing.isNotEmpty()) Note("${missing.joinToString(" and ") { it.label }} need${if (missing.size == 1) "s" else ""} Google Sans Flex, which this phone doesn't have.")
}

@Composable
private fun ShapeSettings() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val scheme = MaterialTheme.colorScheme
    Group("Shape") {
        Label("Corners")
        Chips(Corners.entries, s.corners, { it.label }) { prefs.setCorners(it) }
        // A peek at what the corners look like.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ui = LocalUi.current
            listOf(scheme.primary, scheme.tertiary, scheme.secondary).forEach { col ->
                Box(Modifier.weight(1f).aspectRatio(1.3f).clip(ui.tile).background(Brush.linearGradient(listOf(col, col.copy(alpha = 0.55f)))))
            }
        }
    }
}

@Composable
private fun ResetLook() {
    val prefs = LocalContainer.current.settings
    var ask by remember { mutableStateOf(false) }
    TextButton(onClick = { ask = true }, Modifier.fillMaxWidth()) {
        Icon(MeowIcons.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reset look & layout to defaults")
    }
    if (ask) androidx.compose.material3.AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Reset look & layout?") },
        text = { Text("Font, text size, corners, spacing, navigation bar, tabs, mini player and player style go back to how Meow ships. Your colours, backgrounds and everything else stay as they are.") },
        confirmButton = { TextButton(onClick = { prefs.resetLook(); ask = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------- Layout & navigation

private fun androidx.compose.foundation.lazy.LazyListScope.layoutAndNavigation() {
    item(key = "nav") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Navigation bar") {
            Label("Style")
            Chips(NavBarStyle.entries, s.navStyle, { it.label }) { prefs.setNavStyle(it) }
            Label("Labels")
            Chips(NavLabels.entries, s.navLabels, { it.label }) { prefs.setNavLabels(it) }
            Toggle("Shrink while scrolling", "The bar slims down as you scroll a page and comes back when you scroll up", s.navHideOnScroll) { prefs.setNavHideOnScroll(it) }
        }
    }
    item(key = "tabs") { TabsEditor() }
    item(key = "mini") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Mini player") {
            Chips(MiniPlayerStyle.entries, s.miniPlayerStyle, { it.label }) { prefs.setMiniPlayerStyle(it) }
            Toggle("Previous & next buttons", "Swiping the mini player sideways changes songs either way", s.miniSkipButtons) { prefs.setMiniSkipButtons(it) }
            Label("Progress")
            Chips(MiniProgress.entries, s.miniProgress, { it.label }) { prefs.setMiniProgress(it) }
        }
    }
    item(key = "density") {
        val s = LocalAppSettings.current
        val prefs = LocalContainer.current.settings
        Group("Pages") {
            Label("Spacing")
            Chips(UiDensity.entries, s.uiDensity, { it.label }) { prefs.setUiDensity(it) }
            Label("Page titles")
            Chips(TitleSize.entries, s.titleSize, { it.label }) { prefs.setTitleSize(it) }
            Label("Album & playlist cards")
            Chips(CardSize.entries, s.cardSize, { it.label }) { prefs.setCardSize(it) }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Which tabs the bar holds, in what order, and which one Meow opens on. */
@Composable
private fun TabsEditor() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    val tabs = s.navTabs
    Group("Tabs") {
        Note("Tick the tabs you want and use the arrows to order them. Keep Home, Library or Settings so Settings stays in reach.")
        val ordered = tabs + NavTab.entries.filter { it !in tabs }
        // Settings opens from Home and Library, or is a tab itself: one of them always stays.
        fun allowed(next: List<NavTab>) = next.size >= 2 && next.any { it == NavTab.HOME || it == NavTab.LIBRARY || it == NavTab.SETTINGS }
        ordered.forEach { t ->
            val on = t in tabs
            val i = tabs.indexOf(t)
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(on, { v ->
                    val next = if (v) tabs + t else tabs - t
                    if (allowed(next)) {
                        prefs.setNavTabs(next)
                        if (!v && s.startTab == t) prefs.setStartTab(next.first())
                    }
                }, enabled = !on || allowed(tabs - t))
                Text(t.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (on) {
                    IconButton(onClick = { prefs.setNavTabs(tabs.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) { Icon(MeowIcons.ChevronUp, "Move up") }
                    IconButton(onClick = { prefs.setNavTabs(tabs.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < tabs.lastIndex) { Icon(MeowIcons.ChevronDown, "Move down") }
                }
            }
        }
        Label("Open Meow on")
        Chips(tabs, if (s.startTab in tabs) s.startTab else tabs.first(), { it.label }) { prefs.setStartTab(it) }
        Note("Takes effect the next time Meow starts.")
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------------------- Player

@Composable
private fun PlayerSettings() {
    val s = LocalAppSettings.current
    val container = LocalContainer.current
    val prefs = container.settings
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group("Look") {
            Label("Background")
            Chips(PlayerBackground.entries, s.playerBackground, { it.label }) { prefs.setPlayerBackground(it) }
            if (s.playerBackground == PlayerBackground.APPEARANCE) Note(
                "The background picked for Home in Appearance → Backgrounds, in its dark version" +
                    if (s.fullBleedArtwork) ". Turn off Full-bleed artwork below to see more of it" else ""
            )
            if (s.playerBackground == PlayerBackground.BLUR || s.playerBackground == PlayerBackground.APPEARANCE) SliderSetting(
                "Show background", s.playerBackdropShow, 0f..1f, { if (it < 0.2f) "Subtle" else if (it < 0.7f) "Balanced" else "Vivid" },
            ) { prefs.setPlayerBackdropShow(it) }
            Label("Controls")
            Chips(TransportStyle.entries, s.transportStyle, { it.label }) { prefs.setTransportStyle(it) }
            Label("Progress bar")
            Chips(ScrubberStyle.entries, s.scrubberStyle, { it.label }) { prefs.setScrubberStyle(it) }
        }
        Group("Artwork") {
            Label("Cover size")
            Chips(CoverSize.entries, s.coverSize, { it.label }) { prefs.setCoverSize(it) }
            Note(
                if (s.fullBleedArtwork) "With Full-bleed artwork on, this sets how far down the cover reaches"
                else "Large narrows the side margins; Full width runs the square cover edge to edge. On shorter screens it grows " +
                    "as far as the controls allow: turning off the current lyric or output device makes more room"
            )
            SliderSetting("Corners", s.playerArtCorners.toFloat(), 0f..32f, { "${it.roundToInt()} dp" }, steps = 7) { prefs.setPlayerArtCorners(it.roundToInt()) }
            Toggle("Full-bleed artwork", "The cover runs edge to edge at the top of the player and fades into the background", s.fullBleedArtwork) { prefs.setFullBleedArtwork(it) }
            Toggle("Shrink when paused", "The cover eases back a little while the music is paused", s.artShrinkOnPause) { prefs.setArtShrinkOnPause(it) }
        }
        Group("Immersive mode") {
            Toggle("Immersive mode", "The cover fills the screen and the lyrics play over it, like a lyric video. Tap the screen for its settings; hold the settings button to leave. Also in the player's ⋯ menu", s.immersivePlayer) { prefs.setImmersivePlayer(it) }
            if (s.immersivePlayer) {
                Label("Artwork")
                Chips(com.meow.music.data.prefs.ImmersiveArt.entries, s.immersiveArt, { it.label }) { prefs.setImmersiveArt(it) }
                Label("Lyrics")
                Chips(com.meow.music.data.prefs.ImmersiveLyrics.entries, s.immersiveLyrics, { it.label }) { prefs.setImmersiveLyrics(it) }
                Spacer(Modifier.height(6.dp))
            }
        }
        Group("Animated covers") {
            Toggle("Animated covers", "Motion artwork and video covers, when a song has one. Turn them off for a single song from the player's ⋯ menu", s.canvasEnabled) { prefs.setCanvasEnabled(it) }
            if (s.canvasEnabled) Toggle("Play on mobile data", "Covers saved on the phone play either way", s.canvasOnCellular) { prefs.setCanvasOnCellular(it) }
            if (s.canvasEnabled) Toggle("Save with downloads", "Downloaded songs keep their animated cover on the phone too (an album's songs share one copy)", s.canvasWithDownloads) {
                prefs.setCanvasWithDownloads(it)
                if (it) container.downloads.saveCanvases()
            }
            if (s.canvasEnabled && s.canvasWithDownloads) Toggle("Save on mobile data too", "Off, they're saved once you're on Wi-Fi", s.canvasWithDownloadsCellular) {
                prefs.setCanvasWithDownloadsCellular(it)
                if (it) container.downloads.saveCanvases()
            }
            if (s.canvasEnabled) CanvasStorage()
        }
        // Top to bottom, as they sit on the player.
        Group("On the player") {
            Toggle("\"Playing from\"", "The song's album above the artwork; tap it to open the album", s.showPlayingFrom) { prefs.setShowPlayingFrom(it) }
            Toggle("Current lyric", "The line being sung, just above the progress bar; tap it to open the lyrics", s.lyricsOnPlayer) { prefs.setLyricsOnPlayer(it) }
            Toggle("Stream quality", "The codec and bitrate (or \"Downloaded\") under the progress bar; tap it for the full stats", s.showQuality) { prefs.setShowQuality(it) }
            Toggle("Shuffle and repeat buttons", "Either side of play, always (they're also above the queue). Repeat goes all, one, off", s.playerShuffleRepeat) { prefs.setPlayerShuffleRepeat(it) }
            Toggle("Volume bar", "The volume slider under the controls", !s.hideVolumeBar) { prefs.setHideVolumeBar(!it) }
            Toggle("Song / Video switch", "Off, the player stays audio only and never switches to the music video", s.showVideoSwitch) {
                prefs.setShowVideoSwitch(it)
                if (!it) container.player.setVideoMode(false)
            }
            Toggle("Output device", "Where the sound is going, at the bottom; tap it to switch", s.showOutputDevice) { prefs.setShowOutputDevice(it) }
        }
        Group("Hold to seek") {
            Toggle("Hold previous / next to seek", "Holding them jumps back or forward, again and again while you hold; a tap still changes songs", s.holdSeekSeconds > 0) {
                prefs.setHoldSeekSeconds(if (it) 10 else 0)
            }
            if (s.holdSeekSeconds > 0) SliderSetting("Each step", s.holdSeekSeconds.toFloat(), 1f..20f, { "${it.roundToInt()} s" }, steps = 18) {
                prefs.setHoldSeekSeconds(it.roundToInt())
            }
        }
        Group("Feel") {
            Toggle("Haptics", "A light tap when you press player buttons or swipe the mini player and artwork to change songs", s.haptics) { prefs.setHaptics(it) }
        }
    }
}

/** Animated covers kept on the phone: the ones saved from the player, and the recently played ones. */
@Composable
private fun CanvasStorage() {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val saved by c.canvas.saved.collectAsState()
    var version by remember { mutableStateOf(0) }
    val sizes by androidx.compose.runtime.produceState(0L to 0L, version, saved) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.canvasStore.savedBytes to c.canvasStore.recentBytes }
    }
    fun mb(bytes: Long) = "%.0f MB".format(bytes / 1_048_576f)
    Item(
        "Saved animated covers",
        if ((saved.count == 0)) "None yet · save one from the player's ⋯ menu so it never has to load again"
        else "${saved.count} saved · ${mb(sizes.first)} · tap to remove them all",
        onClick = {
            if ((saved.count > 0)) scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.canvasStore.clearSaved() }
                version++
            }
        },
    )
    Item(
        "Recently played covers",
        "${mb(sizes.second)} kept so they start instantly next time (up to 400 MB) · tap to clear",
        onClick = {
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.canvasStore.clearRecent() }
                version++
            }
        },
    )
}

// ---------------------------------------------------------------------------- Playback & sound

@Composable
private fun SoundPage() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val found by c.localLossless.found.collectAsState()
    val synced by c.losslessSync.songs.collectAsState()
    val syncing by c.losslessSync.working.collectAsState()
    val askFiles = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Lossless still works without file access; it just can't use files on the phone.
        prefs.setLossless(true)
        if (granted) prefs.setLosslessLocal(true) // lossless sync scans when this turns on
    }
    LaunchedEffect(s.losslessLocal) { if (s.losslessLocal && found < 0) c.losslessSync.sync() }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group("Playback") {
            Toggle("Autoplay", "When a song, album or playlist ends, keep going with similar music", s.autoplay) { prefs.setAutoplay(it) }
            Toggle(
                "Songs, not videos",
                "Autoplay and radios play songs: a music video plays as its song, and videos with no song version are left out",
                s.radioSongsOnly,
            ) { prefs.setRadioSongsOnly(it) }
            Toggle("Keep playing videos", "While a music video is showing, the next song plays as its video too, until you switch back to Song", s.videoContinues) { prefs.setVideoContinues(it) }
            Toggle("Skip silence", "Trims silent gaps at the start and end of songs", s.skipSilence) { prefs.setSkipSilence(it) }
            Toggle(
                "Stop when swiped away",
                "Swiping Meow away from your recent apps stops the music. Off, it keeps playing in the background",
                s.stopOnSwipeAway,
            ) { prefs.setStopOnSwipeAway(it) }
            Label("Load ahead")
            Chips(com.meow.music.data.prefs.Preload.entries, s.preload, { it.label }) { prefs.setPreload(it) }
            Note(
                when (s.preload) {
                    com.meow.music.data.prefs.Preload.START -> "While a song plays, the next one's first seconds are loaded, so it starts at once."
                    else -> "While a song plays, what's next in the queue is loaded whole, so it plays without buffering even if the signal drops. It counts toward Smart Downloads' space."
                }
            )
            Toggle(
                "Load ahead on mobile data",
                if (s.preload == com.meow.music.data.prefs.Preload.START) "Off, only on Wi-Fi. The start of a song is about 0.3 MB"
                else "Off, only on Wi-Fi. A whole song is about 3–8 MB",
                s.preloadCellular,
            ) { prefs.setPreloadCellular(it) }
        }
        Group("Crossfade") {
            Toggle(
                "Crossfade songs",
                "As a song ends, it fades out while the next one fades in, as on Spotify. Not between an album's own tracks",
                s.mix,
            ) { prefs.setMix(it) }
            if (s.mix) {
                Toggle(
                    "Automatic length",
                    "Meow listens to how each song ends and the next begins: it crossfades over a fade-out or a quiet intro, and only briefly between two hard edges",
                    s.mixAuto,
                ) { prefs.setMixAuto(it) }
                if (!s.mixAuto) SliderSetting("Length", s.mixSeconds.toFloat(), 1f..12f, { "${it.roundToInt()} s" }, steps = 10) { prefs.setMixSeconds(it.roundToInt()) }
                Toggle(
                    "Match tempo and beats (DJ-style)",
                    "Lines up the songs' beats and plays the next song at the ending one's tempo while they overlap, then eases it back. Off, they crossfade at their own speed",
                    s.mixBeatMatch,
                ) { prefs.setMixBeatMatch(it) }
            }
        }
        Group("Quality") {
            Toggle(
                "Lossless playback",
                "Always plays the best audio there is, keeps hi-res files at full resolution, and plays matching FLAC, WAV and AIFF files on your phone instead of the stream" + when {
                    !s.lossless -> ""
                    !s.losslessLocal -> " · allow file access to use lossless files"
                    found < 0 -> " · looking for lossless files…"
                    found == 0 -> " · no lossless files found yet (add them to your Music folder)"
                    syncing -> " · $found lossless files found, adding them to Downloads…"
                    else -> " · $found lossless files found, ${synced.size} in Downloads"
                },
                s.lossless,
            ) { on ->
                if (!on) { prefs.setLossless(false); prefs.setLosslessLocal(false) }
                else if (c.localLossless.hasPermission()) {
                    prefs.setLossless(true); prefs.setLosslessLocal(true)
                } else askFiles.launch(LocalLossless.permission)
            }
            if (s.lossless) {
                if (s.losslessLocal) Item(
                    "Scan for lossless files again",
                    "New files are added to Downloads as soon as they're on your phone, and Meow checks again every few hours",
                    onClick = { scope.launch { c.losslessSync.sync() } },
                )
                else Item("Allow access to lossless files", null, onClick = { askFiles.launch(LocalLossless.permission) })
                // Data saver plays songs at this quality on mobile data, lossless or not (#40).
                if (s.dataSaver) {
                    Label("Streaming quality on mobile data (data saver)")
                    Chips(AudioQuality.entries.filter { it != AudioQuality.HIGH }, s.cellularQuality.takeIf { it != AudioQuality.HIGH } ?: AudioQuality.NORMAL, { it.label }) { prefs.setCellularQuality(it) }
                }
            } else {
                Label("Streaming quality on Wi-Fi")
                Chips(AudioQuality.entries, s.audioQuality, { it.label }) { prefs.setAudioQuality(it) }
                Label("Streaming quality on mobile data")
                Chips(AudioQuality.entries, s.cellularQuality, { it.label }) { prefs.setCellularQuality(it) }
            }
            Label("Music video quality")
            Chips(listOf(480, 720, 1080, 1440), s.videoMaxHeight, { "${it}p" }) { prefs.setVideoMaxHeight(it) }
            Spacer(Modifier.height(6.dp))
        }
        Group("Mobile data") {
            Toggle(
                "Data saver",
                "On mobile data: covers and pictures at the size they're shown (much smaller downloads), no artist photos " +
                    "fetched for lists, no animated covers (saved ones still play), songs at your mobile data quality and at most " +
                    "Normal (even with lossless on), and music videos at 480p at most. Wi-Fi is unaffected",
                s.dataSaver,
            ) { prefs.setDataSaver(it) }
        }
        Group("Sound") {
            Toggle(
                "Normalize volume",
                "Evens out loudness between songs: each song is measured on your phone and brought to the same level (-14 LUFS, " +
                    "like Spotify's), loud ones down, quiet ones up, with a limiter so nothing clips. Works offline too",
                s.normalize,
            ) { prefs.setNormalize(it) }
            if (s.normalize || s.mix) Toggle(
                "Measure songs on mobile data",
                "Normalize volume and Crossfade measure each song once (its loudness, how its music starts and ends, its beat). Songs " +
                    "on your phone are measured from there; others need a small copy of the song (1–4 MB). Off: that waits for Wi-Fi",
                s.mixTempoOnCellular,
            ) { prefs.setMixTempoOnCellular(it) }
            SoundSettings(onOpenEq = { nav.go(Routes.EQ) })
        }
    }
}

// ---------------------------------------------------------------------------- Lyrics

/** Languages lyrics can be translated into (Google Translate codes); "" is the phone's own. */
private val TRANSLATE_LANGUAGES = listOf(
    "" to "Phone's language", "en" to "English", "es" to "Spanish", "pt" to "Portuguese", "fr" to "French",
    "de" to "German", "it" to "Italian", "nl" to "Dutch", "pl" to "Polish", "tr" to "Turkish", "ru" to "Russian",
    "uk" to "Ukrainian", "ar" to "Arabic", "hi" to "Hindi", "id" to "Indonesian","ne" to "Nepali", "vi" to "Vietnamese",
    "tl" to "Filipino", "ja" to "Japanese", "ko" to "Korean", "zh-CN" to "Chinese (Simplified)", "zh-TW" to "Chinese (Traditional)",
)

@Composable
private fun LyricsSettings() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group("Display") {
            Toggle("Focus blur", "Lines further from the one being sung fall out of focus", s.lyricsBlur) { prefs.setLyricsBlur(it) }
            Toggle(
                "Translate lyrics",
                "Each line in your language under it, when a song is in another one. Machine translation: the lyrics' text is sent to Google Translate",
                s.lyricsTranslate,
            ) { prefs.setLyricsTranslate(it) }
            if (s.lyricsTranslate) {
                Label("Translate into")
                Chips(TRANSLATE_LANGUAGES.map { it.first }, s.lyricsTranslateTo.takeIf { code -> TRANSLATE_LANGUAGES.any { it.first == code } } ?: "",
                    { code -> TRANSLATE_LANGUAGES.first { it.first == code }.second }) { prefs.setLyricsTranslateTo(it) }
            }
            Label("Alignment")
            Chips(LyricsAlign.entries, s.lyricsAlign, { it.label }) { prefs.setLyricsAlign(it) }
            SliderSetting("Text size", s.lyricsScale, 0.75f..1.4f, { "${(it * 100).roundToInt()}%" }) { prefs.setLyricsScale(it) }
        }
        Group("Sources") {
            Toggle("Prefer karaoke & synced lyrics", "Karaoke lyrics (lit up word by word) first, from any source that has them, then time-synced lines, then plain text", s.preferSynced) { prefs.setPreferSynced(it) }
            Toggle("Uncensor lyrics", "Fill starred-out swear words (f**k) back in from Genius, keeping word timings, and pass over a source that still stars them out", s.skipCensored) { prefs.setSkipCensored(it) }
            Label("Order")
            Note("Meow tries these in order, and only the ones switched on. You can also switch source on any song from the player's ⋯ menu.")
            s.lyricsOrder.forEachIndexed { i, src ->
                val on = src !in s.lyricsOff
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (i == 0 && on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", color = if (i == 0 && on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).alpha(if (on) 1f else 0.45f)) {
                        Text(src.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(src.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Turned off, a source is never asked (#51); at least one stays on.
                    androidx.compose.material3.Switch(
                        on, { v -> prefs.setLyricsOff(if (v) s.lyricsOff - src else s.lyricsOff + src) },
                        enabled = !on || s.lyricsOrder.count { it !in s.lyricsOff } > 1,
                        modifier = Modifier.scale(0.8f),
                    )
                    IconButton(onClick = { prefs.setLyricsOrder(s.lyricsOrder.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                        Icon(MeowIcons.ChevronUp, "Move ${src.label} up")
                    }
                    IconButton(onClick = { prefs.setLyricsOrder(s.lyricsOrder.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < s.lyricsOrder.lastIndex) {
                        Icon(MeowIcons.ChevronDown, "Move ${src.label} down")
                    }
                }
            }
        }
        Group("Your music files") {
            val c = LocalContainer.current
            Item(
                "Save lyrics into your music files",
                "Synced lyrics go into your FLAC and MP3 files' tags (as LRC), so other players show them too: Local files, and " +
                    "the lossless files played for songs. Lyrics already saved are used, others are looked up. Android asks once " +
                    "before the files are changed. You can also do it for one song from its ⋯ menu",
                onClick = { c.lyricsToFiles.request(null) },
            )
        }
        Group("Android Auto") {
            Toggle("Lyrics in the car", "On the car's player, in place of the song title and cover. The Lyrics button there turns them on and off too", s.carLyrics) { prefs.setCarLyrics(it) }
            if (s.carLyrics) {
                Label("Style")
                Chips(CarLyricsStyle.entries, s.carLyricsStyle, { it.label }) { prefs.setCarLyricsStyle(it) }
                Note(s.carLyricsStyle.detail)
                CarLyricsPreview(s.carLyricsStyle)
                if (s.carLyricsStyle != CarLyricsStyle.TEXT) Toggle(
                    "Keep the song title",
                    "Lyrics on the cover only. The car's small media card then reads as usual (its cover is tiny), and the full player shows the lyric card big. Android Auto doesn't tell apps which of the two is showing",
                    s.carLyricsKeepTitle,
                ) { prefs.setCarLyricsKeepTitle(it) }
                Toggle("On this phone too", "Also show them on the lock screen and in the media notification when you're not driving", s.carLyricsOnPhone) { prefs.setCarLyricsOnPhone(it) }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Downloads & storage

@Composable
private fun DownloadSettings() {
    val c = LocalContainer.current
    val s = LocalAppSettings.current
    val prefs = c.settings
    val nav = LocalNavigator.current
    val context = LocalContext.current
    var cacheBytes by remember { mutableLongStateOf(c.songCacheBytes()) }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group("Downloads") {
            Item("Manage downloads", "See, sort and delete what's on your phone", onClick = { nav.go(Routes.DOWNLOADS) }) { Chevron() }
            Toggle("Download on Wi-Fi only", null, s.downloadWifiOnly) { prefs.setDownloadWifiOnly(it) }
            Toggle("Download liked songs automatically", "Every song you like is saved for offline, including likes synced from other devices", s.downloadLikes) {
                prefs.setDownloadLikes(it)
                if (it) c.downloads.downloadAll(c.library.liked.value)
            }
        }
        Group("Smart Downloads") {
            Note(
                "One space for music on your phone: songs you stream stay here, so replays start at once and use no data, " +
                    "and the songs you're most likely to play next are downloaded ahead. When it's full, the songs you're least " +
                    "likely to come back to make room. Learned on your phone from what you play, finish, skip and like."
            )
            val sizes = SMART_DOWNLOAD_SIZES
            var i by remember { mutableFloatStateOf(sizes.indexOfFirst { it >= s.offlineGb }.let { if (it < 0) sizes.lastIndex else it }.toFloat()) }
            val gb = sizes[i.roundToInt().coerceIn(0, sizes.lastIndex)]
            val freeGb = remember { context.filesDir.usableSpace / 1_073_741_824f }
            val picks by c.downloads.smartIds.collectAsState()
            val downloads by c.downloads.downloads.collectAsState()
            val pickBytes = picks.sumOf { downloads[it]?.bytes ?: 0L }
            Label("Space to use: ${formatGb(gb)}")
            Note(
                "Room for about ${smartSongEstimate(gb, s.audioQuality)} songs at ${s.audioQuality.label.lowercase()} quality · %.1f GB free on this phone".format(freeGb) +
                    (if (gb > freeGb) " — more than is free, so it'll stop when storage runs low" else "") +
                    "\nUsing %.1f GB: %.1f GB downloaded ahead, %.1f GB from songs you've played".format(
                        (pickBytes + cacheBytes) / 1_073_741_824.0, pickBytes / 1_073_741_824.0, cacheBytes / 1_073_741_824.0,
                    )
            )
            Slider(
                i, { i = it }, Modifier.padding(horizontal = 16.dp), valueRange = 0f..sizes.lastIndex.toFloat(), steps = sizes.size - 2,
                onValueChangeFinished = {
                    prefs.setOfflineGb(gb)
                    c.trimSongCache()
                    if (s.smartDownloads) SmartDownloadWorker.runNow(context)
                },
            )
            Toggle("Smart Downloads", "Downloads the songs you're likely to play next into this space, a little at a time, and swaps them out as your taste changes. Off, it only keeps songs you've played", s.smartDownloads) {
                prefs.setSmartDownloads(it)
                SmartDownloadWorker.schedule(context, it)
                if (it) SmartDownloadWorker.runNow(context) else c.trimSongCache()
            }
            if (s.smartDownloads) Toggle("Smart Downloads on mobile data","Off, it waits for Wi-Fi. A song is about 3–8 MB", s.smartDownloadsCellular) {
                prefs.setSmartDownloadsCellular(it)
                // The worker's network rule is fixed when it's scheduled, so it's scheduled again with the new one.
                SmartDownloadWorker.schedule(context, true, cellular = it)
                SmartDownloadWorker.runNow(context, force = false, cellular = it)
                c.downloads.refreshSmartIds()
            }
            Item("Clear Smart Downloads", "Removes what it downloaded ahead and the songs kept from streaming. Songs you downloaded yourself stay", onClick = {
                c.downloads.clearSmartDownloads()
                cacheBytes = c.songCacheBytes()
            })
        }
    }
}

// ---------------------------------------------------------------------------- Library & Replay

private val libraryTabs = listOf("PLAYLISTS" to "Playlists", "SONGS" to "Songs", "ALBUMS" to "Albums", "ARTISTS" to "Artists", "DOWNLOADS" to "Downloads")

@Composable
private fun LibraryReplaySettings() {
    val s = LocalAppSettings.current
    val prefs = LocalContainer.current.settings
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group("Library") {
            Label("Albums, artists & playlists as")
            Chips(LibraryView.entries, s.libraryView, { it.label }) { prefs.setLibraryView(it) }
            Label("Open Library on")
            // "LOCAL" (On this phone) was a tab before Local files got its own page: Playlists stands in for it.
            val startTab = s.libraryStartTab.takeIf { k -> libraryTabs.any { it.first == k } } ?: "PLAYLISTS"
            Chips(libraryTabs.map { it.first }, startTab, { k -> libraryTabs.firstOrNull { it.first == k }?.second ?: k }) { prefs.setLibraryStartTab(it) }
            Spacer(Modifier.height(6.dp))
            Toggle("Full-width playlist pictures", "Playlists open with their picture edge to edge, like an artist page", s.playlistHeroCover) { prefs.setPlaylistHeroCover(it) }
        }
        Group("Replay") {
            Toggle("Show time in hours", if (s.replayHours) "Listening time reads like \"20h 4m\"" else "Listening time reads like \"1,204 min\"", s.replayHours) { prefs.setReplayHours(it) }
            Toggle("Use theme colours", "Replay's cards follow your accent instead of Meow's own gradient", s.replayThemeColors) { prefs.setReplayThemeColors(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(current: Int, onPick: (Int) -> Unit) {
    var hue by remember {
        val hsl = FloatArray(3); ColorUtils.colorToHSL(current, hsl); mutableFloatStateOf(hsl[0])
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentSwatches.forEach { col ->
                val neutral = isNeutralSeed(col.toArgb())
                // The old white (light grey) counts as the white swatch.
                val sel = col.toArgb() == current || (neutral && isNeutralSeed(current))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(col)
                        .border(
                            if (sel) 3.dp else if (neutral) 1.dp else 0.dp,
                            if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape,
                        )
                        .clickable { onPick(col.toArgb()) },
                    contentAlignment = Alignment.Center,
                ) { if (sel) Icon(MeowIcons.Check, null, tint = if (neutral) Color.Black else Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(
                Brush.horizontalGradient((0..6).map { Color(ColorUtils.HSLToColor(floatArrayOf(it * 60f, 0.75f, 0.6f))) })
            )
        )
        Slider(hue, { hue = it }, valueRange = 0f..359f, onValueChangeFinished = {
            onPick(ColorUtils.HSLToColor(floatArrayOf(hue, 0.72f, 0.6f)))
        })
    }
}

/** What the car shows right now (or would), drawn by the same code that draws it for the car. */
@Composable
private fun CarLyricsPreview(style: CarLyricsStyle) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val song by c.player.currentSong.collectAsState()
    var lyrics by remember { mutableStateOf<com.meow.music.data.lyrics.Lyrics?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var bg by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var card by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(song?.id) {
        lyrics = null; card = null; line = null; loaded = false
        val s = song ?: return@LaunchedEffect
        lyrics = runCatching { c.lyrics.auto(s) }.getOrNull()?.takeIf { it.synced }
        loaded = true
        bg = runCatching { com.meow.music.playback.CarLyricsCard.background(context, s.thumbnail) }.getOrNull()
    }
    LaunchedEffect(lyrics, style, bg) {
        val l = lyrics ?: return@LaunchedEffect
        while (true) {
            val pos = c.player.lyricPosition
            var idx = com.meow.music.playback.CarLyricsCard.lineAt(l.lines, pos)
            if (idx < 0 || l.lines[idx].isGap) idx = l.lines.indexOfFirst { !it.isGap && it.text.isNotBlank() && it.timeMs >= pos }.takeIf { it >= 0 } ?: idx
            val ln = l.lines.getOrNull(idx)
            if (ln != null && !ln.isGap) {
                val sung = if (style == CarLyricsStyle.KARAOKE && ln.isWordSynced) ln.words.count { it.startMs <= pos } else -1
                line = ln.text
                card = if (style == CarLyricsStyle.TEXT) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.meow.music.playback.CarLyricsCard.render(bg, l.lines, idx, sung).asImageBitmap()
                }
            }
            kotlinx.coroutines.delay(300)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = LocalUi.current.shape(14.dp)
        val img = card
        if (img != null) androidx.compose.foundation.Image(img, "Lyric card", Modifier.size(132.dp).clip(shape))
        else com.meow.music.ui.components.Artwork(song?.thumbnail, Modifier.size(132.dp), shape, size = 300)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            val s = song
            when {
                s == null -> Text("Play a song to see how its lyrics look in the car.", style = MaterialTheme.typography.bodyMedium)
                loaded && lyrics == null -> {
                    Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text("No synced lyrics for this song, so the car shows it as usual.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(line ?: s.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                    Text("${s.title} · ${s.artistText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
